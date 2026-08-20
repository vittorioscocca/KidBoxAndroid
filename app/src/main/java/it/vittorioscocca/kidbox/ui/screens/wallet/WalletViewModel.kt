package it.vittorioscocca.kidbox.ui.screens.wallet

import it.vittorioscocca.kidbox.R
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.WalletRepository
import it.vittorioscocca.kidbox.data.wallet.PendingWalletImport
import it.vittorioscocca.kidbox.data.wallet.WalletParsedData
import it.vittorioscocca.kidbox.data.wallet.WalletPdfParser
import it.vittorioscocca.kidbox.data.wallet.WalletTicketAIExtractor
import it.vittorioscocca.kidbox.data.wallet.WalletTicketExtraction
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerMember
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class WalletUiState(
    val familyId: String = "",
    val tickets: List<KBWalletTicketEntity> = emptyList(),
    val visibilityMembers: List<VisibilityPickerMember> = emptyList(),
    val isLoading: Boolean = true,
    val hasQueuedSharePdf: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val pdfBytesEvent: ByteArray? = null,
    val currentPlan: KBPlan = KBPlan.FREE,
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val familyMemberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
    private val badgeManager: HomeBadgeManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val ticketAIExtractor: WalletTicketAIExtractor,
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

        viewModelScope.launch {
            val plan = subscriptionRepository.getPlan(familyId)
            _uiState.value = _uiState.value.copy(currentPlan = plan)
        }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                walletRepository.observeActiveByFamilyId(familyId),
                familyMemberDao.observeActiveByFamilyId(familyId),
            ) { tickets, members ->
                val uid = auth.currentUser?.uid
                val picker = members
                    .asSequence()
                    .filter { it.userId != uid }
                    .map { m ->
                        VisibilityPickerMember(
                            uid = m.userId,
                            displayName = m.displayName?.takeIf { it.isNotBlank() } ?: "Membro",
                        )
                    }
                    .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
                    .toList()
                tickets to picker
            }.collect { (tickets, picker) ->
                _uiState.value = _uiState.value.copy(
                    familyId = familyId,
                    tickets = tickets,
                    visibilityMembers = picker,
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
                _uiState.value = _uiState.value.copy(isImporting = false, message = context.getString(R.string.wallet_cannot_read_pdf))
                return@launch
            }
            val parsed = runCatching {
                WalletPdfParser.parse(context, bytes, fileName)
            }.getOrElse {
                WalletParsedData(
                    suggestedTitle = fileName?.removeSuffix(".pdf") ?: context.getString(R.string.wallet_default_ticket_title),
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
        visibilityScope: String,
        visibilityMemberIds: List<String>,
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
                _uiState.value = _uiState.value.copy(isImporting = false, message = context.getString(R.string.wallet_cannot_read_pdf))
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
                visibilityScope = visibilityScope,
                visibilityMemberIds = visibilityMemberIds,
            )
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                message = result.fold(onSuccess = { null }, onFailure = { it.message ?: context.getString(R.string.wallet_save_error) }),
            )
            if (result.isSuccess) {
                _parsedData.value = null
                onSuccess()
            }
        }
    }

    /** Lettura AI del biglietto (piano Max): [text] è il testo già estratto dal PDF (vedi [WalletParsedData.rawText]). */
    suspend fun runAiTicketExtraction(
        text: String?,
        fallbackBitmap: android.graphics.Bitmap?,
        familyId: String,
    ): Result<WalletTicketExtraction> = ticketAIExtractor.extract(text, fallbackBitmap, familyId)

    fun estimatedAiTicketMessageCost(usedImageFallback: Boolean): Int =
        WalletTicketAIExtractor.estimatedMessageUnits(usedImageFallback)

    fun deleteTicket(ticketId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching { walletRepository.deleteTicket(ticketId, familyId) }
                .onFailure { _uiState.value = _uiState.value.copy(message = it.message ?: "Errore eliminazione") }
        }
    }

    fun updateTicketVisibility(ticketId: String, visibilityScope: String, visibilityMemberIds: List<String>) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            val result = walletRepository.updateWalletTicketVisibility(
                ticketId = ticketId,
                familyId = familyId,
                visibilityScope = visibilityScope,
                visibilityMemberIds = visibilityMemberIds,
            )
            _uiState.value = _uiState.value.copy(
                message = result.fold(
                    onSuccess = { "Visibilità aggiornata" },
                    onFailure = { it.message ?: "Aggiornamento non riuscito" },
                ),
            )
        }
    }

    fun updateTicketReminderOffset(ticketId: String, reminderOffsetHours: Int?) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            val result = walletRepository.updateTicketReminderOffset(
                ticketId = ticketId,
                familyId = familyId,
                reminderOffsetHours = reminderOffsetHours,
            )
            _uiState.value = _uiState.value.copy(
                message = result.fold(
                    onSuccess = { "Promemoria aggiornato" },
                    onFailure = { it.message ?: "Aggiornamento non riuscito" },
                ),
            )
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
