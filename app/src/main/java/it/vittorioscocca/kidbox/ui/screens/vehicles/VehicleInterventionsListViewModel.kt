package it.vittorioscocca.kidbox.ui.screens.vehicles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.data.repository.VehicleEventRepository
import it.vittorioscocca.kidbox.data.repository.VehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VehicleInterventionsListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val vehicleEventRepository: VehicleEventRepository,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val vehicleId: String = savedStateHandle.get<String>("vehicleId").orEmpty()

    val vehicle: StateFlow<VehicleEntity?> =
        vehicleRepository.observeById(vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<VehicleEventEntity>> =
        vehicleEventRepository.observeByVehicle(familyId, vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (familyId.isNotBlank()) {
            vehicleRepository.startRealtime(familyId)
            vehicleEventRepository.startRealtime(familyId)
        }
    }

    override fun onCleared() {
        vehicleRepository.stopRealtime()
        vehicleEventRepository.stopRealtime()
        super.onCleared()
    }
}
