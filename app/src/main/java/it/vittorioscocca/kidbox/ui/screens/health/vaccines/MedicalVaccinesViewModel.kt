package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.data.sync.VaccineSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.notifications.VaccineReminderScheduler
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Allineato a iOS `VaccineTimeFilter`. */
enum class VaccineListTimeFilter {
    ALL,
    MONTHS3,
    MONTHS6,
    YEAR1,
    CUSTOM,
}

data class MedicalVaccinesState(
    val isLoading: Boolean = true,
    val timeFilter: VaccineListTimeFilter = VaccineListTimeFilter.ALL,
    /** Tutti i vaccini del bambino (prima del filtro periodo); per UI empty-filter vs libretto vuoto. */
    val unfilteredCount: Int = 0,
    val overdue: List<KBVaccine> = emptyList(),
    val scheduled: List<KBVaccine> = emptyList(),
    val administered: List<KBVaccine> = emptyList(),
    val planned: List<KBVaccine> = emptyList(),
    val skipped: List<KBVaccine> = emptyList(),
)

@HiltViewModel
class MedicalVaccinesViewModel @Inject constructor(
    private val repository: VaccineRepository,
    private val syncCenter: VaccineSyncCenter,
    private val reminderScheduler: VaccineReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVaccinesState())
    val uiState: StateFlow<MedicalVaccinesState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var latestVaccines: List<KBVaccine> = emptyList()
    private var timeFilter: VaccineListTimeFilter = VaccineListTimeFilter.ALL
    private var customFilterStartMs: Long = 0L
    private var customFilterEndMs: Long = 0L

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        val cal = Calendar.getInstance()
        customFilterEndMs = cal.timeInMillis
        cal.add(Calendar.MONTH, -1)
        customFilterStartMs = cal.timeInMillis

        syncCenter.start(familyId)
        repository.observe(familyId, childId)
            .onEach { vaccines ->
                latestVaccines = vaccines
                emitPartitioned()
            }
            .launchIn(viewModelScope)
    }

    fun setTimeFilter(filter: VaccineListTimeFilter) {
        timeFilter = filter
        emitPartitioned()
    }

    fun setCustomFilterRange(startMs: Long, endMs: Long) {
        customFilterStartMs = startMs
        customFilterEndMs = endMs
        timeFilter = VaccineListTimeFilter.CUSTOM
        emitPartitioned()
    }

    fun deleteVaccines(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                reminderScheduler.cancelVaccineReminder(id)
                repository.getById(id)?.let { repository.softDelete(it) }
            }
        }
    }

    private fun emitPartitioned() {
        val vaccines = latestVaccines.filter { passesTimeFilter(it) }
        val now = System.currentTimeMillis()
        val scheduledAll = vaccines.filter { it.statusRaw == "scheduled" }
        val overdue = scheduledAll
            .filter { (it.scheduledDateEpochMillis ?: Long.MAX_VALUE) < now }
            .sortedBy { it.scheduledDateEpochMillis }
        val scheduled = scheduledAll
            .filter { (it.scheduledDateEpochMillis ?: Long.MAX_VALUE) >= now }
            .sortedBy { it.scheduledDateEpochMillis }
        val administered = vaccines
            .filter { it.statusRaw == "administered" }
            .sortedByDescending { it.administeredDateEpochMillis }
        val planned = vaccines.filter { it.statusRaw == "planned" }
        val skipped = vaccines.filter { it.statusRaw == "skipped" }

        _uiState.value = MedicalVaccinesState(
            isLoading = false,
            timeFilter = timeFilter,
            unfilteredCount = latestVaccines.size,
            overdue = overdue,
            scheduled = scheduled,
            administered = administered,
            planned = planned,
            skipped = skipped,
        )
    }

    private fun passesTimeFilter(v: KBVaccine): Boolean {
        val cutoffMs = cutoffMillis() ?: return true
        val ref = v.administeredDateEpochMillis
            ?: v.scheduledDateEpochMillis
            ?: v.updatedAtEpochMillis
        if (timeFilter == VaccineListTimeFilter.CUSTOM) {
            val endExclusive = customFilterEndMs + 24L * 60 * 60 * 1000
            return ref >= customFilterStartMs && ref < endExclusive
        }
        return ref >= cutoffMs
    }

    private fun cutoffMillis(): Long? {
        val cal = Calendar.getInstance()
        return when (timeFilter) {
            VaccineListTimeFilter.ALL -> null
            VaccineListTimeFilter.MONTHS3 -> {
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis
            }
            VaccineListTimeFilter.MONTHS6 -> {
                cal.add(Calendar.MONTH, -6)
                cal.timeInMillis
            }
            VaccineListTimeFilter.YEAR1 -> {
                cal.add(Calendar.YEAR, -1)
                cal.timeInMillis
            }
            VaccineListTimeFilter.CUSTOM -> customFilterStartMs
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank()) syncCenter.stop(familyId)
    }
}
