package it.vittorioscocca.kidbox.ui.screens.homeitems

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.home.HousePaymentAttachmentTag
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.HousePaymentRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HousePaymentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val housePaymentRepository: HousePaymentRepository,
    private val documentRepository: DocumentRepository,
    private val attachmentService: HealthAttachmentService,
) : ViewModel() {
    private val paymentId: String = savedStateHandle.get<String>("paymentId").orEmpty()
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()

    val payment: StateFlow<HousePaymentEntity?> =
        housePaymentRepository.observeById(paymentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _paymentAttachments = MutableStateFlow<List<KBDocumentEntity>>(emptyList())
    val paymentAttachments: StateFlow<List<KBDocumentEntity>> get() = _paymentAttachments

    private val _attachmentUploading = MutableStateFlow(false)
    val attachmentUploading: StateFlow<Boolean> get() = _attachmentUploading

    private val _openFileEvent = MutableStateFlow<Pair<String, File>?>(null)
    val openFileEvent: StateFlow<Pair<String, File>?> get() = _openFileEvent

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> get() = _attachmentError

    init {
        if (familyId.isNotBlank()) {
            housePaymentRepository.startRealtime(familyId)
            documentRepository.startRealtime(familyId)
            documentRepository.observeAllDocuments(familyId)
                .map { docs -> docs.filter { HousePaymentAttachmentTag.matches(it.notes, paymentId) } }
                .onEach { _paymentAttachments.value = it }
                .launchIn(viewModelScope)
        }
    }

    override fun onCleared() {
        housePaymentRepository.stopRealtime()
        super.onCleared()
    }

    fun uploadHousePaymentAttachment(uri: Uri) {
        if (familyId.isBlank() || paymentId.isBlank()) return
        _attachmentUploading.value = true
        _attachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadHousePaymentAttachment(uri, paymentId, familyId)
                .onSuccess { _attachmentUploading.value = false }
                .onFailure {
                    _attachmentUploading.value = false
                    _attachmentError.value = it.message ?: "Errore durante l'upload"
                }
        }
    }

    fun deleteAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch { attachmentService.deleteAttachment(doc) }
    }

    fun openAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch {
            attachmentService.downloadAttachment(doc)
                .onSuccess { file -> _openFileEvent.value = doc.mimeType to file }
                .onFailure { _attachmentError.value = it.message ?: "Errore apertura file" }
        }
    }

    fun consumeOpenFileEvent() {
        _openFileEvent.value = null
    }

    fun consumeAttachmentError() {
        _attachmentError.value = null
    }

    fun updateHousePayment(entity: HousePaymentEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { housePaymentRepository.updateHousePayment(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deleteHousePayment(entity: HousePaymentEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                attachmentService.deleteAllCasaAttachmentsForHousePayment(entity.id, entity.familyId)
                housePaymentRepository.deleteHousePayment(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }
}
