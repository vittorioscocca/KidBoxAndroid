package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.repository.NoteRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class NoteDetailUiState(
    val familyId: String = "",
    val noteId: String = "",
    val title: String = "",
    val body: String = "",
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    val visibilityMemberIds: List<String> = emptyList(),
    val pickerMembersExcludingSelf: List<VisibilityPickerMember> = emptyList(),
    /** Creatore: solo lui può cambiare visibilità (anche dopo il primo salvataggio). */
    val noteCreatedBy: String = "",
    val noteCreatedAtEpochMillis: Long? = null,
    val currentUid: String = "",
    val isNewRoute: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
) {
    val canEditVisibility: Boolean
        get() =
            currentUid.isNotBlank() &&
                (noteCreatedBy.isBlank() || noteCreatedBy == currentUid)
}

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val badgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
    private val familyMemberDao: KBFamilyMemberDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundKey: Triple<String, String, Boolean>? = null

    /**
     * Scaduta l'attesa della sincronizzazione, si smette di mostrare il
     * caricamento anche se la nota non è mai arrivata.
     */
    private var syncTimeoutJob: Job? = null
    private var syncWaitExpired = false

    fun bind(
        familyId: String,
        noteId: String,
        isNewRoute: Boolean,
    ) {
        if (familyId.isBlank() || noteId.isBlank()) {
            _uiState.value = NoteDetailUiState(isLoading = false, errorMessage = "Nota non disponibile")
            return
        }
        val key = Triple(familyId, noteId, isNewRoute)
        if (boundKey == key && observeJob != null) return
        boundKey = key
        badgeManager.clearLocal(CounterField.NOTES)
        viewModelScope.launch { badgeManager.resetRemote(familyId, CounterField.NOTES) }
        _uiState.value = _uiState.value.copy(
            familyId = familyId,
            noteId = noteId,
            isNewRoute = isNewRoute,
            isLoading = true,
            currentUid = auth.currentUser?.uid.orEmpty(),
        )
        // Il listener delle note lo avviava SOLO la schermata elenco: arrivando
        // qui da una notifica quella schermata non viene mai aperta, quindi
        // nessuno scaricava la nota e l'attesa non sarebbe mai finita. Avviarlo
        // anche qui è ciò che rende la nota effettivamente recuperabile.
        // È idempotente: se l'elenco l'ha già agganciato per questa famiglia,
        // `startRealtime` esce subito.
        noteRepository.startRealtime(
            familyId = familyId,
            onPermissionDenied = {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Accesso Note negato per questa famiglia",
                )
            },
        )
        // Aprendo una nota da notifica si arriva PRIMA che la sincronizzazione
        // l'abbia portata in locale: senza attendere si mostrerebbe un editor
        // vuoto al posto della nota. Si resta in caricamento finché non arriva,
        // e l'observer qui sotto la applica appena compare.
        syncWaitExpired = false
        syncTimeoutJob?.cancel()
        syncTimeoutJob = if (isNewRoute) {
            null
        } else {
            viewModelScope.launch {
                delay(SYNC_WAIT_TIMEOUT_MS)
                syncWaitExpired = true
                val cur = _uiState.value
                if (cur.isLoading) {
                    _uiState.value = cur.copy(
                        isLoading = false,
                        errorMessage = "Nota non disponibile",
                    )
                }
            }
        }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                noteRepository.observeById(noteId),
                familyMemberDao.observeActiveByFamilyId(familyId),
            ) { note, members ->
                val me = auth.currentUser?.uid.orEmpty()
                val pickerMembers = members
                    .filter { it.userId != me }
                    .map { row ->
                        val label = row.displayName?.trim()?.takeIf { it.isNotEmpty() }
                            ?: row.email?.trim()?.takeIf { it.isNotEmpty() }
                            ?: "Membro"
                        VisibilityPickerMember(uid = row.userId, displayName = label)
                    }
                    .sortedBy { it.displayName.lowercase() }
                note to pickerMembers
            }.collect { (note, pickerMembers) ->
                val cur = _uiState.value
                if (note == null) {
                    // Non ancora sincronizzata: si resta in caricamento invece di
                    // mostrare un editor vuoto. Per una nota NUOVA l'editor vuoto
                    // è invece la cosa giusta, e non c'è nulla da attendere.
                    val stillWaiting = !cur.isNewRoute && !syncWaitExpired
                    _uiState.value = cur.copy(
                        isLoading = stillWaiting,
                        pickerMembersExcludingSelf = pickerMembers,
                    )
                } else {
                    syncTimeoutJob?.cancel()
                    if (cur.isDirty) {
                        _uiState.value = cur.copy(
                            isLoading = false,
                            pickerMembersExcludingSelf = pickerMembers,
                            noteCreatedBy = note.createdBy,
                            noteCreatedAtEpochMillis = note.createdAtEpochMillis,
                        )
                        return@collect
                    }
                    val titlePlain = note.title.htmlToPlainText()
                    _uiState.value = cur.copy(
                        title = titlePlain,
                        body = note.body,
                        visibilityScope = KBVisibilityScope.normalized(note.visibilityScope),
                        visibilityMemberIds = note.visibilityMemberIds,
                        pickerMembersExcludingSelf = pickerMembers,
                        noteCreatedBy = note.createdBy,
                            noteCreatedAtEpochMillis = note.createdAtEpochMillis,
                        isLoading = false,
                        isDirty = false,
                    )
                }
            }
        }
    }

    fun updateTitle(value: String) {
        val current = _uiState.value
        // No-op se il valore non è cambiato: evita che echi programmatici
        // del TextField (es. recompose dopo save con titolo normalizzato)
        // rialzino isDirty e facciano ricomparire il bottone "Salva".
        if (current.title == value) return
        _uiState.value = current.copy(title = value, isDirty = true)
    }

    fun updateBody(value: String) {
        val current = _uiState.value
        // Vedi updateTitle: lo stesso vale per il corpo, dove il setText
        // programmatico nell'AndroidView del RichNoteEditor farebbe scattare
        // il TextWatcher con il body trimEnd() arrivato dall'observer.
        if (current.body == value) return
        _uiState.value = current.copy(body = value, isDirty = true)
    }

    fun setVisibilityConfirmed(
        scope: String,
        memberIds: List<String>,
    ) {
        val norm = KBVisibilityScope.normalized(scope)
        val ids = if (norm == KBVisibilityScope.MEMBERS) memberIds else emptyList()
        _uiState.value = _uiState.value.copy(
            visibilityScope = norm,
            visibilityMemberIds = ids,
            isDirty = true,
        )
    }

    fun save(
        onDone: () -> Unit,
    ) {
        val current = _uiState.value
        if (current.familyId.isBlank() || current.noteId.isBlank()) return
        val normalizedTitle = current.title.htmlToPlainText().trim()
        val normalizedBody = current.body.trimEnd()
        // Aggiornamento ottimistico: nascondi subito il bottone "Salva"
        // mettendo isDirty=false PRIMA che la rete risponda. Allineiamo
        // anche title/body alla forma normalizzata così l'eco dell'observer
        // non innesca un setText programmatico che rialzerebbe isDirty.
        _uiState.value = current.copy(
            title = normalizedTitle,
            body = normalizedBody,
            isSaving = true,
            isDirty = false,
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                noteRepository.upsertNote(
                    familyId = current.familyId,
                    noteId = current.noteId,
                    title = normalizedTitle,
                    body = normalizedBody,
                    visibilityScope = current.visibilityScope,
                    visibilityMemberIds = current.visibilityMemberIds,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                onDone()
            }.onFailure { err ->
                // Se il salvataggio fallisce, ripristina lo stato "dirty"
                // così il bottone ricompare e l'utente può riprovare.
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isDirty = true,
                    errorMessage = err.message ?: "Errore salvataggio",
                )
            }
        }
    }

    fun saveSilently() {
        val current = _uiState.value
        if (current.familyId.isBlank() || current.noteId.isBlank() || !current.isDirty) return
        val normalizedTitle = current.title.htmlToPlainText().trim()
        val normalizedBody = current.body.trimEnd()
        viewModelScope.launch {
            runCatching {
                noteRepository.upsertNote(
                    familyId = current.familyId,
                    noteId = current.noteId,
                    title = normalizedTitle,
                    body = normalizedBody,
                    visibilityScope = current.visibilityScope,
                    visibilityMemberIds = current.visibilityMemberIds,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    title = normalizedTitle,
                    body = normalizedBody,
                    isDirty = false,
                )
            }
        }
    }
}

/**
 * Quanto si attende che la sincronizzazione porti una nota aperta da
 * notifica. Stesso ordine di grandezza dell'attesa dei to-do.
 */
private const val SYNC_WAIT_TIMEOUT_MS = 25_000L
