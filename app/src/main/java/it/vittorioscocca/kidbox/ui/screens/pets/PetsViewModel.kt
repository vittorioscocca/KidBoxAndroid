package it.vittorioscocca.kidbox.ui.screens.pets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.repository.PetEventRepository
import it.vittorioscocca.kidbox.data.repository.PetRepository
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val petRepository: PetRepository,
    private val petEventRepository: PetEventRepository,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()

    val pets: StateFlow<List<PetEntity>> = petRepository.observeByFamily(familyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (familyId.isNotBlank()) {
            petRepository.startRealtime(familyId)
            petEventRepository.startRealtime(familyId)
        }
    }

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    /** Pull-to-refresh: rilegge da Firestore animali ed eventi. */
    fun forceRefresh() = pullToRefresh.refresh {
        if (familyId.isBlank()) return@refresh
        petRepository.awaitForceRestartRealtime(familyId)
        petEventRepository.awaitForceRestartRealtime(familyId)
    }

    override fun onCleared() {
        petRepository.stopRealtime()
        petEventRepository.stopRealtime()
        super.onCleared()
    }

    fun addPet(
        name: String,
        species: String,
        breed: String?,
        birthDate: Long?,
        color: String?,
        chipCode: String?,
        notes: String?,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                petRepository.addPet(
                    familyId = familyId,
                    name = name,
                    species = species,
                    breed = breed,
                    birthDate = birthDate,
                    color = color,
                    chipCode = chipCode,
                    notes = notes,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deletePet(entity: PetEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { petRepository.deletePet(entity) }
                .onFailure { onError(it.message ?: "Errore") }
        }
    }
}
