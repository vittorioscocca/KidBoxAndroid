package it.vittorioscocca.kidbox.ui.screens.health.treatments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.repository.DoseLogRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MedicalTreatmentsState(
    val isLoading: Boolean = true,
    val active: List<KBTreatment> = emptyList(),
    val longTerm: List<KBTreatment> = emptyList(),
    val inactive: List<KBTreatment> = emptyList(),
    /** Tutte le cure dopo filtro periodo (per selezione / duplica / elimina). */
    val allFiltered: List<KBTreatment> = emptyList(),
    val takenDosesByTreatmentId: Map<String, Int> = emptyMap(),
    val timeFilter: TreatmentTimeFilter = TreatmentTimeFilter.ALL,
    val customFilterStartMillis: Long = defaultCustomFilterStartMillis(),
    val customFilterEndMillis: Long = System.currentTimeMillis(),
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isEmptyDueToFilter: Boolean = false,
)

@HiltViewModel
class MedicalTreatmentsViewModel @Inject constructor(
    private val repository: TreatmentRepository,
    private val doseLogRepository: DoseLogRepository,
    private val syncCenter: TreatmentSyncCenter,
    private val notifManager: TreatmentNotificationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalTreatmentsState())
    val uiState: StateFlow<MedicalTreatmentsState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        syncCenter.start(familyId)

        val now = System.currentTimeMillis()

        combine(
            repository.observe(familyId, childId),
            doseLogRepository.observeByFamilyAndChild(familyId, childId),
        ) { treatments, doseLogs ->
            val takenMap = doseLogs
                .filter { !it.isDeleted && it.taken }
                .groupBy { it.treatmentId }
                .mapValues { (_, logs) ->
                    logs.distinctBy { it.dayNumber to it.slotIndex }.size
                }

            val prev = _uiState.value
            val filtered = treatments
                .filter { !it.isDeleted }
                .filter {
                    passesTreatmentTimeFilter(
                        it,
                        prev.timeFilter,
                        prev.customFilterStartMillis,
                        prev.customFilterEndMillis,
                    )
                }

            val active = mutableListOf<KBTreatment>()
            val longTerm = mutableListOf<KBTreatment>()
            val inactive = mutableListOf<KBTreatment>()

            for (t in filtered) {
                when {
                    !t.isActive || t.isDeleted -> inactive.add(t)
                    t.isLongTerm -> longTerm.add(t)
                    t.endDateEpochMillis != null && t.endDateEpochMillis < now -> inactive.add(t)
                    else -> active.add(t)
                }
            }

            val anyNonDeleted = treatments.any { !it.isDeleted }
            val emptyDueToFilter = anyNonDeleted && filtered.isEmpty() && prev.timeFilter != TreatmentTimeFilter.ALL

            MedicalTreatmentsState(
                isLoading = false,
                active = active.sortedByDescending { it.startDateEpochMillis },
                longTerm = longTerm.sortedByDescending { it.startDateEpochMillis },
                inactive = inactive.sortedByDescending { it.startDateEpochMillis },
                allFiltered = filtered.sortedByDescending { it.startDateEpochMillis },
                takenDosesByTreatmentId = takenMap,
                timeFilter = prev.timeFilter,
                customFilterStartMillis = prev.customFilterStartMillis,
                customFilterEndMillis = prev.customFilterEndMillis,
                isSelecting = prev.isSelecting,
                selectedIds = prev.selectedIds.filter { id -> filtered.any { it.id == id } }.toSet(),
                isEmptyDueToFilter = emptyDueToFilter,
            )
        }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun setTimeFilter(filter: TreatmentTimeFilter) {
        _uiState.value = _uiState.value.copy(timeFilter = filter)
    }

    fun setCustomFilterStart(millis: Long) {
        _uiState.value = _uiState.value.copy(customFilterStartMillis = millis)
    }

    fun setCustomFilterEnd(millis: Long) {
        _uiState.value = _uiState.value.copy(customFilterEndMillis = millis)
    }

    fun applyCustomFilter() {
        var s = _uiState.value.customFilterStartMillis
        var e = _uiState.value.customFilterEndMillis
        if (s > e) {
            val t = s
            s = e
            e = t
        }
        _uiState.value = _uiState.value.copy(
            customFilterStartMillis = s,
            customFilterEndMillis = e,
            timeFilter = TreatmentTimeFilter.CUSTOM,
        )
    }

    fun clearTimeFilter() {
        _uiState.value = _uiState.value.copy(timeFilter = TreatmentTimeFilter.ALL)
    }

    fun setSelecting(selecting: Boolean) {
        val s = _uiState.value
        val wasSelecting = s.isSelecting
        _uiState.value = s.copy(
            isSelecting = selecting,
            selectedIds = if (!selecting || (!wasSelecting && selecting)) emptySet() else s.selectedIds,
        )
    }

    fun toggleSelection(id: String) {
        val cur = _uiState.value.selectedIds.toMutableSet()
        if (cur.contains(id)) cur.remove(id) else cur.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = cur)
    }

    fun toggleSelectAllFiltered() {
        val all = _uiState.value.allFiltered.map { it.id }.toSet()
        val cur = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (cur == all) emptySet() else all,
        )
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val snapshot = _uiState.value.allFiltered.filter { it.id in ids }
            for (t in snapshot) {
                notifManager.cancel(t.id)
                repository.softDelete(t)
            }
            _uiState.value = _uiState.value.copy(selectedIds = emptySet(), isSelecting = false)
        }
    }

    fun duplicateSelected() {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val snapshot = _uiState.value.allFiltered.filter { it.id in ids }
            val now = System.currentTimeMillis()
            for (t in snapshot) {
                val newId = UUID.randomUUID().toString()
                val endDate = if (t.isLongTerm) {
                    null
                } else {
                    now + TimeUnit.DAYS.toMillis((t.durationDays - 1).toLong())
                }
                val copy = t.copy(
                    id = newId,
                    startDateEpochMillis = now,
                    endDateEpochMillis = endDate,
                    reminderEnabled = false,
                    isActive = true,
                    isDeleted = false,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    updatedBy = null,
                    createdBy = null,
                    syncStatus = 0,
                    lastSyncError = null,
                    syncStateRaw = 0,
                )
                try {
                    repository.upsert(copy)
                } catch (_: Exception) {
                    // locale già aggiornato da observe; errore remoto resta su entity
                }
            }
            _uiState.value = _uiState.value.copy(selectedIds = emptySet(), isSelecting = false)
        }
    }
}
