package it.vittorioscocca.kidbox.ui.screens.health.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.sync.MedicalVisitSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Allineato a iOS [PeriodFilter] (sheet periodo rapido). */
enum class VisitPeriodFilter(val displayLabel: String) {
    ALL("Tutto"),
    THREE_MONTHS("3 mesi"),
    SIX_MONTHS("6 mesi"),
    ONE_YEAR("1 anno"),
    CUSTOM("Personalizzato"),
}

data class MedicalVisitsState(
    val isLoading: Boolean = true,
    val childName: String = "",
    val booked: List<KBMedicalVisit> = emptyList(),
    val pending: List<KBMedicalVisit> = emptyList(),
    val resultAvailable: List<KBMedicalVisit> = emptyList(),
    val completed: List<KBMedicalVisit> = emptyList(),
    val hasAnyVisit: Boolean = false,
    val periodFilter: VisitPeriodFilter = VisitPeriodFilter.ALL,
    val customFilterStartEpoch: Long = defaultCustomStart(),
    val customFilterEndEpoch: Long = System.currentTimeMillis(),
    val searchQuery: String = "",
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val filteredVisitCount: Int = 0,
)

private fun defaultCustomStart(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, -1)
    return c.timeInMillis
}

@HiltViewModel
class MedicalVisitsViewModel @Inject constructor(
    private val repository: MedicalVisitRepository,
    private val syncCenter: MedicalVisitSyncCenter,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val aiSettings: AiSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVisitsState())
    val uiState: StateFlow<MedicalVisitsState> = _uiState.asStateFlow()

    val isAiGloballyEnabled: StateFlow<Boolean> get() = aiSettings.isEnabled

    private var familyId: String = ""
    private var childId: String = ""
    private var rawVisits: List<KBMedicalVisit> = emptyList()
    private var observeJob: Job? = null

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        observeJob?.cancel()
        this.familyId = familyId
        this.childId = childId
        rawVisits = emptyList()
        _uiState.value = MedicalVisitsState(isLoading = true)
        syncCenter.start(familyId)

        viewModelScope.launch {
            val name = resolveChildName(childId)
            _uiState.value = _uiState.value.copy(childName = name)
        }

        observeJob = repository.observe(familyId, childId)
            .onEach { visits ->
                rawVisits = visits
                rebuildBuckets()
            }
            .launchIn(viewModelScope)
    }

    fun setSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
        rebuildBuckets()
    }

    fun setPeriodFilter(filter: VisitPeriodFilter) {
        _uiState.value = _uiState.value.copy(periodFilter = filter)
        rebuildBuckets()
    }

    fun setCustomPeriodRange(startEpoch: Long, endEpoch: Long) {
        var s = startEpoch
        var e = endEpoch
        if (s > e) {
            val t = s
            s = e
            e = t
        }
        _uiState.value = _uiState.value.copy(
            customFilterStartEpoch = s,
            customFilterEndEpoch = e,
            periodFilter = VisitPeriodFilter.CUSTOM,
        )
        rebuildBuckets()
    }

    fun toggleSelecting() {
        val st = _uiState.value
        val next = !st.isSelecting
        _uiState.value = st.copy(
            isSelecting = next,
            selectedIds = if (next) st.selectedIds else emptySet(),
        )
    }

    fun toggleVisitSelected(id: String) {
        val st = _uiState.value
        val next = mutableSetOf<String>().apply { addAll(st.selectedIds) }
        if (next.contains(id)) next.remove(id) else next.add(id)
        _uiState.value = st.copy(selectedIds = next)
    }

    fun selectOrDeselectAllFiltered() {
        val st = _uiState.value
        val all = filteredIds(st)
        val allSelected = st.selectedIds.containsAll(all) && all.isNotEmpty()
        _uiState.value = st.copy(selectedIds = if (allSelected) emptySet() else all.toSet())
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.softDeleteById(it) }
            _uiState.value = _uiState.value.copy(selectedIds = emptySet(), isSelecting = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        if (familyId.isNotBlank()) syncCenter.stop(familyId)
    }

    private fun effectiveStatus(v: KBMedicalVisit): KBVisitStatus {
        val s = KBVisitStatus.fromRaw(v.visitStatusRaw)
        return if (s == KBVisitStatus.UNKNOWN_STATUS) KBVisitStatus.PENDING else s
    }

    private fun filteredIds(st: MedicalVisitsState): List<String> =
        rawVisits
            .filter { passesPeriodFilter(it, st) }
            .filter { passesSearch(it, st.searchQuery) }
            .map { it.id }

    private fun rebuildBuckets() {
        val st = _uiState.value
        val periodFiltered = rawVisits.filter { passesPeriodFilter(it, st) }
        val filtered = periodFiltered.filter { passesSearch(it, st.searchQuery) }

        fun bucket(list: List<KBMedicalVisit>, status: KBVisitStatus): List<KBMedicalVisit> =
            list.filter { effectiveStatus(it) == status }.sortedByDescending { it.dateEpochMillis }

        _uiState.value = st.copy(
            isLoading = false,
            hasAnyVisit = rawVisits.isNotEmpty(),
            filteredVisitCount = filtered.size,
            booked = bucket(filtered, KBVisitStatus.BOOKED).sortedBy { it.dateEpochMillis },
            pending = bucket(filtered, KBVisitStatus.PENDING).sortedBy { it.dateEpochMillis },
            resultAvailable = bucket(filtered, KBVisitStatus.RESULT_AVAILABLE),
            completed = bucket(filtered, KBVisitStatus.COMPLETED),
        )
    }

    private fun passesSearch(v: KBMedicalVisit, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val ql = q.lowercase()
        return v.reason.lowercase().contains(ql) ||
            (v.doctorName?.lowercase()?.contains(ql) == true) ||
            (v.diagnosis?.lowercase()?.contains(ql) == true) ||
            (v.recommendations?.lowercase()?.contains(ql) == true) ||
            (v.notes?.lowercase()?.contains(ql) == true)
    }

    private fun passesPeriodFilter(v: KBMedicalVisit, st: MedicalVisitsState): Boolean {
        val ref = v.dateEpochMillis
        when (st.periodFilter) {
            VisitPeriodFilter.ALL -> return true
            VisitPeriodFilter.CUSTOM -> {
                val start = startOfDay(st.customFilterStartEpoch)
                val end = endOfDayInclusive(st.customFilterEndEpoch)
                return ref in start..end
            }
            VisitPeriodFilter.THREE_MONTHS,
            VisitPeriodFilter.SIX_MONTHS,
            VisitPeriodFilter.ONE_YEAR,
            -> {
                val cutoff = cutoffMillis(st.periodFilter) ?: return true
                return ref >= cutoff
            }
        }
    }

    private fun startOfDay(epoch: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = epoch
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDayInclusive(epoch: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = epoch
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    private fun cutoffMillis(filter: VisitPeriodFilter): Long? {
        val c = Calendar.getInstance()
        return when (filter) {
            VisitPeriodFilter.ALL -> null
            VisitPeriodFilter.THREE_MONTHS -> {
                c.add(Calendar.MONTH, -3)
                c.timeInMillis
            }
            VisitPeriodFilter.SIX_MONTHS -> {
                c.add(Calendar.MONTH, -6)
                c.timeInMillis
            }
            VisitPeriodFilter.ONE_YEAR -> {
                c.add(Calendar.YEAR, -1)
                c.timeInMillis
            }
            VisitPeriodFilter.CUSTOM -> null
        }
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }
}
