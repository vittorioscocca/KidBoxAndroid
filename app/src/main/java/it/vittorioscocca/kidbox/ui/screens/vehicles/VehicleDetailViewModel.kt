package it.vittorioscocca.kidbox.ui.screens.vehicles

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.VehicleEventRepository
import it.vittorioscocca.kidbox.data.repository.VehicleRepository
import it.vittorioscocca.kidbox.data.vehicles.VehicleAttachmentTag
import it.vittorioscocca.kidbox.data.vehicles.VehicleEventAttachmentTag
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val vehicleEventRepository: VehicleEventRepository,
    private val documentRepository: DocumentRepository,
    private val attachmentService: HealthAttachmentService,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val vehicleId: String = savedStateHandle.get<String>("vehicleId").orEmpty()

    val vehicle: StateFlow<VehicleEntity?> =
        vehicleRepository.observeById(vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val vehicleEvents: StateFlow<List<VehicleEventEntity>> =
        vehicleEventRepository.observeByVehicle(familyId, vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _vehicleAttachments = MutableStateFlow<List<KBDocumentEntity>>(emptyList())
    val vehicleAttachments: StateFlow<List<KBDocumentEntity>> get() = _vehicleAttachments

    private val _draftEventId = MutableStateFlow<String?>(null)
    private val _eventDraftAttachments = MutableStateFlow<List<KBDocumentEntity>>(emptyList())
    val eventDraftAttachments: StateFlow<List<KBDocumentEntity>> get() = _eventDraftAttachments

    private val _attachmentUploading = MutableStateFlow(false)
    val attachmentUploading: StateFlow<Boolean> get() = _attachmentUploading

    private val _openFileEvent = MutableStateFlow<Pair<String, File>?>(null)
    val openFileEvent: StateFlow<Pair<String, File>?> get() = _openFileEvent

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> get() = _attachmentError

    init {
        if (familyId.isNotBlank()) {
            vehicleRepository.startRealtime(familyId)
            vehicleEventRepository.startRealtime(familyId)
            documentRepository.startRealtime(familyId)

            documentRepository.observeAllDocuments(familyId)
                .map { docs -> docs.filter { VehicleAttachmentTag.matches(it.notes, vehicleId) } }
                .onEach { _vehicleAttachments.value = it }
                .launchIn(viewModelScope)

            combine(documentRepository.observeAllDocuments(familyId), _draftEventId) { docs, draftId ->
                if (draftId.isNullOrBlank()) emptyList()
                else docs.filter { VehicleEventAttachmentTag.matches(it.notes, draftId) }
            }
                .onEach { _eventDraftAttachments.value = it }
                .launchIn(viewModelScope)
        }
    }

    override fun onCleared() {
        vehicleRepository.stopRealtime()
        vehicleEventRepository.stopRealtime()
        super.onCleared()
    }

    fun bindEventDraftAttachments(draftEventId: String?) {
        _draftEventId.value = draftEventId?.takeIf { it.isNotBlank() }
    }

    fun uploadVehicleAttachment(uri: Uri) {
        if (familyId.isBlank() || vehicleId.isBlank()) return
        _attachmentUploading.value = true
        _attachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadVehicleAttachment(uri, vehicleId, familyId)
                .onSuccess { _attachmentUploading.value = false }
                .onFailure { err ->
                    _attachmentUploading.value = false
                    _attachmentError.value = err.message ?: "Errore durante l'upload"
                }
        }
    }

    fun uploadEventDraftAttachment(uri: Uri) {
        val draftId = _draftEventId.value ?: return
        if (familyId.isBlank()) return
        _attachmentUploading.value = true
        _attachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadVehicleEventAttachment(uri, draftId, familyId)
                .onSuccess { _attachmentUploading.value = false }
                .onFailure { err ->
                    _attachmentUploading.value = false
                    _attachmentError.value = err.message ?: "Errore durante l'upload"
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
                .onFailure { err -> _attachmentError.value = err.message ?: "Errore apertura file" }
        }
    }

    fun consumeOpenFileEvent() {
        _openFileEvent.value = null
    }

    fun consumeAttachmentError() {
        _attachmentError.value = null
    }

    suspend fun deleteAllAttachmentsForDraftEvent(draftEventId: String) {
        if (familyId.isBlank() || draftEventId.isBlank()) return
        attachmentService.deleteAllGarageAttachmentsForVehicleEvent(draftEventId, familyId)
    }

    fun addVehicleEvent(
        presetEventId: String?,
        title: String,
        eventType: String,
        dateMillis: Long,
        km: Int?,
        cost: Double?,
        garageName: String?,
        notes: String?,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank() || vehicleId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                vehicleEventRepository.addVehicleEvent(
                    familyId = familyId,
                    vehicleId = vehicleId,
                    title = title,
                    eventType = eventType,
                    dateMillis = dateMillis,
                    km = km,
                    cost = cost,
                    garageName = garageName,
                    notes = notes,
                    presetEventId = presetEventId,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deleteVehicle(entity: VehicleEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val events = vehicleEventRepository.listActiveByVehicle(entity.familyId, entity.id)
                for (e in events) {
                    attachmentService.deleteAllGarageAttachmentsForVehicleEvent(e.id, entity.familyId)
                }
                attachmentService.deleteAllGarageAttachmentsForVehicle(entity.id, entity.familyId)
                vehicleRepository.deleteVehicle(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun updateVehicle(entity: VehicleEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { vehicleRepository.updateVehicle(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deleteVehicleEvent(entity: VehicleEventEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                attachmentService.deleteAllGarageAttachmentsForVehicleEvent(entity.id, entity.familyId)
                vehicleEventRepository.deleteVehicleEvent(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }
}
