package it.vittorioscocca.kidbox.ui.screens.vehicles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.repository.VehicleEventRepository
import it.vittorioscocca.kidbox.data.repository.VehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val vehicleEventRepository: VehicleEventRepository,
    private val attachmentService: HealthAttachmentService,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()

    val vehicles: StateFlow<List<VehicleEntity>> =
        vehicleRepository.observeByFamily(familyId)
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

    fun addVehicle(
        name: String,
        licensePlate: String?,
        brand: String?,
        model: String?,
        year: Int?,
        fuelType: String?,
        color: String?,
        vin: String?,
        insuranceExpiryDate: Long?,
        revisionExpiryDate: Long?,
        taxExpiryDate: Long?,
        lastServiceDate: Long?,
        nextServiceDate: Long?,
        currentKm: Int?,
        notes: String?,
        reminderEnabled: Boolean,
        reminderOffsetsJson: String? = null,
        presetVehicleId: String? = null,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                vehicleRepository.addVehicle(
                    familyId = familyId,
                    name = name,
                    licensePlate = licensePlate,
                    brand = brand,
                    model = model,
                    year = year,
                    fuelType = fuelType,
                    color = color,
                    vin = vin,
                    insuranceExpiryDate = insuranceExpiryDate,
                    revisionExpiryDate = revisionExpiryDate,
                    taxExpiryDate = taxExpiryDate,
                    lastServiceDate = lastServiceDate,
                    nextServiceDate = nextServiceDate,
                    currentKm = currentKm,
                    notes = notes,
                    reminderEnabled = reminderEnabled,
                    reminderOffsetsJson = reminderOffsetsJson,
                    presetVehicleId = presetVehicleId,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deleteVehicle(entity: VehicleEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val events = vehicleEventRepository.listActiveByVehicle(entity.familyId, entity.id)
                for (e in events) {
                    attachmentService.deleteAllGarageAttachmentsForVehicleEvent(e.id, entity.familyId)
                }
                attachmentService.deleteAllGarageAttachmentsForVehicle(entity.id, entity.familyId)
                vehicleRepository.deleteVehicle(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }
}
