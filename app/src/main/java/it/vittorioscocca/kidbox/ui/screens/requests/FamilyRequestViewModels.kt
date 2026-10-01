package it.vittorioscocca.kidbox.ui.screens.requests

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequest
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequestRemoteStore
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequestRemoteStore.Outcome
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Nomi dei membri per uid, come li mostra il resto dell'app. */
data class FamilyRequestNames(
    val byUid: Map<String, String> = emptyMap(),
    val me: String = "",
) {
    fun name(context: Context, uid: String): String {
        if (uid == me) return context.getString(R.string.requests_you)
        return byUid[uid]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.requests_family_member)
    }

    fun firstName(context: Context, uid: String): String =
        name(context, uid).split(" ").firstOrNull().orEmpty()

    companion object {
        fun from(members: List<KBFamilyMemberEntity>, me: String) = FamilyRequestNames(
            byUid = members.associate { m ->
                m.userId to (m.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: m.email?.trim().orEmpty())
            },
            me = me,
        )
    }
}

/** Testo dell'esito di una risposta, condiviso da card e schermata. */
fun Outcome.message(context: Context): String? = when (this) {
    Outcome.ClaimedByMe -> context.getString(R.string.requests_outcome_mine)
    is Outcome.ClaimedBy ->
        if (name.isBlank()) context.getString(R.string.requests_outcome_someone_first)
        else context.getString(R.string.requests_outcome_taken_by, name)
    Outcome.Declined -> null
    Outcome.Closed -> context.getString(R.string.requests_outcome_closed)
}

data class FamilyRequestsHomeState(
    val requests: List<FamilyRequest> = emptyList(),
    val names: FamilyRequestNames = FamilyRequestNames(),
)

/**
 * Le richieste aperte della famiglia attiva, per la sezione in Home. Il
 * listener vive finché la Home raccoglie lo stato.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilyRequestsHomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val memberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val familyId = MutableStateFlow("")
    private val busyIds = MutableStateFlow<Set<String>>(emptySet())
    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Esiti da mostrare nello snackbar della Home: dopo un «Io» la card sparisce. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<FamilyRequestsHomeState> = familyId
        .flatMapLatest { fid ->
            if (fid.isBlank()) {
                flowOf(FamilyRequestsHomeState())
            } else {
                combine(
                    FamilyRequestRemoteStore.observeOpen(fid),
                    memberDao.observeActiveByFamilyId(fid),
                ) { list, members ->
                    FamilyRequestsHomeState(
                        requests = list,
                        names = FamilyRequestNames.from(members, auth.currentUser?.uid.orEmpty()),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyRequestsHomeState())

    val busy: StateFlow<Set<String>> = busyIds

    fun setFamily(id: String) {
        familyId.value = id
    }

    fun respond(request: FamilyRequest, yes: Boolean) {
        if (request.id in busyIds.value) return
        busyIds.value = busyIds.value + request.id
        viewModelScope.launch {
            try {
                val outcome = FamilyRequestRemoteStore.respond(request.familyId, request.id, yes)
                outcome.message(appContext)?.let { _messages.send(it) }
            } catch (e: Exception) {
                _messages.send(appContext.getString(R.string.requests_respond_failed))
            } finally {
                busyIds.value = busyIds.value - request.id
            }
        }
    }
}

data class FamilyRequestDetailState(
    val request: FamilyRequest? = null,
    val loaded: Boolean = false,
    val names: FamilyRequestNames = FamilyRequestNames(),
)

/**
 * Una richiesta, aperta da una card o da una notifica. Con `answer=yes` (azione
 * «Ci penso io» della notifica) risponde da sola, una volta sola.
 */
@HiltViewModel
class FamilyRequestDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    memberDao: KBFamilyMemberDao,
    auth: FirebaseAuth,
) : ViewModel() {

    val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val requestId: String = savedStateHandle.get<String>("requestId").orEmpty()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val state: StateFlow<FamilyRequestDetailState> = combine(
        FamilyRequestRemoteStore.observe(familyId, requestId),
        memberDao.observeActiveByFamilyId(familyId),
    ) { request, members ->
        FamilyRequestDetailState(
            request = request,
            loaded = true,
            names = FamilyRequestNames.from(members, auth.currentUser?.uid.orEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyRequestDetailState())

    init {
        // La rotta resta nel back stack con lo stesso argomento: senza il flag
        // ogni ricreazione rimanderebbe la risposta.
        if (savedStateHandle.get<String>("answer") == "yes" && savedStateHandle.get<Boolean>(KEY_ANSWERED) != true) {
            savedStateHandle[KEY_ANSWERED] = true
            respond(yes = true)
        }
    }

    fun respond(yes: Boolean) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val outcome = FamilyRequestRemoteStore.respond(familyId, requestId, yes)
                _message.value = outcome.message(appContext)
            } catch (e: Exception) {
                _message.value = appContext.getString(R.string.requests_respond_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancel() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                FamilyRequestRemoteStore.cancel(appContext, familyId, requestId)
            } catch (e: Exception) {
                _message.value = appContext.getString(R.string.requests_cancel_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val KEY_ANSWERED = "answerConsumed"
    }
}
