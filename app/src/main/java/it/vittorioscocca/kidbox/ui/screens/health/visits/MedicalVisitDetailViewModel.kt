package it.vittorioscocca.kidbox.ui.screens.health.visits

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.VisitAttachmentTag
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.notifications.VisitReminderScheduler
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class LinkedPrescriptionRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

data class MedicalVisitDetailState(
    val isLoading: Boolean = true,
    val visit: KBMedicalVisit? = null,
    val childName: String = "",
    val linkedTreatments: List<LinkedPrescriptionRow> = emptyList(),
    val linkedExams: List<LinkedPrescriptionRow> = emptyList(),
    val error: String? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val attachments: List<KBDocumentEntity> = emptyList(),
    val isUploading: Boolean = false,
    val openFileEvent: Pair<String, File>? = null, // mimeType to File
    val uploadError: String? = null,
)

@HiltViewModel
class MedicalVisitDetailViewModel @Inject constructor(
    private val repository: MedicalVisitRepository,
    private val treatmentRepository: TreatmentRepository,
    private val examRepository: MedicalExamRepository,
    private val reminderScheduler: VisitReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val attachmentService: HealthAttachmentService,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVisitDetailState())
    val uiState: StateFlow<MedicalVisitDetailState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var visitId: String = ""

    fun bind(familyId: String, childId: String, visitId: String) {
        this.familyId = familyId
        this.childId = childId
        this.visitId = visitId
        _uiState.value = MedicalVisitDetailState(isLoading = true)

        viewModelScope.launch {
            val childName = resolveChildName(childId)
            val visit = repository.loadOnce(visitId)
            val treatmentRows = if (visit != null) {
                decodeStringList(visit.linkedTreatmentIdsJson).mapNotNull { tid ->
                    treatmentRepository.getById(tid)?.let { t ->
                        val dosage = if (t.dosageValue % 1.0 == 0.0) "%.0f".format(t.dosageValue) else "%.1f".format(t.dosageValue)
                        LinkedPrescriptionRow(
                            id = t.id,
                            title = t.drugName,
                            subtitle = "$dosage ${t.dosageUnit} · ${t.dailyFrequency}x/die",
                        )
                    }
                }
            } else {
                emptyList()
            }
            val examRows = if (visit != null) {
                decodeStringList(visit.linkedExamIdsJson).mapNotNull { eid ->
                    examRepository.getById(eid)?.let { e ->
                        LinkedPrescriptionRow(
                            id = e.id,
                            title = e.name,
                            subtitle = if (e.isUrgent) "Urgente" else null,
                        )
                    }
                }
            } else {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                visit = visit,
                childName = childName,
                linkedTreatments = treatmentRows,
                linkedExams = examRows,
                error = if (visit == null) "Visita non trovata" else null,
            )
        }

        documentRepository.startRealtime(familyId)

        documentRepository.observeAllDocuments(familyId)
            .map { docs -> docs.filter { VisitAttachmentTag.matches(it.notes, visitId) } }
            .onEach { _uiState.value = _uiState.value.copy(attachments = it) }
            .launchIn(viewModelScope)
    }

    fun requestDelete() { _uiState.value = _uiState.value.copy(confirmDelete = true) }
    fun cancelDelete() { _uiState.value = _uiState.value.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = _uiState.value.visit?.id ?: return
        _uiState.value = _uiState.value.copy(confirmDelete = false)
        viewModelScope.launch {
            runCatching { repository.delete(id, familyId) }
            reminderScheduler.cancel("${id}_reminder", id)
            reminderScheduler.cancel("${id}_next_reminder", id)
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }

    fun uploadAttachment(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploading = true, uploadError = null)
        viewModelScope.launch {
            attachmentService.uploadVisitAttachment(uri, visitId, familyId, childId)
                .onSuccess { _uiState.value = _uiState.value.copy(isUploading = false) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadError = err.message ?: "Errore durante l'upload",
                    )
                }
        }
    }

    fun deleteAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch {
            attachmentService.deleteAttachment(doc)
        }
    }

    fun openAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch {
            attachmentService.downloadAttachment(doc)
                .onSuccess { file ->
                    _uiState.value = _uiState.value.copy(openFileEvent = doc.mimeType to file)
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(uploadError = err.message ?: "Errore apertura file")
                }
        }
    }

    fun consumeOpenFileEvent() {
        _uiState.value = _uiState.value.copy(openFileEvent = null)
    }

    fun consumeUploadError() {
        _uiState.value = _uiState.value.copy(uploadError = null)
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }
}
