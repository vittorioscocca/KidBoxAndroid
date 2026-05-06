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
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.sync.MedicalExamSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.notifications.ExamReminderScheduler
import it.vittorioscocca.kidbox.ui.screens.health.common.PrescribingVisitSummary
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MedicalExamDetailState(
    val isLoading: Boolean = true,
    val exam: KBMedicalExam? = null,
    val prescribingVisitSummary: PrescribingVisitSummary? = null,
    val childName: String = "",
    val error: String? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val attachments: List<KBDocumentEntity> = emptyList(),
    val isUploading: Boolean = false,
    val openFileEvent: Pair<String, File>? = null,
    val uploadError: String? = null,
)

@HiltViewModel
class MedicalExamDetailViewModel @Inject constructor(
    private val repository: MedicalExamRepository,
    private val visitRepository: MedicalVisitRepository,
    private val examSyncCenter: MedicalExamSyncCenter,
    private val reminderScheduler: ExamReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val attachmentService: HealthAttachmentService,
    private val documentRepository: DocumentRepository,
    private val aiSettings: AiSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalExamDetailState())
    val uiState: StateFlow<MedicalExamDetailState> = _uiState.asStateFlow()

    val isAiGloballyEnabled: StateFlow<Boolean> get() = aiSettings.isEnabled

    private var familyId: String = ""
    private var childId: String = ""
    private var examId: String = ""
    private var observeExamJob: Job? = null

    fun bind(familyId: String, childId: String, examId: String) {
        this.familyId = familyId
        this.childId = childId
        this.examId = examId
        _uiState.value = MedicalExamDetailState(isLoading = true)
        observeExamJob?.cancel()

        if (familyId.isNotBlank()) {
            examSyncCenter.start(familyId)
        }

        documentRepository.startRealtime(familyId)

        documentRepository.observeAllDocuments(familyId)
            .map { docs -> docs.filter { ExamAttachmentTag.matches(it.notes, examId) } }
            .onEach { _uiState.value = _uiState.value.copy(attachments = it) }
            .launchIn(viewModelScope)

        observeExamJob = viewModelScope.launch {
            val childName = resolveChildName(childId)
            repository.observe(familyId, childId).collect { list ->
                val ex = list.firstOrNull { it.id == examId }
                if (ex == null) {
                    _uiState.value = MedicalExamDetailState(
                        isLoading = false,
                        exam = null,
                        prescribingVisitSummary = null,
                        childName = childName,
                        error = "Esame non trovato",
                    )
                    return@collect
                }
                val presc = ex.prescribingVisitId?.let { vid ->
                    visitRepository.loadOnce(vid)?.let { v ->
                        PrescribingVisitSummary(
                            visitId = v.id,
                            reason = v.reason.ifBlank { "Visita" },
                            dateEpochMillis = v.dateEpochMillis,
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exam = ex,
                    prescribingVisitSummary = presc,
                    childName = childName,
                    error = null,
                )
            }
        }
    }

    fun requestDelete() { _uiState.value = _uiState.value.copy(confirmDelete = true) }
    fun cancelDelete() { _uiState.value = _uiState.value.copy(confirmDelete = false) }

    fun confirmDelete() {
        val exam = _uiState.value.exam ?: return
        _uiState.value = _uiState.value.copy(confirmDelete = false)
        viewModelScope.launch {
            runCatching { repository.softDelete(exam) }
            reminderScheduler.cancelExamReminder(exam.id)
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }

    fun uploadAttachment(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploading = true, uploadError = null)
        viewModelScope.launch {
            attachmentService.uploadExamAttachment(uri, examId, familyId, childId)
                .onSuccess { _uiState.value = _uiState.value.copy(isUploading = false) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(isUploading = false, uploadError = err.message ?: "Errore durante l'upload")
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

    fun consumeOpenFileEvent() { _uiState.value = _uiState.value.copy(openFileEvent = null) }
    fun consumeUploadError() { _uiState.value = _uiState.value.copy(uploadError = null) }

    /** Allinea a iOS: campanella nella card scadenza — aggiorna `reminderOn` e AlarmManager. */
    fun toggleExamReminder() {
        val ex = _uiState.value.exam ?: return
        if (ex.deadlineEpochMillis == null) return
        val childName = _uiState.value.childName
        viewModelScope.launch {
            if (ex.reminderOn) {
                reminderScheduler.cancelExamReminder(ex.id)
                runCatching {
                    val updated = repository.upsert(ex.copy(reminderOn = false))
                    _uiState.value = _uiState.value.copy(exam = updated)
                }
            } else {
                runCatching {
                    val updated = repository.upsert(ex.copy(reminderOn = true))
                    reminderScheduler.scheduleExamReminder(updated, childName)
                    _uiState.value = _uiState.value.copy(exam = updated)
                }
            }
        }
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }
}
