package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.TreatmentAttachmentTag
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.DoseLogRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.domain.model.KBDoseLog
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.slotLabelFor
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class DoseState { PENDING, TAKEN, SKIPPED }

data class DoseSlot(
    val dayNumber: Int,
    val slotIndex: Int,
    val scheduledTime: String,
    val label: String,
    val state: DoseState,
    val logId: String?,
)

data class DayEntry(
    val dayNumber: Int,
    val dateMillis: Long,
    val slots: List<DoseSlot>,
)

data class MedicalTreatmentDetailState(
    val isLoading: Boolean = true,
    val treatment: KBTreatment? = null,
    val childName: String = "",
    val todaySlots: List<DoseSlot> = emptyList(),
    val calendarDays: List<DayEntry> = emptyList(),
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val showCalendar: Boolean = false,
    val showExtendSheet: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val attachments: List<KBDocumentEntity> = emptyList(),
    val isUploading: Boolean = false,
    val openFileEvent: Pair<String, File>? = null,
    val uploadError: String? = null,
)

@HiltViewModel
class MedicalTreatmentDetailViewModel @Inject constructor(
    private val repository: TreatmentRepository,
    private val doseLogRepository: DoseLogRepository,
    private val notifManager: TreatmentNotificationManager,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val attachmentService: HealthAttachmentService,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalTreatmentDetailState())
    val uiState: StateFlow<MedicalTreatmentDetailState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""
    private var treatmentId = ""
    private var childName = ""

    fun bind(familyId: String, childId: String, treatmentId: String) {
        if (this.treatmentId == treatmentId) return
        this.familyId = familyId
        this.childId = childId
        this.treatmentId = treatmentId

        viewModelScope.launch {
            childName = resolveChildName(childId)
            _uiState.value = _uiState.value.copy(childName = childName)
        }

        documentRepository.startRealtime(familyId)

        documentRepository.observeAllDocuments(familyId)
            .map { docs -> docs.filter { TreatmentAttachmentTag.matches(it.notes, treatmentId) } }
            .onEach { _uiState.value = _uiState.value.copy(attachments = it) }
            .launchIn(viewModelScope)

        combine(
            repository.observe(familyId, childId),
            doseLogRepository.observeByTreatment(treatmentId),
        ) { treatments, logs ->
            val treatment = treatments.firstOrNull { it.id == treatmentId }
            Pair(treatment, logs)
        }
            .onEach { (treatment, logs) ->
                if (treatment == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@onEach
                }
                val todaySlots = buildTodaySlots(treatment, logs)
                val calendarDays = buildCalendarDays(treatment, logs)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    treatment = treatment,
                    todaySlots = todaySlots,
                    calendarDays = calendarDays,
                    isActive = treatment.isActive,
                    isDeleted = treatment.isDeleted,
                )
            }
            .launchIn(viewModelScope)
    }

    fun markTaken(dayNumber: Int, slotIndex: Int, scheduledTime: String) {
        val t = _uiState.value.treatment ?: return
        viewModelScope.launch {
            doseLogRepository.markTaken(t.id, familyId, childId, dayNumber, slotIndex, scheduledTime)
            notifManager.cancelSlot(t.id, dayNumber, slotIndex)
        }
    }

    fun markSkipped(dayNumber: Int, slotIndex: Int, scheduledTime: String) {
        val t = _uiState.value.treatment ?: return
        viewModelScope.launch {
            doseLogRepository.markSkipped(t.id, familyId, childId, dayNumber, slotIndex, scheduledTime)
            notifManager.cancelSlot(t.id, dayNumber, slotIndex)
        }
    }

    fun clearLog(logId: String) {
        viewModelScope.launch {
            doseLogRepository.clearLog(logId)
        }
    }

    fun setActive(active: Boolean) {
        val t = _uiState.value.treatment ?: return
        viewModelScope.launch {
            val updated = t.copy(isActive = active)
            repository.upsert(updated)
            if (active && updated.reminderEnabled) {
                notifManager.schedule(updated, childName)
            } else {
                notifManager.cancel(updated.id)
            }
        }
    }

    fun extend(days: Int) {
        val t = _uiState.value.treatment ?: return
        viewModelScope.launch {
            val updated = t.copy(
                durationDays = t.durationDays + days,
                endDateEpochMillis = t.endDateEpochMillis?.let { it + TimeUnit.DAYS.toMillis(days.toLong()) },
            )
            repository.upsert(updated)
            if (updated.reminderEnabled && updated.isActive) {
                notifManager.schedule(updated, childName)
            }
            _uiState.value = _uiState.value.copy(showExtendSheet = false)
        }
    }

    fun delete() {
        val t = _uiState.value.treatment ?: return
        viewModelScope.launch {
            notifManager.cancel(t.id)
            repository.softDelete(t)
            _uiState.value = _uiState.value.copy(isDeleted = true, showDeleteDialog = false)
        }
    }

    fun toggleCalendar() {
        _uiState.value = _uiState.value.copy(showCalendar = !_uiState.value.showCalendar)
    }

    fun showExtendSheet() { _uiState.value = _uiState.value.copy(showExtendSheet = true) }
    fun dismissExtendSheet() { _uiState.value = _uiState.value.copy(showExtendSheet = false) }
    fun showDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = true) }
    fun dismissDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = false) }

    fun uploadAttachment(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploading = true, uploadError = null)
        viewModelScope.launch {
            attachmentService.uploadTreatmentAttachment(uri, treatmentId, familyId, childId)
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

    private fun buildTodaySlots(treatment: KBTreatment, logs: List<KBDoseLog>): List<DoseSlot> {
        val now = System.currentTimeMillis()
        val dayNumber = TimeUnit.MILLISECONDS.toDays(now - treatment.startDateEpochMillis).toInt()
        if (dayNumber < 0) return emptyList()
        if (!treatment.isLongTerm && dayNumber >= treatment.durationDays) return emptyList()
        val times = treatment.scheduleTimesList()
        return times.mapIndexed { idx, time ->
            val log = logs.firstOrNull { it.dayNumber == dayNumber && it.slotIndex == idx }
            DoseSlot(
                dayNumber = dayNumber,
                slotIndex = idx,
                scheduledTime = time,
                label = slotLabelFor(idx),
                state = when {
                    log == null -> DoseState.PENDING
                    log.taken -> DoseState.TAKEN
                    log.takenAtEpochMillis != null -> DoseState.SKIPPED
                    else -> DoseState.PENDING
                },
                logId = log?.id,
            )
        }
    }

    private fun buildCalendarDays(treatment: KBTreatment, logs: List<KBDoseLog>): List<DayEntry> {
        val now = System.currentTimeMillis()
        val startMillis = treatment.startDateEpochMillis
        val totalDays = if (treatment.isLongTerm) {
            TimeUnit.MILLISECONDS.toDays(now - startMillis).toInt().coerceAtLeast(0) + 1
        } else {
            treatment.durationDays
        }
        val times = treatment.scheduleTimesList()
        return (0 until totalDays.coerceAtMost(365)).map { dayNum ->
            val dayMillis = startMillis + TimeUnit.DAYS.toMillis(dayNum.toLong())
            val slots = times.mapIndexed { idx, time ->
                val log = logs.firstOrNull { it.dayNumber == dayNum && it.slotIndex == idx }
                DoseSlot(
                    dayNumber = dayNum,
                    slotIndex = idx,
                    scheduledTime = time,
                    label = slotLabelFor(idx),
                    state = when {
                        log == null -> DoseState.PENDING
                        log.taken -> DoseState.TAKEN
                        log.takenAtEpochMillis != null -> DoseState.SKIPPED
                        else -> DoseState.PENDING
                    },
                    logId = log?.id,
                )
            }
            DayEntry(dayNumber = dayNum, dateMillis = dayMillis, slots = slots)
        }
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }
}
