package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.data.sync.VaccineSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class MedicalVaccinesState(
    val isLoading: Boolean = true,
    val overdue: List<KBVaccine> = emptyList(),
    val scheduled: List<KBVaccine> = emptyList(),
    val administered: List<KBVaccine> = emptyList(),
    val skipped: List<KBVaccine> = emptyList(),
)

@HiltViewModel
class MedicalVaccinesViewModel @Inject constructor(
    private val repository: VaccineRepository,
    private val syncCenter: VaccineSyncCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVaccinesState())
    val uiState: StateFlow<MedicalVaccinesState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        syncCenter.start(familyId)
        repository.observe(familyId, childId)
            .onEach { vaccines ->
                _uiState.value = MedicalVaccinesState(
                    isLoading = false,
                    overdue = vaccines.filter { it.computedStatus() == KBVaccineStatus.OVERDUE }
                        .sortedBy { it.scheduledDateEpochMillis },
                    scheduled = vaccines.filter { it.computedStatus() == KBVaccineStatus.SCHEDULED }
                        .sortedBy { it.scheduledDateEpochMillis },
                    administered = vaccines.filter { it.computedStatus() == KBVaccineStatus.ADMINISTERED }
                        .sortedByDescending { it.administeredDateEpochMillis },
                    skipped = vaccines.filter { it.computedStatus() == KBVaccineStatus.SKIPPED },
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank()) syncCenter.stop(familyId)
    }
}
