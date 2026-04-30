package it.vittorioscocca.kidbox.ui.screens.health.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.sync.MedicalVisitSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class MedicalVisitsState(
    val isLoading: Boolean = true,
    val pending: List<KBMedicalVisit> = emptyList(),
    val booked: List<KBMedicalVisit> = emptyList(),
    val completed: List<KBMedicalVisit> = emptyList(),
    val resultAvailable: List<KBMedicalVisit> = emptyList(),
    val unknownStatus: List<KBMedicalVisit> = emptyList(),
)

@HiltViewModel
class MedicalVisitsViewModel @Inject constructor(
    private val repository: MedicalVisitRepository,
    private val syncCenter: MedicalVisitSyncCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVisitsState())
    val uiState: StateFlow<MedicalVisitsState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        syncCenter.start(familyId)
        repository.observe(familyId, childId)
            .onEach { visits ->
                _uiState.value = MedicalVisitsState(
                    isLoading = false,
                    pending = visits.filter { KBVisitStatus.fromRaw(it.visitStatusRaw) == KBVisitStatus.PENDING }
                        .sortedBy { it.dateEpochMillis },
                    booked = visits.filter { KBVisitStatus.fromRaw(it.visitStatusRaw) == KBVisitStatus.BOOKED }
                        .sortedBy { it.dateEpochMillis },
                    completed = visits.filter { KBVisitStatus.fromRaw(it.visitStatusRaw) == KBVisitStatus.COMPLETED }
                        .sortedByDescending { it.dateEpochMillis },
                    resultAvailable = visits.filter { KBVisitStatus.fromRaw(it.visitStatusRaw) == KBVisitStatus.RESULT_AVAILABLE }
                        .sortedByDescending { it.dateEpochMillis },
                    unknownStatus = visits.filter { KBVisitStatus.fromRaw(it.visitStatusRaw) == KBVisitStatus.UNKNOWN_STATUS }
                        .sortedByDescending { it.dateEpochMillis },
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank()) syncCenter.stop(familyId)
    }
}
