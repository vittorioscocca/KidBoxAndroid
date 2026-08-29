package it.vittorioscocca.kidbox.ui.screens.health.exams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.examStatusFromRaw
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.ExpenseRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.notifications.ExamReminderScheduler
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val costText: String = "",
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
    private val expenseRepository: ExpenseRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** L'id della spesa collegata all'esame in modifica, se ne ha una. */
    private var loadedLinkedExpenseId: String? = null

    private val _uiState = MutableStateFlow(MedicalExamFormState())
    val uiState: StateFlow<MedicalExamFormState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var boundPrescribingVisitId: String? = null
    private var attachmentsJob: Job? = null
    private var saveAsDraftHidden: Boolean = false

    fun bind(
        familyId: String,
        childId: String,
        examId: String?,
        prescribingVisitId: String? = null,
        saveAsDraftHidden: Boolean = false,
        /** Increment when aprendo di nuovo il foglio “nuovo esame” dalla visita così si rigenera l’id. */
        bindNonce: Int = 0,
    ) {
        this.familyId = familyId
        this.childId = childId
        this.boundPrescribingVisitId = prescribingVisitId
        this.saveAsDraftHidden = saveAsDraftHidden

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
        attachmentsJob?.cancel()
        attachmentsJob = combine(
            documentRepository.observeAllDocuments(familyId),
            _uiState.map { it.examId }.distinctUntilChanged(),
        ) { docs, currentExamId ->
            docs.filter { ExamAttachmentTag.matches(it.notes, currentExamId) }
        }
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
            loadedLinkedExpenseId = exam.linkedExpenseId
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
                costText = exam.cost?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString().replace('.', ',') }.orEmpty(),
                // L'id della spesa collegata non sta nello stato UI: non si mostra,
                // serve solo a ritrovare la voce al salvataggio.
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
    fun setCostText(v: String) { _uiState.value = _uiState.value.copy(costText = v) }
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
                cost = s.costText.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it > 0 },
                linkedExpenseId = loadedLinkedExpenseId,
                location = s.location.takeIf { it.isNotBlank() },
                statusRaw = effectiveStatus.rawValue,
                resultText = if (s.hasResult) s.resultText.takeIf { it.isNotBlank() } else null,
                resultDateEpochMillis = if (s.hasResult) s.resultDateEpochMillis else null,
                prescribingVisitId = boundPrescribingVisitId,
                reminderOn = s.reminderOn && deadline != null,
                isDeleted = saveAsDraftHidden,
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
                        // L'esame che costa qualcosa è anche una spesa di
                        // famiglia, categoria Salute.
                        val linkedId = expenseRepository.syncLinkedExpense(
                            familyId = familyId,
                            linkedExpenseId = savedExam.linkedExpenseId,
                            amount = savedExam.cost,
                            title = savedExam.name,
                            fallbackTitle = appContext.getString(R.string.health_exam_expense_fallback_title),
                            // La scadenza dell'esame se c'è: è la data in cui si paga.
                            dateEpochMillis = savedExam.deadlineEpochMillis ?: savedExam.createdAtEpochMillis,
                            notes = savedExam.location ?: savedExam.notes,
                            categorySlug = "salute",
                        )
                        if (linkedId != savedExam.linkedExpenseId) {
                            loadedLinkedExpenseId = linkedId
                            runCatching { repository.upsert(savedExam.copy(linkedExpenseId = linkedId)) }
                        }
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
