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
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBDoseLog
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.TreatmentSchedulePeriod
import it.vittorioscocca.kidbox.domain.model.schedulePeriodForTime
import it.vittorioscocca.kidbox.domain.model.schedulePeriodLabel
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import java.io.File
import java.time.Instant
import java.time.ZoneId
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

/** [dayNumber]: giorno terapia 1-based (come iOS), non il giorno del calendario in cui si è presa la dose. */
data class DoseSlot(
    val dayNumber: Int,
    val slotIndex: Int,
    val scheduledTime: String,
    /** Etichetta fascia da orario programmato (Mattina / Pranzo / …). */
    val label: String,
    /** Fascia da [scheduledTime] (ordinamento e chip “programmato”); `null` se non deducibile (es. dose 5+ senza orario valido). */
    val schedulePeriod: TreatmentSchedulePeriod?,
    /** Fascia mostrata: se assunta, da [takenAtEpochMillis], altrimenti uguale a [schedulePeriod]. */
    val displayPeriod: TreatmentSchedulePeriod?,
    val state: DoseState,
    val logId: String?,
    /** Istante in cui è stata registrata l’assunzione (solo se [state] == TAKEN). */
    val takenAtEpochMillis: Long? = null,
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
    private val treatmentSyncCenter: TreatmentSyncCenter,
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

        if (familyId.isNotBlank()) {
            treatmentSyncCenter.start(familyId)
        }

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

    /** True se il giorno terapeutico [dayNumber] cade in un giorno di calendario dopo oggi (timezone dispositivo). */
    private fun isTherapeuticDayCalendarFuture(dayNumber: Int): Boolean {
        val t = _uiState.value.treatment ?: return false
        val dayMillis = t.startDateEpochMillis + TimeUnit.DAYS.toMillis((dayNumber - 1).toLong())
        val zone = ZoneId.systemDefault()
        val dayDate = Instant.ofEpochMilli(dayMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        return dayDate.isAfter(today)
    }

    fun markTaken(dayNumber: Int, slotIndex: Int, scheduledTime: String, takenAtEpochMillis: Long = System.currentTimeMillis()) {
        val t = _uiState.value.treatment ?: return
        if (isTherapeuticDayCalendarFuture(dayNumber)) return
        viewModelScope.launch {
            doseLogRepository.markTaken(
                t.id,
                familyId,
                childId,
                dayNumber,
                slotIndex,
                scheduledTime,
                takenAtEpochMillis = takenAtEpochMillis,
            )
            notifManager.cancelSlot(t.id, dayNumber - 1, slotIndex)
        }
    }

    fun markSkipped(dayNumber: Int, slotIndex: Int, scheduledTime: String) {
        val t = _uiState.value.treatment ?: return
        if (isTherapeuticDayCalendarFuture(dayNumber)) return
        viewModelScope.launch {
            doseLogRepository.markSkipped(t.id, familyId, childId, dayNumber, slotIndex, scheduledTime)
            notifManager.cancelSlot(t.id, dayNumber - 1, slotIndex)
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

    fun setReminderEnabled(enabled: Boolean) {
        val t = _uiState.value.treatment ?: return
        viewModelScope.launch {
            val updated = t.copy(reminderEnabled = enabled)
            repository.upsert(updated)
            if (enabled && updated.isActive) {
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

    /** Compat: versioni precedenti salvavano il primo giorno come `dayNumber == 0`. */
    private fun doseLogForSlot(logs: List<KBDoseLog>, therapeuticDay: Int, slotIndex: Int): KBDoseLog? =
        logs.firstOrNull { it.dayNumber == therapeuticDay && it.slotIndex == slotIndex }
            ?: if (therapeuticDay == 1) logs.firstOrNull { it.dayNumber == 0 && it.slotIndex == slotIndex } else null

    private fun sortedSlotIndices(times: List<String>): List<Int> =
        times.indices.sortedWith(
            compareBy<Int>(
                { idx -> schedulePeriodForTime(times[idx], idx)?.ordinal ?: Int.MAX_VALUE },
                { idx -> sortMinutesWithinDay(times[idx], idx) },
                { idx -> idx },
            ),
        )

    /** Minuti per ordinamento: nella Notte, dopo mezzanotte viene dopo la sera (stesso giorno terapia). */
    private fun sortMinutesWithinDay(time: String, slotIndex: Int): Int {
        val mins = TreatmentSchedulePeriod.parseScheduleTimeToMinutesOfDay(time) ?: return Int.MAX_VALUE
        val p = schedulePeriodForTime(time, slotIndex)
            ?: TreatmentSchedulePeriod.fromScheduleTimeString(time)
            ?: TreatmentSchedulePeriod.NOTTE
        return if (p == TreatmentSchedulePeriod.NOTTE && mins < 6 * 60) mins + 24 * 60 else mins
    }

    private fun makeDoseSlot(dayNumber: Int, slotIndex: Int, time: String, log: KBDoseLog?): DoseSlot {
        val schedPeriod = schedulePeriodForTime(time, slotIndex)
        val label = schedulePeriodLabel(time, slotIndex)
        val dispPeriod = when {
            log?.taken == true && log.takenAtEpochMillis != null ->
                TreatmentSchedulePeriod.fromEpochMillis(log.takenAtEpochMillis)
            else -> schedPeriod
        }
        return DoseSlot(
            dayNumber = dayNumber,
            slotIndex = slotIndex,
            scheduledTime = time,
            label = label,
            schedulePeriod = schedPeriod,
            displayPeriod = dispPeriod,
            state = when {
                log == null -> DoseState.PENDING
                log.taken -> DoseState.TAKEN
                else -> DoseState.SKIPPED
            },
            logId = log?.id,
            takenAtEpochMillis = log?.takeIf { it.taken }?.takenAtEpochMillis,
        )
    }

    private fun buildTodaySlots(treatment: KBTreatment, logs: List<KBDoseLog>): List<DoseSlot> {
        val now = System.currentTimeMillis()
        val start = treatment.startDateEpochMillis
        val daysSinceStart = TimeUnit.MILLISECONDS.toDays(now - start).toInt()
        if (daysSinceStart < 0) return emptyList()
        val dayNumber = daysSinceStart + 1
        if (!treatment.isLongTerm && dayNumber > treatment.durationDays) return emptyList()
        val times = treatment.scheduleTimesList()
        return sortedSlotIndices(times).map { idx ->
            makeDoseSlot(dayNumber, idx, times[idx], doseLogForSlot(logs, dayNumber, idx))
        }
    }

    private fun buildCalendarDays(treatment: KBTreatment, logs: List<KBDoseLog>): List<DayEntry> {
        val now = System.currentTimeMillis()
        val startMillis = treatment.startDateEpochMillis
        val totalDays = if (treatment.isLongTerm) {
            val daysSince = TimeUnit.MILLISECONDS.toDays(now - startMillis).toInt().coerceAtLeast(0)
            maxOf(daysSince + 7, 7)
        } else {
            treatment.durationDays
        }
        val times = treatment.scheduleTimesList()
        val capped = totalDays.coerceAtMost(365)
        val order = sortedSlotIndices(times)
        return (1..capped).map { dayNum ->
            val dayMillis = startMillis + TimeUnit.DAYS.toMillis((dayNum - 1).toLong())
            val slots = order.map { idx ->
                val time = times[idx]
                makeDoseSlot(dayNum, idx, time, doseLogForSlot(logs, dayNum, idx))
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
