package it.vittorioscocca.kidbox.ui.screens.health.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.sync.MedicalExamSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Allineato a iOS [ExamTimeFilter]. */
enum class ExamTimeFilter(val displayLabel: String) {
    ALL("Tutti"),
    MONTHS_3("Ultimi 3 mesi"),
    MONTHS_6("Ultimi 6 mesi"),
    YEAR_1("Ultimo anno"),
    CUSTOM("Personalizzato"),
}

data class MedicalExamsState(
    val isLoading: Boolean = true,
    val childName: String = "",
    val pending: List<KBMedicalExam> = emptyList(),
    val booked: List<KBMedicalExam> = emptyList(),
    /** Eseguiti + Risultato disponibile (come iOS [PediatricExamsView] sezione "Eseguiti"). */
    val executed: List<KBMedicalExam> = emptyList(),
    val unknownStatus: List<KBMedicalExam> = emptyList(),
    val hasAnyExam: Boolean = false,
    val timeFilter: ExamTimeFilter = ExamTimeFilter.ALL,
    val customFilterStartEpoch: Long = defaultCustomStart(),
    val customFilterEndEpoch: Long = System.currentTimeMillis(),
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /** Esami dopo filtro periodo (per “Tutte” / stato vuoto filtro). */
    val filteredExamCount: Int = 0,
)

private fun defaultCustomStart(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, -1)
    return c.timeInMillis
}

@HiltViewModel
class MedicalExamsViewModel @Inject constructor(
    private val repository: MedicalExamRepository,
    private val syncCenter: MedicalExamSyncCenter,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val aiSettings: AiSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalExamsState())
    val uiState: StateFlow<MedicalExamsState> = _uiState.asStateFlow()

    val isAiGloballyEnabled: StateFlow<Boolean> get() = aiSettings.isEnabled

    private var familyId: String = ""
    private var childId: String = ""
    private var rawExams: List<KBMedicalExam> = emptyList()
    private var observeJob: Job? = null

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        observeJob?.cancel()
        this.familyId = familyId
        this.childId = childId
        rawExams = emptyList()
        _uiState.value = MedicalExamsState(isLoading = true)
        syncCenter.start(familyId)
        viewModelScope.launch {
            val name = resolveChildName(childId)
            _uiState.value = _uiState.value.copy(childName = name)
        }
        val knownRaws = KBExamStatus.entries.map { it.rawValue }.toSet()
        observeJob = repository.observe(familyId, childId)
            .onEach { exams ->
                rawExams = exams
                rebuildBuckets(knownRaws)
            }
            .launchIn(viewModelScope)
    }

    fun setTimeFilter(filter: ExamTimeFilter) {
        _uiState.value = _uiState.value.copy(
            timeFilter = filter,
            childName = _uiState.value.childName,
        )
        rebuildBuckets(KBExamStatus.entries.map { it.rawValue }.toSet())
    }

    fun setCustomFilterRange(startEpoch: Long, endEpoch: Long) {
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
            timeFilter = ExamTimeFilter.CUSTOM,
            childName = _uiState.value.childName,
        )
        rebuildBuckets(KBExamStatus.entries.map { it.rawValue }.toSet())
    }

    fun toggleSelecting() {
        val st = _uiState.value
        val next = !st.isSelecting
        _uiState.value = st.copy(
            isSelecting = next,
            selectedIds = if (next) st.selectedIds else emptySet(),
            childName = st.childName,
        )
    }

    fun toggleExamSelected(id: String) {
        val st = _uiState.value
        val next = mutableSetOf<String>().apply { addAll(st.selectedIds) }
        if (next.contains(id)) {
            next.remove(id)
        } else {
            next.add(id)
        }
        _uiState.value = st.copy(selectedIds = next, childName = st.childName)
    }

    fun selectOrDeselectAllFiltered() {
        val st = _uiState.value
        val all = filteredIds(st)
        val allSelected = st.selectedIds.containsAll(all) && all.isNotEmpty()
        _uiState.value = st.copy(
            selectedIds = if (allSelected) emptySet() else all.toSet(),
            childName = st.childName,
        )
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        val frozenChildName = _uiState.value.childName
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.softDeleteById(it) }
            _uiState.value = _uiState.value.copy(
                selectedIds = emptySet(),
                isSelecting = false,
                childName = frozenChildName,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        if (familyId.isNotBlank()) syncCenter.stop(familyId)
    }

    private fun filteredIds(st: MedicalExamsState): List<String> =
        rawExams.filter { passesTimeFilter(it, st) }.map { it.id }

    private fun rebuildBuckets(knownRaws: Set<String>) {
        val st = _uiState.value
        val filtered = rawExams.filter { passesTimeFilter(it, st) }
        _uiState.value = st.copy(
            isLoading = false,
            hasAnyExam = rawExams.isNotEmpty(),
            filteredExamCount = filtered.size,
            childName = st.childName,
            pending = filtered
                .filter { it.statusRaw == KBExamStatus.PENDING.rawValue }
                .sortedBy { it.deadlineEpochMillis ?: Long.MAX_VALUE },
            booked = filtered
                .filter { it.statusRaw == KBExamStatus.BOOKED.rawValue }
                .sortedBy { it.deadlineEpochMillis ?: Long.MAX_VALUE },
            executed = filtered
                .filter {
                    it.statusRaw == KBExamStatus.DONE.rawValue ||
                        it.statusRaw == KBExamStatus.RESULT_IN.rawValue
                }
                .sortedByDescending { it.deadlineEpochMillis ?: it.createdAtEpochMillis },
            unknownStatus = filtered
                .filter { it.statusRaw !in knownRaws }
                .sortedByDescending { it.deadlineEpochMillis ?: it.createdAtEpochMillis },
        )
    }

    private fun passesTimeFilter(e: KBMedicalExam, st: MedicalExamsState): Boolean {
        when (st.timeFilter) {
            ExamTimeFilter.ALL -> return true
            ExamTimeFilter.CUSTOM -> {
                val ref = e.deadlineEpochMillis ?: e.createdAtEpochMillis
                val start = st.customFilterStartEpoch
                val endDayStart = endOfDayExclusive(st.customFilterEndEpoch)
                return ref >= startOfDay(start) && ref < endDayStart
            }
            ExamTimeFilter.MONTHS_3,
            ExamTimeFilter.MONTHS_6,
            ExamTimeFilter.YEAR_1 -> {
                val cutoff = cutoffMillis(st.timeFilter) ?: return true
                val ref = e.deadlineEpochMillis ?: e.createdAtEpochMillis
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

    /** Inizio del giorno successivo a [epoch] (limite esclusivo, come iOS). */
    private fun endOfDayExclusive(epoch: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = epoch
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    private fun cutoffMillis(filter: ExamTimeFilter): Long? {
        val c = Calendar.getInstance()
        return when (filter) {
            ExamTimeFilter.ALL -> null
            ExamTimeFilter.MONTHS_3 -> {
                c.add(Calendar.MONTH, -3)
                c.timeInMillis
            }
            ExamTimeFilter.MONTHS_6 -> {
                c.add(Calendar.MONTH, -6)
                c.timeInMillis
            }
            ExamTimeFilter.YEAR_1 -> {
                c.add(Calendar.YEAR, -1)
                c.timeInMillis
            }
            ExamTimeFilter.CUSTOM -> null
        }
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }
}
