package it.vittorioscocca.kidbox.ui.screens.pets

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.data.pets.PetAttachmentTag
import it.vittorioscocca.kidbox.data.pets.PetEventAttachmentTag
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.PetEventRepository
import it.vittorioscocca.kidbox.data.repository.PetRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val petRepository: PetRepository,
    private val petEventRepository: PetEventRepository,
    private val treatmentRepository: TreatmentRepository,
    private val documentRepository: DocumentRepository,
    private val attachmentService: HealthAttachmentService,
    private val treatmentSyncCenter: TreatmentSyncCenter,
    private val treatmentNotificationManager: TreatmentNotificationManager,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val petId: String = savedStateHandle.get<String>("petId").orEmpty()

    val pet: StateFlow<PetEntity?> = petRepository.observeById(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<PetEventEntity>> =
        petEventRepository.observeByPet(familyId, petId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val treatments: StateFlow<List<KBTreatment>> =
        treatmentRepository.observeByFamilyAndPet(familyId, petId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Gli allegati della scheda animale: libretto, pedigree, referti. */
    private val _petAttachments = MutableStateFlow<List<KBDocumentEntity>>(emptyList())
    val petAttachments: StateFlow<List<KBDocumentEntity>> get() = _petAttachments

    // Gli allegati caricati mentre l'evento è ancora in bozza: sono già in
    // Documenti, taggati con l'id che l'evento avrà quando lo salvi.
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
            petRepository.startRealtime(familyId)
            petEventRepository.startRealtime(familyId)
            treatmentSyncCenter.start(familyId)
            documentRepository.startRealtime(familyId)

            documentRepository.observeAllDocuments(familyId)
                .map { docs -> docs.filter { PetAttachmentTag.matches(it.notes, petId) } }
                .onEach { _petAttachments.value = it }
                .launchIn(viewModelScope)

            combine(documentRepository.observeAllDocuments(familyId), _draftEventId) { docs, draftId ->
                if (draftId.isNullOrBlank()) emptyList()
                else docs.filter { PetEventAttachmentTag.matches(it.notes, draftId) }
            }
                .onEach { _eventDraftAttachments.value = it }
                .launchIn(viewModelScope)
        }
    }

    fun bindEventDraftAttachments(draftEventId: String?) {
        _draftEventId.value = draftEventId?.takeIf { it.isNotBlank() }
    }

    fun uploadPetAttachment(uri: Uri) {
        if (familyId.isBlank() || petId.isBlank()) return
        _attachmentUploading.value = true
        _attachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadPetAttachment(uri, petId, familyId)
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
            attachmentService.uploadPetEventAttachment(uri, draftId, familyId)
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

    // L'evento che non viene salvato non deve lasciare allegati orfani in Documenti.
    fun discardDraftAttachments(draftEventId: String) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            attachmentService.deleteAllAttachmentsForPetEvent(draftEventId, familyId)
        }
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

    override fun onCleared() {
        petRepository.stopRealtime()
        petEventRepository.stopRealtime()
        super.onCleared()
    }

    fun addPetEvent(
        eventId: String?,
        title: String,
        eventType: String,
        dateMillis: Long,
        nextDueDate: Long?,
        vetName: String?,
        cost: Double?,
        notes: String?,
        reminderEnabled: Boolean,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank() || petId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                petEventRepository.addPetEvent(
                    familyId = familyId,
                    petId = petId,
                    title = title,
                    eventType = eventType,
                    dateMillis = dateMillis,
                    nextDueDate = nextDueDate,
                    vetName = vetName,
                    cost = cost,
                    notes = notes,
                    reminderEnabled = reminderEnabled,
                    presetEventId = eventId,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deletePet(entity: PetEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                attachmentService.deleteAllAttachmentsForPet(entity.id, entity.familyId)
                val treats = treatmentRepository.listByFamilyAndPet(entity.familyId, entity.id)
                treats.forEach { t ->
                    treatmentNotificationManager.cancel(t.id)
                    treatmentRepository.softDelete(t)
                }
                petRepository.deletePet(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun updatePet(entity: PetEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { petRepository.updatePet(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun updatePetEvent(entity: PetEventEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { petEventRepository.updatePetEvent(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deletePetEvent(entity: PetEventEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                // Gli allegati se ne vanno con l'evento, come su iOS: da soli
                // resterebbero in Documenti senza più niente che li spieghi.
                attachmentService.deleteAllAttachmentsForPetEvent(entity.id, entity.familyId)
                petEventRepository.deletePetEvent(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }
}
