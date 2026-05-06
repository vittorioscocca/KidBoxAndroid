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
import java.util.Locale
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
    val selectedYear: Int? = null,
    val searchQuery: String = "",
    val activeKinds: Set<HealthTimelineEventKind> = emptySet(),
    val availableYears: List<Int> = emptyList(),
    val filteredCount: Int = 0,
    val eventsGroupedByYearMonth: List<YearTimelineGroup> = emptyList(),
)

data class YearTimelineGroup(
    val year: Int,
    val months: List<MonthTimelineGroup>,
)

data class MonthTimelineGroup(
    val month: Int,
    val events: List<HealthTimelineEvent>,
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
    private var allEvents: List<HealthTimelineEvent> = emptyList()

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

            (visitEvents + examEvents + treatmentEvents + vaccineEvents)
                .sortedByDescending { it.dateEpochMillis }
        }
            .onEach { events ->
                allEvents = events
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    events = events,
                    availableYears = extractAvailableYears(events),
                )
                recomputeFilteredState()
            }
            .launchIn(viewModelScope)
    }

    fun toggleKind(kind: HealthTimelineEventKind) {
        val current = _uiState.value.activeKinds.toMutableSet()
        if (current.contains(kind)) current.remove(kind) else current.add(kind)
        _uiState.value = _uiState.value.copy(activeKinds = current)
        recomputeFilteredState()
    }

    fun setSelectedYear(year: Int?) {
        _uiState.value = _uiState.value.copy(selectedYear = year)
        recomputeFilteredState()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        recomputeFilteredState()
    }

    private fun recomputeFilteredState() {
        val s = _uiState.value
        val q = s.searchQuery.trim().lowercase(Locale.ITALIAN)
        val filtered = allEvents.filter { event ->
            val kindOk = s.activeKinds.isEmpty() || s.activeKinds.contains(event.kind)
            val yearOk = s.selectedYear == null || yearOf(event.dateEpochMillis) == s.selectedYear
            val queryOk = q.isBlank() ||
                event.title.lowercase(Locale.ITALIAN).contains(q) ||
                event.subtitle.orEmpty().lowercase(Locale.ITALIAN).contains(q) ||
                event.kind.rawLabel.lowercase(Locale.ITALIAN).contains(q)
            kindOk && yearOk && queryOk
        }
        _uiState.value = s.copy(
            filteredCount = filtered.size,
            eventsGroupedByYearMonth = groupByYearMonth(filtered),
        )
    }

    private fun extractAvailableYears(events: List<HealthTimelineEvent>): List<Int> =
        events.map { yearOf(it.dateEpochMillis) }.distinct().sortedDescending()

    private fun groupByYearMonth(events: List<HealthTimelineEvent>): List<YearTimelineGroup> {
        val byYear = events.groupBy { yearOf(it.dateEpochMillis) }
        return byYear.keys.sortedDescending().map { year ->
            val yearEvents = byYear[year].orEmpty()
            val byMonth = yearEvents.groupBy { monthOf(it.dateEpochMillis) }
            YearTimelineGroup(
                year = year,
                months = byMonth.keys.sortedDescending().map { month ->
                    MonthTimelineGroup(
                        month = month,
                        events = byMonth[month].orEmpty().sortedByDescending { it.dateEpochMillis },
                    )
                },
            )
        }
    }

    private fun yearOf(epochMillis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMillis }.get(Calendar.YEAR)

    private fun monthOf(epochMillis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMillis }.get(Calendar.MONTH) + 1

    override fun onCleared() {
        super.onCleared()
        if (boundFamilyId.isNotBlank()) {
            visitSyncCenter.stop(boundFamilyId)
            examSyncCenter.stop(boundFamilyId)
            vaccineSyncCenter.stop(boundFamilyId)
        }
    }
}
