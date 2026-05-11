package it.vittorioscocca.kidbox.ui.screens.homeitems

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.home.HomeItemAttachmentTag
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.HomeItemRepository
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
class HomeItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val homeItemRepository: HomeItemRepository,
    private val documentRepository: DocumentRepository,
    private val attachmentService: HealthAttachmentService,
) : ViewModel() {
    private val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()

    val item: StateFlow<HomeItemEntity?> =
        homeItemRepository.observeById(itemId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _itemAttachments = MutableStateFlow<List<KBDocumentEntity>>(emptyList())
    val itemAttachments: StateFlow<List<KBDocumentEntity>> get() = _itemAttachments

    private val _attachmentUploading = MutableStateFlow(false)
    val attachmentUploading: StateFlow<Boolean> get() = _attachmentUploading

    private val _openFileEvent = MutableStateFlow<Pair<String, File>?>(null)
    val openFileEvent: StateFlow<Pair<String, File>?> get() = _openFileEvent

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> get() = _attachmentError

    init {
        if (familyId.isNotBlank()) {
            homeItemRepository.startRealtime(familyId)
            documentRepository.startRealtime(familyId)
            documentRepository.observeAllDocuments(familyId)
                .map { docs -> docs.filter { HomeItemAttachmentTag.matches(it.notes, itemId) } }
                .onEach { _itemAttachments.value = it }
                .launchIn(viewModelScope)
        }
    }

    override fun onCleared() {
        homeItemRepository.stopRealtime()
        super.onCleared()
    }

    fun uploadHomeItemAttachment(uri: Uri) {
        if (familyId.isBlank() || itemId.isBlank()) return
        _attachmentUploading.value = true
        _attachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadHomeItemAttachment(uri, itemId, familyId)
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

    fun updateHomeItem(entity: HomeItemEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { homeItemRepository.updateHomeItem(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deleteHomeItem(entity: HomeItemEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                attachmentService.deleteAllCasaAttachmentsForHomeItem(entity.id, entity.familyId)
                homeItemRepository.deleteHomeItem(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }
}
