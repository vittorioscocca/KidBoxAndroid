package it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.repository.LoyaltyCardPhotoSide
import it.vittorioscocca.kidbox.data.repository.PaymentCardItem
import it.vittorioscocca.kidbox.data.repository.PaymentCardPlain
import it.vittorioscocca.kidbox.data.repository.PaymentCardRepository
import it.vittorioscocca.kidbox.data.repository.normalizedForSave
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerMember
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import java.util.Locale
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PaymentCardsUiState(
    val familyId: String = "",
    val items: List<PaymentCardItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    /** Foto già scaricate e decifrate, per path Storage. Solo in memoria. */
    val photos: Map<String, Bitmap> = emptyMap(),
    val busyPhotoSide: LoyaltyCardPhotoSide? = null,
    /** Membri per il selettore di visibilità, senza chi sta usando l'app. */
    val visibilityMembers: List<VisibilityPickerMember> = emptyList(),
)

/**
 * Sezione «Pagamento» del Wallet. I campi si decifrano qui, fuori dal thread
 * principale, a ogni emissione di Room: in memoria, mai su disco.
 */
@HiltViewModel
class PaymentCardsViewModel @Inject constructor(
    private val repository: PaymentCardRepository,
    private val familyMemberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentCardsUiState())
    val uiState: StateFlow<PaymentCardsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundFamilyId: String? = null
    private val loadingPhotoPaths = mutableSetOf<String>()

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    fun forceRefresh() = pullToRefresh.refresh {
        val familyId = boundFamilyId ?: return@refresh
        repository.awaitForceRestartRealtime(familyId)
    }

    fun bind(familyId: String) {
        if (familyId.isBlank()) {
            _uiState.value = PaymentCardsUiState(isLoading = false)
            return
        }
        if (boundFamilyId == familyId && observeJob != null) return
        boundFamilyId = familyId
        repository.startRealtime(familyId)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                repository.observeActiveByFamilyId(familyId)
                    .map { cards -> cards.map { PaymentCardItem(it, repository.decrypt(it)) } }
                    .flowOn(Dispatchers.Default),
                familyMemberDao.observeActiveByFamilyId(familyId),
            ) { items, members ->
                val uid = auth.currentUser?.uid
                val picker = members
                    .filter { it.userId != uid }
                    .map { m ->
                        VisibilityPickerMember(
                            uid = m.userId,
                            displayName = m.displayName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wallet_payment_member_fallback),
                        )
                    }
                    .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
                items to picker
            }.collect { (items, picker) ->
                _uiState.value = _uiState.value.copy(
                    familyId = familyId,
                    items = items,
                    visibilityMembers = picker,
                    isLoading = false,
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun save(
        cardId: String?,
        plain: PaymentCardPlain,
        colorHex: String,
        visibilityScope: String,
        visibilityMemberIds: List<String>,
        onSuccess: (String) -> Unit,
    ) {
        val familyId = _uiState.value.familyId
        val normalized = plain.normalizedForSave()
        if (familyId.isBlank() || normalized.cardNumber.length < 12) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val result = repository.saveCard(familyId, cardId, normalized, colorHex, visibilityScope, visibilityMemberIds)
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                message = result.fold(
                    onSuccess = { null },
                    onFailure = { context.getString(R.string.wallet_payment_save_error) },
                ),
            )
            result.onSuccess(onSuccess)
        }
    }

    fun deleteCard(cardId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch { repository.deleteCard(cardId, familyId) }
    }

    fun setCardPhoto(cardId: String, side: LoyaltyCardPhotoSide, bitmap: Bitmap) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyPhotoSide = side)
            val bytes = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
            }
            val result = repository.setCardPhoto(cardId, familyId, side, bytes)
            _uiState.value = _uiState.value.copy(
                busyPhotoSide = null,
                message = result.fold(
                    onSuccess = { null },
                    onFailure = { context.getString(R.string.wallet_loyalty_photo_upload_error) },
                ),
            )
            result.onSuccess {
                val card = _uiState.value.items.firstOrNull { it.entity.id == cardId }?.entity
                val path = when (side) {
                    LoyaltyCardPhotoSide.FRONT -> card?.frontPhotoStoragePath
                    LoyaltyCardPhotoSide.BACK -> card?.backPhotoStoragePath
                }
                if (path != null) _uiState.value = _uiState.value.copy(photos = _uiState.value.photos + (path to bitmap))
            }
        }
    }

    fun removeCardPhoto(cardId: String, side: LoyaltyCardPhotoSide) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyPhotoSide = side)
            val result = repository.removeCardPhoto(cardId, side)
            _uiState.value = _uiState.value.copy(
                busyPhotoSide = null,
                message = result.fold(
                    onSuccess = { null },
                    onFailure = { context.getString(R.string.wallet_loyalty_photo_delete_error) },
                ),
            )
        }
    }

    fun loadCardPhoto(storagePath: String?) {
        val familyId = _uiState.value.familyId
        if (storagePath.isNullOrBlank() || familyId.isBlank()) return
        if (_uiState.value.photos.containsKey(storagePath)) return
        if (!loadingPhotoPaths.add(storagePath)) return
        viewModelScope.launch {
            val bytes = repository.loadCardPhoto(storagePath, familyId)
            val bitmap = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            loadingPhotoPaths.remove(storagePath)
            if (bitmap != null) {
                _uiState.value = _uiState.value.copy(photos = _uiState.value.photos + (storagePath to bitmap))
            }
        }
    }

    override fun onCleared() {
        // Le foto decifrate non sopravvivono alla schermata.
        _uiState.value = _uiState.value.copy(photos = emptyMap())
        super.onCleared()
    }
}
