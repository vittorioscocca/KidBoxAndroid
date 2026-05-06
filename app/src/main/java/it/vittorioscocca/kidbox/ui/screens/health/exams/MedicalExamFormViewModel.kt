package it.vittorioscocca.kidbox.ui.screens.health.exams

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.examStatusFromRaw
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.notifications.ExamReminderScheduler
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MedicalExamFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val examId: String = UUID.randomUUID().toString(),
    val childName: String = "",
    val name: String = "",
    val isUrgent: Boolean = false,
    val hasDeadline: Boolean = false,
    val deadlineEpochMillis: Long = System.currentTimeMillis(),
    val preparation: String = "",
    val notes: String = "",
    val location: String = "",
    val status: KBExamStatus = KBExamStatus.PENDING,
    val reminderOn: Boolean = false,
    val hasResult: Boolean = false,
    val resultText: String = "",
    val resultDateEpochMillis: Long = System.currentTimeMillis(),
    val attachments: List<KBDocumentEntity> = emptyList(),
    val isUploading: Boolean = false,
    val openFileEvent: Pair<String, File>? = null,
    val uploadError: String? = null,
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class MedicalExamFormViewModel @Inject constructor(
    private val repository: MedicalExamRepository,
    private val reminderScheduler: ExamReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val attachmentService: HealthAttachmentService,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalExamFormState())
    val uiState: StateFlow<MedicalExamFormState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var boundPrescribingVisitId: String? = null

    fun bind(
        familyId: String,
        childId: String,
        examId: String?,
        prescribingVisitId: String? = null,
        /** Increment when aprendo di nuovo il foglio “nuovo esame” dalla visita così si rigenera l’id. */
        bindNonce: Int = 0,
    ) {
        this.familyId = familyId
        this.childId = childId
        this.boundPrescribingVisitId = prescribingVisitId

        viewModelScope.launch {
            val name = resolveChildName(childId)
            if (examId != null) {
                loadExamIntoState(examId, name)
            } else {
                _uiState.value = MedicalExamFormState(
                    examId = UUID.randomUUID().toString(),
                    childName = name,
                )
            }
        }

        documentRepository.startRealtime(familyId)
        documentRepository.observeAllDocuments(familyId)
            .map { docs -> docs.filter { ExamAttachmentTag.matches(it.notes, _uiState.value.examId) } }
            .onEach { docs -> _uiState.value = _uiState.value.copy(attachments = docs) }
            .launchIn(viewModelScope)
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    private suspend fun loadExamIntoState(examId: String, childName: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, childName = childName)
        val exam = repository.getById(examId)
        if (exam != null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                examId = exam.id,
                childName = childName,
                name = exam.name,
                isUrgent = exam.isUrgent,
                hasDeadline = exam.deadlineEpochMillis != null,
                deadlineEpochMillis = exam.deadlineEpochMillis ?: System.currentTimeMillis(),
                preparation = exam.preparation.orEmpty(),
                notes = exam.notes.orEmpty(),
                location = exam.location.orEmpty(),
                status = examStatusFromRaw(exam.statusRaw),
                reminderOn = exam.reminderOn,
                hasResult = exam.resultText != null || exam.resultDateEpochMillis != null,
                resultText = exam.resultText.orEmpty(),
                resultDateEpochMillis = exam.resultDateEpochMillis ?: System.currentTimeMillis(),
            )
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false, childName = childName)
        }
    }

    fun setName(v: String) { _uiState.value = _uiState.value.copy(name = v) }
    fun setIsUrgent(v: Boolean) { _uiState.value = _uiState.value.copy(isUrgent = v) }
    fun setHasDeadline(v: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasDeadline = v,
            reminderOn = if (!v) false else _uiState.value.reminderOn,
        )
    }
    fun setDeadlineEpochMillis(v: Long) { _uiState.value = _uiState.value.copy(deadlineEpochMillis = v) }
    fun setStatus(v: KBExamStatus) { _uiState.value = _uiState.value.copy(status = v) }
    fun setReminderOn(v: Boolean) { _uiState.value = _uiState.value.copy(reminderOn = v) }
    fun setLocation(v: String) { _uiState.value = _uiState.value.copy(location = v) }
    fun setPreparation(v: String) { _uiState.value = _uiState.value.copy(preparation = v) }
    fun setNotes(v: String) { _uiState.value = _uiState.value.copy(notes = v) }
    fun setHasResult(v: Boolean) { _uiState.value = _uiState.value.copy(hasResult = v) }
    fun setResultText(v: String) { _uiState.value = _uiState.value.copy(resultText = v) }
    fun setResultDateEpochMillis(v: Long) { _uiState.value = _uiState.value.copy(resultDateEpochMillis = v) }
    fun consumeOpenFileEvent() { _uiState.value = _uiState.value.copy(openFileEvent = null) }
    fun consumeUploadError() { _uiState.value = _uiState.value.copy(uploadError = null) }
    fun consumeSaved() { _uiState.value = _uiState.value.copy(saved = false) }

    fun uploadAttachment(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploading = true, uploadError = null)
        viewModelScope.launch {
            attachmentService.uploadExamAttachment(uri, _uiState.value.examId, familyId, childId)
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
        viewModelScope.launch { attachmentService.deleteAttachment(doc) }
    }

    fun openAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch {
            attachmentService.downloadAttachment(doc)
                .onSuccess { file -> _uiState.value = _uiState.value.copy(openFileEvent = doc.mimeType to file) }
                .onFailure { err -> _uiState.value = _uiState.value.copy(uploadError = err.message ?: "Errore apertura file") }
        }
    }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deadline = if (s.hasDeadline) s.deadlineEpochMillis else null
            val effectiveStatus = if (s.hasResult && s.status != KBExamStatus.RESULT_IN) {
                KBExamStatus.RESULT_IN
            } else {
                s.status
            }
            val exam = KBMedicalExam(
                id = s.examId,
                familyId = familyId,
                childId = childId,
                name = s.name.trim(),
                isUrgent = s.isUrgent,
                deadlineEpochMillis = deadline,
                preparation = s.preparation.takeIf { it.isNotBlank() },
                notes = s.notes.takeIf { it.isNotBlank() },
                location = s.location.takeIf { it.isNotBlank() },
                statusRaw = effectiveStatus.rawValue,
                resultText = if (s.hasResult) s.resultText.takeIf { it.isNotBlank() } else null,
                resultDateEpochMillis = if (s.hasResult) s.resultDateEpochMillis else null,
                prescribingVisitId = boundPrescribingVisitId,
                reminderOn = s.reminderOn && deadline != null,
                isDeleted = false,
                syncStateRaw = 0,
                lastSyncError = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = "",
                createdBy = "",
            )
            runCatching { repository.upsert(exam) }
                .fold(
                    onSuccess = { savedExam ->
                        if (savedExam.reminderOn && savedExam.deadlineEpochMillis != null) {
                            reminderScheduler.scheduleExamReminder(savedExam, s.childName)
                        } else {
                            reminderScheduler.cancelExamReminder(savedExam.id)
                        }
                        _uiState.value = _uiState.value.copy(isSaving = false, saved = true, saveError = null)
                    },
                    onFailure = { err ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            saveError = err.message ?: "Errore sconosciuto",
                        )
                    },
                )
        }
    }
}
