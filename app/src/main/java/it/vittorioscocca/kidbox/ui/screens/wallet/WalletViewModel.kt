package it.vittorioscocca.kidbox.ui.screens.wallet

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.WalletRepository
import it.vittorioscocca.kidbox.data.wallet.PendingWalletImport
import it.vittorioscocca.kidbox.data.wallet.WalletParsedData
import it.vittorioscocca.kidbox.data.wallet.WalletPdfParser
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WalletUiState(
    val familyId: String = "",
    val tickets: List<KBWalletTicketEntity> = emptyList(),
    val isLoading: Boolean = true,
    val hasQueuedSharePdf: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val pdfBytesEvent: ByteArray? = null,
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val badgeManager: HomeBadgeManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _parsedData = MutableStateFlow<WalletParsedData?>(null)
    val parsedData: StateFlow<WalletParsedData?> = _parsedData.asStateFlow()

    private var observeJob: Job? = null
    private var boundFamilyId: String? = null

    fun bind(familyId: String) {
        if (familyId.isBlank()) {
            _uiState.value = WalletUiState(isLoading = false, message = "Famiglia non disponibile")
            return
        }
        if (boundFamilyId == familyId && observeJob != null) return
        boundFamilyId = familyId

        badgeManager.clearLocal(CounterField.WALLET)
        viewModelScope.launch { badgeManager.resetRemote(familyId, CounterField.WALLET) }

        walletRepository.startRealtime(familyId)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            walletRepository.observeActiveByFamilyId(familyId).collect { list ->
                _uiState.value = _uiState.value.copy(
                    familyId = familyId,
                    tickets = list,
                    isLoading = false,
                    hasQueuedSharePdf = PendingWalletImport.peek() != null,
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun consumePdfBytes() {
        _uiState.value = _uiState.value.copy(pdfBytesEvent = null)
    }

    fun importQueuedShare() {
        val uri = PendingWalletImport.take() ?: return
        importPdf(uri)
    }

    fun importPdf(uri: Uri) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, message = null)
            val result = walletRepository.importPdfFromUri(familyId, uri)
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                hasQueuedSharePdf = PendingWalletImport.peek() != null,
                message = result.fold(
                    onSuccess = { "Biglietto aggiunto" },
                    onFailure = { it.message ?: "Import non riuscito" },
                ),
            )
        }
    }

    fun parsePdf(context: Context, uri: Uri, fileName: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: run {
                _uiState.value = _uiState.value.copy(isImporting = false, message = "Impossibile leggere il PDF")
                return@launch
            }
            val parsed = runCatching {
                WalletPdfParser.parse(context, bytes, fileName)
            }.getOrElse {
                WalletParsedData(
                    suggestedTitle = fileName?.removeSuffix(".pdf") ?: "Biglietto",
                    kind = WalletTicketKind.OTHER,
                    emitter = null, eventDate = null, location = null,
                    bookingCode = null, barcodeText = null, barcodeFormat = null,
                    notes = null, thumbnailBase64 = null,
                )
            }
            _parsedData.value = parsed
            _uiState.value = _uiState.value.copy(isImporting = false)
        }
    }

    fun addTicketFromForm(
        familyId: String,
        pdfUri: Uri,
        title: String,
        parsed: WalletParsedData,
        context: Context,
        onSuccess: () -> Unit,
    ) {
        if (familyId.isBlank() || title.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, message = null)
            val bytes = runCatching {
                context.contentResolver.openInputStream(pdfUri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                _uiState.value = _uiState.value.copy(isImporting = false, message = "Impossibile leggere il PDF")
                return@launch
            }
            val fileName = runCatching {
                context.contentResolver.query(pdfUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "$title.pdf"

            val result = walletRepository.addTicket(
                familyId = familyId,
                pdfBytes = bytes,
                fileName = fileName,
                parsed = parsed,
                title = title,
            )
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                message = result.fold(onSuccess = { null }, onFailure = { it.message ?: "Errore salvataggio" }),
            )
            if (result.isSuccess) {
                _parsedData.value = null
                onSuccess()
            }
        }
    }

    fun deleteTicket(ticketId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching { walletRepository.deleteTicket(ticketId, familyId) }
                .onFailure { _uiState.value = _uiState.value.copy(message = it.message ?: "Errore eliminazione") }
        }
    }

    fun openPdf(ticketId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            val result = runCatching { walletRepository.openPdf(familyId, ticketId) }
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                pdfBytesEvent = result.getOrNull(),
                message = result.exceptionOrNull()?.message,
            )
        }
    }

    fun refreshQueuedBanner() {
        _uiState.value = _uiState.value.copy(hasQueuedSharePdf = PendingWalletImport.peek() != null)
    }
}
