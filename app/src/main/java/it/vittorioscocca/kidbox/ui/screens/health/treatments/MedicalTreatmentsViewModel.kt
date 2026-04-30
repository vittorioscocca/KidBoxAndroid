package it.vittorioscocca.kidbox.ui.screens.health.treatments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.repository.DoseLogRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.sync.DoseLogSyncCenter
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class MedicalTreatmentsState(
    val isLoading: Boolean = true,
    val active: List<KBTreatment> = emptyList(),
    val longTerm: List<KBTreatment> = emptyList(),
    val inactive: List<KBTreatment> = emptyList(),
)

@HiltViewModel
class MedicalTreatmentsViewModel @Inject constructor(
    private val repository: TreatmentRepository,
    private val doseLogRepository: DoseLogRepository,
    private val syncCenter: TreatmentSyncCenter,
    private val doseLogSyncCenter: DoseLogSyncCenter,
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
        doseLogSyncCenter.start(familyId)

        val now = System.currentTimeMillis()

        repository.observe(familyId, childId)
            .onEach { treatments ->
                val active = mutableListOf<KBTreatment>()
                val longTerm = mutableListOf<KBTreatment>()
                val inactive = mutableListOf<KBTreatment>()

                for (t in treatments) {
                    when {
                        !t.isActive || t.isDeleted -> inactive.add(t)
                        t.isLongTerm -> longTerm.add(t)
                        t.endDateEpochMillis != null && t.endDateEpochMillis < now -> inactive.add(t)
                        else -> active.add(t)
                    }
                }

                _uiState.value = MedicalTreatmentsState(
                    isLoading = false,
                    active = active.sortedByDescending { it.startDateEpochMillis },
                    longTerm = longTerm.sortedByDescending { it.startDateEpochMillis },
                    inactive = inactive.sortedByDescending { it.startDateEpochMillis },
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank()) {
            syncCenter.stop(familyId)
            doseLogSyncCenter.stop(familyId)
        }
    }
}
