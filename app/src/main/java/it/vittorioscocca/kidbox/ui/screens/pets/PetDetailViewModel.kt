package it.vittorioscocca.kidbox.ui.screens.pets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.data.repository.PetEventRepository
import it.vittorioscocca.kidbox.data.repository.PetRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val petRepository: PetRepository,
    private val petEventRepository: PetEventRepository,
    private val treatmentRepository: TreatmentRepository,
    private val treatmentSyncCenter: TreatmentSyncCenter,
    private val treatmentNotificationManager: TreatmentNotificationManager,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val petId: String = savedStateHandle.get<String>("petId").orEmpty()

    val pet: StateFlow<PetEntity?> = petRepository.observeById(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<PetEventEntity>> =
        petEventRepository.observeByPet(familyId, petId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val treatments: StateFlow<List<KBTreatment>> =
        treatmentRepository.observeByFamilyAndPet(familyId, petId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (familyId.isNotBlank()) {
            petRepository.startRealtime(familyId)
            petEventRepository.startRealtime(familyId)
            treatmentSyncCenter.start(familyId)
        }
    }

    override fun onCleared() {
        petRepository.stopRealtime()
        petEventRepository.stopRealtime()
        super.onCleared()
    }

    fun addPetEvent(
        title: String,
        eventType: String,
        dateMillis: Long,
        nextDueDate: Long?,
        vetName: String?,
        cost: Double?,
        notes: String?,
        reminderEnabled: Boolean,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank() || petId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                petEventRepository.addPetEvent(
                    familyId = familyId,
                    petId = petId,
                    title = title,
                    eventType = eventType,
                    dateMillis = dateMillis,
                    nextDueDate = nextDueDate,
                    vetName = vetName,
                    cost = cost,
                    notes = notes,
                    reminderEnabled = reminderEnabled,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deletePet(entity: PetEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val treats = treatmentRepository.listByFamilyAndPet(entity.familyId, entity.id)
                treats.forEach { t ->
                    treatmentNotificationManager.cancel(t.id)
                    treatmentRepository.softDelete(t)
                }
                petRepository.deletePet(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun updatePet(entity: PetEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { petRepository.updatePet(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deletePetEvent(entity: PetEventEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { petEventRepository.deletePetEvent(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }
}
