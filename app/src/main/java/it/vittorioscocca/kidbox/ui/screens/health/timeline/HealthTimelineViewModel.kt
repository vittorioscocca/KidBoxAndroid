package it.vittorioscocca.kidbox.ui.screens.health.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.data.sync.MedicalExamSyncCenter
import it.vittorioscocca.kidbox.data.sync.MedicalVisitSyncCenter
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.data.sync.VaccineSyncCenter
import it.vittorioscocca.kidbox.domain.model.HealthTimelineEvent
import it.vittorioscocca.kidbox.domain.model.HealthTimelineEventKind
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HealthTimelineState(
    val isLoading: Boolean = true,
    val subjectName: String = "",
    val events: List<HealthTimelineEvent> = emptyList(),
    val eventsGroupedByYear: List<Pair<Int, List<HealthTimelineEvent>>> = emptyList(),
)

@HiltViewModel
class HealthTimelineViewModel @Inject constructor(
    private val visitRepository: MedicalVisitRepository,
    private val examRepository: MedicalExamRepository,
    private val treatmentRepository: TreatmentRepository,
    private val vaccineRepository: VaccineRepository,
    private val visitSyncCenter: MedicalVisitSyncCenter,
    private val examSyncCenter: MedicalExamSyncCenter,
    private val treatmentSyncCenter: TreatmentSyncCenter,
    private val vaccineSyncCenter: VaccineSyncCenter,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthTimelineState())
    val uiState: StateFlow<HealthTimelineState> = _uiState.asStateFlow()

    private var boundFamilyId = ""
    private var boundChildId = ""

    fun bind(familyId: String, childId: String) {
        if (boundFamilyId == familyId && boundChildId == childId) return
        boundFamilyId = familyId
        boundChildId = childId

        visitSyncCenter.start(familyId)
        examSyncCenter.start(familyId)
        treatmentSyncCenter.start(familyId)
        vaccineSyncCenter.start(familyId)

        viewModelScope.launch {
            val child = childDao.getById(childId)
            val name = if (child != null) {
                child.name
            } else {
                memberDao.observeActiveByFamilyId(familyId)
                    .first()
                    .firstOrNull { it.userId == childId }
                    ?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Profilo"
            }
            _uiState.value = _uiState.value.copy(subjectName = name)
        }

        combine(
            visitRepository.observe(familyId, childId),
            examRepository.observe(familyId, childId),
            treatmentRepository.observe(familyId, childId),
            vaccineRepository.observe(familyId, childId),
        ) { visits, exams, treatments, vaccines ->
            val visitEvents = visits
                .filter { !it.isDeleted }
                .map { visit ->
                    HealthTimelineEvent(
                        id = "visit-${visit.id}",
                        sourceId = visit.id,
                        dateEpochMillis = visit.dateEpochMillis,
                        kind = HealthTimelineEventKind.VISIT,
                        title = visit.reason.ifBlank { "Visita medica" },
                        subtitle = visit.doctorName,
                    )
                }

            val examEvents = exams
                .filter { !it.isDeleted }
                .map { exam ->
                    HealthTimelineEvent(
                        id = "exam-${exam.id}",
                        sourceId = exam.id,
                        dateEpochMillis = exam.deadlineEpochMillis ?: exam.createdAtEpochMillis,
                        kind = HealthTimelineEventKind.EXAM,
                        title = exam.name,
                        subtitle = exam.statusRaw.takeIf { it.isNotBlank() },
                    )
                }

            val treatmentEvents = treatments
                .filter { !it.isDeleted }
                .map { treatment ->
                    val subtitle = when {
                        treatment.isLongTerm -> "Lungo termine"
                        treatment.durationDays > 0 -> "${treatment.durationDays} giorni"
                        else -> null
                    }
                    HealthTimelineEvent(
                        id = "treatment-${treatment.id}",
                        sourceId = treatment.id,
                        dateEpochMillis = treatment.startDateEpochMillis,
                        kind = HealthTimelineEventKind.TREATMENT,
                        title = treatment.drugName,
                        subtitle = subtitle,
                    )
                }

            val vaccineEvents = vaccines
                .filter { !it.isDeleted }
                .map { vaccine ->
                    val dateMs = vaccine.administeredDateEpochMillis
                        ?: vaccine.scheduledDateEpochMillis
                        ?: vaccine.createdAtEpochMillis
                    HealthTimelineEvent(
                        id = "vaccine-${vaccine.id}",
                        sourceId = vaccine.id,
                        dateEpochMillis = dateMs,
                        kind = HealthTimelineEventKind.VACCINE,
                        title = vaccine.name,
                        subtitle = vaccine.computedStatus().rawValue,
                    )
                }

            val combined = (visitEvents + examEvents + treatmentEvents + vaccineEvents)
                .sortedByDescending { it.dateEpochMillis }

            val grouped = combined
                .groupBy { event ->
                    Calendar.getInstance().apply { timeInMillis = event.dateEpochMillis }
                        .get(Calendar.YEAR)
                }
                .entries
                .sortedByDescending { it.key }
                .map { (year, events) -> year to events }

            combined to grouped
        }
            .onEach { (events, grouped) ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    events = events,
                    eventsGroupedByYear = grouped,
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        if (boundFamilyId.isNotBlank()) {
            visitSyncCenter.stop(boundFamilyId)
            examSyncCenter.stop(boundFamilyId)
            treatmentSyncCenter.stop(boundFamilyId)
            vaccineSyncCenter.stop(boundFamilyId)
        }
    }
}
