package it.vittorioscocca.kidbox.ui.screens.health.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.mapper.KBDoctorSpecialization
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.notifications.VisitReminderScheduler
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicalVisitFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val childName: String = "",
    val visitId: String = UUID.randomUUID().toString(),
    val reason: String = "",
    val doctorName: String = "",
    val specialization: KBDoctorSpecialization? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val visitStatus: KBVisitStatus = KBVisitStatus.PENDING,
    val reminderOn: Boolean = false,
    val diagnosis: String = "",
    val recommendations: String = "",
    val notes: String = "",
    val hasNextVisit: Boolean = false,
    val nextVisitDateMillis: Long = System.currentTimeMillis(),
    val nextVisitReason: String = "",
    val nextVisitReminderOn: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val canSave: Boolean get() = reason.isNotBlank()
}

@HiltViewModel
class MedicalVisitFormViewModel @Inject constructor(
    private val repository: MedicalVisitRepository,
    private val reminderScheduler: VisitReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVisitFormState())
    val uiState: StateFlow<MedicalVisitFormState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""

    fun bind(familyId: String, childId: String, visitId: String?) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId

        viewModelScope.launch {
            val name = resolveChildName(childId)
            _uiState.value = _uiState.value.copy(childName = name)
        }

        if (visitId != null) loadVisit(visitId)
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    private fun loadVisit(visitId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val visit = repository.loadOnce(visitId)
            if (visit != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    visitId = visit.id,
                    reason = visit.reason,
                    doctorName = visit.doctorName.orEmpty(),
                    specialization = KBDoctorSpecialization.fromRaw(visit.doctorSpecializationRaw),
                    dateMillis = visit.dateEpochMillis,
                    visitStatus = KBVisitStatus.fromRaw(visit.visitStatusRaw),
                    reminderOn = visit.reminderOn,
                    diagnosis = visit.diagnosis.orEmpty(),
                    recommendations = visit.recommendations.orEmpty(),
                    notes = visit.notes.orEmpty(),
                    hasNextVisit = visit.nextVisitDateEpochMillis != null,
                    nextVisitDateMillis = visit.nextVisitDateEpochMillis ?: System.currentTimeMillis(),
                    nextVisitReason = visit.nextVisitReason.orEmpty(),
                    nextVisitReminderOn = visit.nextVisitReminderOn,
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setReason(v: String) { _uiState.value = _uiState.value.copy(reason = v) }
    fun setDoctorName(v: String) { _uiState.value = _uiState.value.copy(doctorName = v) }
    fun setSpecialization(v: KBDoctorSpecialization?) { _uiState.value = _uiState.value.copy(specialization = v) }
    fun setDateMillis(v: Long) { _uiState.value = _uiState.value.copy(dateMillis = v) }
    fun setVisitStatus(v: KBVisitStatus) { _uiState.value = _uiState.value.copy(visitStatus = v) }
    fun setReminderOn(v: Boolean) { _uiState.value = _uiState.value.copy(reminderOn = v) }
    fun setDiagnosis(v: String) { _uiState.value = _uiState.value.copy(diagnosis = v) }
    fun setRecommendations(v: String) { _uiState.value = _uiState.value.copy(recommendations = v) }
    fun setNotes(v: String) { _uiState.value = _uiState.value.copy(notes = v) }
    fun setHasNextVisit(v: Boolean) { _uiState.value = _uiState.value.copy(hasNextVisit = v) }
    fun setNextVisitDateMillis(v: Long) { _uiState.value = _uiState.value.copy(nextVisitDateMillis = v) }
    fun setNextVisitReason(v: String) { _uiState.value = _uiState.value.copy(nextVisitReason = v) }
    fun setNextVisitReminderOn(v: Boolean) { _uiState.value = _uiState.value.copy(nextVisitReminderOn = v) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextDate = if (s.hasNextVisit) s.nextVisitDateMillis else null
            val nextReason = if (s.hasNextVisit) s.nextVisitReason.takeIf { it.isNotBlank() } else null
            val nextReminderOn = s.hasNextVisit && s.nextVisitReminderOn
            val visit = KBMedicalVisit(
                id = s.visitId,
                familyId = familyId,
                childId = childId,
                dateEpochMillis = s.dateMillis,
                doctorName = s.doctorName.takeIf { it.isNotBlank() },
                doctorSpecializationRaw = s.specialization?.rawValue,
                travelDetailsJson = null,
                reason = s.reason.trim(),
                diagnosis = s.diagnosis.takeIf { it.isNotBlank() },
                recommendations = s.recommendations.takeIf { it.isNotBlank() },
                linkedTreatmentIdsJson = "[]",
                linkedExamIdsJson = "[]",
                asNeededDrugsJson = null,
                therapyTypesJson = "[]",
                prescribedExamsJson = null,
                photoUrlsJson = "[]",
                notes = s.notes.takeIf { it.isNotBlank() },
                nextVisitDateEpochMillis = nextDate,
                nextVisitReason = nextReason,
                visitStatusRaw = s.visitStatus.rawValue,
                reminderOn = s.reminderOn,
                nextVisitReminderOn = nextReminderOn,
                isDeleted = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = null,
                createdBy = null,
                syncStateRaw = 0,
                lastSyncError = null,
            )
            runCatching { repository.save(visit) }
                .fold(
                    onSuccess = {
                        scheduleReminders(visit)
                        _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
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

    private fun scheduleReminders(visit: KBMedicalVisit) {
        val visitTitle = buildString {
            append(visit.reason)
            visit.doctorName?.takeIf { it.isNotBlank() }?.let { append(" – $it") }
        }
        if (visit.reminderOn) {
            reminderScheduler.schedule(
                reminderKey = "${visit.id}_reminder",
                visitDateMillis = visit.dateEpochMillis,
                title = visitTitle,
                visitId = visit.id,
                familyId = visit.familyId,
                childId = visit.childId,
            )
        } else {
            reminderScheduler.cancel("${visit.id}_reminder", visit.id)
        }
        val nextDate = visit.nextVisitDateEpochMillis
        if (visit.nextVisitReminderOn && nextDate != null) {
            reminderScheduler.schedule(
                reminderKey = "${visit.id}_next_reminder",
                visitDateMillis = nextDate,
                title = "Prossima visita: ${visit.nextVisitReason ?: visit.reason}",
                visitId = visit.id,
                familyId = visit.familyId,
                childId = visit.childId,
            )
        } else {
            reminderScheduler.cancel("${visit.id}_next_reminder", visit.id)
        }
    }
}
