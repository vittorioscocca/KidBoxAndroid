package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import it.vittorioscocca.kidbox.data.repository.LoyaltyCardPhotoSide
import it.vittorioscocca.kidbox.data.repository.LoyaltyCardRepository
import java.io.ByteArrayOutputStream
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoyaltyCardsUiState(
    val familyId: String = "",
    val cards: List<KBLoyaltyCardEntity> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    /** Foto tessera già scaricate e decifrate, indicizzate per path Storage. */
    val photos: Map<String, Bitmap> = emptyMap(),
    /** Lato in upload/rimozione: la UI mostra uno spinner al posto dello slot. */
    val busyPhotoSide: LoyaltyCardPhotoSide? = null,
    /** Modalità selezione multipla attiva (stessa UX della schermata Password). */
    val isSelecting: Boolean = false,
    /** Id delle carte spuntate in modalità selezione. */
    val selectedIds: Set<String> = emptySet(),
)

/**
 * ViewModel dedicato alla sezione "Carte" del Wallet, separato da
 * [it.vittorioscocca.kidbox.ui.screens.wallet.WalletViewModel] — stessa
 * convenzione già usata per `WalletDocumentsViewModel` (sotto-feature con
 * stato/logica propri).
 */
@HiltViewModel
class LoyaltyCardsViewModel @Inject constructor(
    private val repository: LoyaltyCardRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoyaltyCardsUiState())
    val uiState: StateFlow<LoyaltyCardsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundFamilyId: String? = null

    /** Download in corso, per non scaricare due volte la stessa foto. */
    private val loadingPhotoPaths = mutableSetOf<String>()

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    /** Pull-to-refresh: rilegge le carte fedeltà da Firestore. */
    fun forceRefresh() = pullToRefresh.refresh {
        val familyId = boundFamilyId ?: return@refresh
        repository.awaitForceRestartRealtime(familyId)
    }

    fun bind(familyId: String) {
        if (familyId.isBlank()) {
            _uiState.value = LoyaltyCardsUiState(isLoading = false)
            return
        }
        if (boundFamilyId == familyId && observeJob != null) return
        boundFamilyId = familyId

        repository.startRealtime(familyId)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeActiveByFamilyId(familyId).collect { cards ->
                _uiState.value = _uiState.value.copy(
                    familyId = familyId,
                    cards = cards,
                    isLoading = false,
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun addCard(
        brandId: String?,
        brandName: String,
        cardNumber: String,
        barcodeFormat: String,
        note: String?,
        primaryColorHex: String,
        secondaryColorHex: String,
        logoURL: String? = null,
        onSuccess: (String) -> Unit,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || brandName.isBlank() || cardNumber.isBlank()) return
        viewModelScope.launch {
            // Colori della card dal logo REALE del brand, risolti UNA VOLTA SOLA qui
            // alla creazione e poi persistiti/sincronizzati: ricalcolarli a ogni
            // recomposition sarebbe costoso e farebbe sfarfallare la UI.
            // Se il logo manca, non si scarica (offline), o il colore estratto è
            // troppo slavato/chiaro per reggere il testo bianco, l'estrattore
            // restituisce null e restano i colori curati del catalogo.
            // Il flusso "carta non in elenco" (brandId null, colore scelto
            // dall'utente) non passa mai di qui: non ha logoURL.
            val extracted = if (brandId != null) {
                LoyaltyCardColorExtractor.colorsFromLogo(context, logoURL)
            } else {
                null
            }
            val result = repository.addCard(
                familyId = familyId,
                brandId = brandId,
                brandName = brandName,
                cardNumber = cardNumber,
                barcodeFormat = barcodeFormat,
                note = note,
                primaryColorHex = extracted?.primaryHex ?: primaryColorHex,
                secondaryColorHex = extracted?.secondaryHex ?: secondaryColorHex,
                logoURL = logoURL,
            )
            _uiState.value = _uiState.value.copy(
                message = result.fold(onSuccess = { null }, onFailure = { it.message ?: context.getString(R.string.wallet_loyalty_save_error) }),
            )
            result.onSuccess { onSuccess(it) }
        }
    }

    fun updateCard(cardId: String, cardNumber: String, note: String?) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            val result = repository.updateCard(cardId, familyId, cardNumber, note)
            _uiState.value = _uiState.value.copy(
                message = result.fold(onSuccess = { null }, onFailure = { it.message ?: context.getString(R.string.wallet_loyalty_update_error) }),
            )
        }
    }

    /**
     * Salva la foto di un lato della tessera: il bitmap dello scanner viene
     * compresso in JPEG e poi CIFRATO dal repository prima dell'upload.
     */
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
                    onFailure = { it.message ?: context.getString(R.string.wallet_loyalty_photo_upload_error) },
                ),
            )
            // La miniatura è già in memoria: evita un round-trip di download.
            result.onSuccess {
                val path = currentPhotoPath(cardId, side)
                if (path != null) {
                    _uiState.value = _uiState.value.copy(photos = _uiState.value.photos + (path to bitmap))
                }
            }
        }
    }

    fun removeCardPhoto(cardId: String, side: LoyaltyCardPhotoSide) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyPhotoSide = side)
            val result = repository.removeCardPhoto(cardId, familyId, side)
            _uiState.value = _uiState.value.copy(
                busyPhotoSide = null,
                message = result.fold(
                    onSuccess = { null },
                    onFailure = { it.message ?: context.getString(R.string.wallet_loyalty_photo_delete_error) },
                ),
            )
        }
    }

    /** Scarica e decifra la foto se non è già in [LoyaltyCardsUiState.photos]. */
    fun loadCardPhoto(storagePath: String?) {
        val familyId = _uiState.value.familyId
        if (storagePath.isNullOrBlank() || familyId.isBlank()) return
        if (_uiState.value.photos.containsKey(storagePath)) return
        if (!loadingPhotoPaths.add(storagePath)) return
        viewModelScope.launch {
            val bytes = repository.loadCardPhoto(storagePath, familyId)
            val bitmap = bytes?.let {
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            loadingPhotoPaths.remove(storagePath)
            if (bitmap != null) {
                _uiState.value = _uiState.value.copy(photos = _uiState.value.photos + (storagePath to bitmap))
            }
        }
    }

    private fun currentPhotoPath(cardId: String, side: LoyaltyCardPhotoSide): String? {
        val card = _uiState.value.cards.firstOrNull { it.id == cardId } ?: return null
        return when (side) {
            LoyaltyCardPhotoSide.FRONT -> card.frontPhotoStoragePath
            LoyaltyCardPhotoSide.BACK -> card.backPhotoStoragePath
        }
    }

    /** Entra/esce dalla modalità selezione; uscendo la selezione si azzera. */
    fun setSelecting(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSelecting = value,
            selectedIds = if (value) _uiState.value.selectedIds else emptySet(),
        )
    }

    fun toggleSelected(cardId: String) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (current.contains(cardId)) current - cardId else current + cardId,
        )
    }

    /** Azzera la selezione senza uscire dalla modalità (cambio ricerca/ordine). */
    fun clearSelection() {
        if (_uiState.value.selectedIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    fun deleteSelectedCards() {
        val familyId = _uiState.value.familyId
        val ids = _uiState.value.selectedIds.toList()
        if (familyId.isBlank() || ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteCards(ids, familyId) }
                .onFailure { _uiState.value = _uiState.value.copy(message = it.message ?: context.getString(R.string.wallet_loyalty_delete_error)) }
            _uiState.value = _uiState.value.copy(isSelecting = false, selectedIds = emptySet())
        }
    }

    fun deleteCard(cardId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.deleteCard(cardId, familyId) }
                .onFailure { _uiState.value = _uiState.value.copy(message = it.message ?: context.getString(R.string.wallet_loyalty_delete_error)) }
        }
    }
}
