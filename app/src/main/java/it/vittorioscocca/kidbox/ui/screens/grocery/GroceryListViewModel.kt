package it.vittorioscocca.kidbox.ui.screens.grocery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.repository.GroceryRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class GroceryListUiState(
    val familyId: String = "",
    val items: List<KBGroceryItemEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val toBuy: List<KBGroceryItemEntity> get() = items.filter { !it.isPurchased }
    val purchased: List<KBGroceryItemEntity> get() = items.filter { it.isPurchased }
    val groupedToBuy: Map<String, List<KBGroceryItemEntity>> get() = toBuy.groupBy {
        it.category?.trim()?.takeIf { value -> value.isNotEmpty() } ?: "Altro"
    }.toSortedMap()
}

@HiltViewModel
class GroceryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groceryRepository: GroceryRepository,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val _uiState = MutableStateFlow(GroceryListUiState(familyId = familyId))
    val uiState: StateFlow<GroceryListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        start()
    }

    fun start() {
        if (familyId.isBlank() || observeJob != null) return
        groceryRepository.startRealtime(familyId)
        observeJob = viewModelScope.launch {
            groceryRepository.observeByFamilyId(familyId).collectLatest { entities ->
                _uiState.value = _uiState.value.copy(
                    items = entities.sortedByDescending { it.createdAtEpochMillis },
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    fun addItem(name: String, category: String?, notes: String?) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                groceryRepository.addItem(
                    familyId = familyId,
                    name = name.trim(),
                    category = category?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore salvataggio")
            }
        }
    }

    fun updateItem(itemId: String, name: String, category: String?, notes: String?) {
        viewModelScope.launch {
            runCatching {
                groceryRepository.updateItem(
                    itemId = itemId,
                    name = name.trim(),
                    category = category?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore aggiornamento")
            }
        }
    }

    fun togglePurchased(itemId: String) {
        viewModelScope.launch {
            runCatching { groceryRepository.togglePurchased(itemId) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore aggiornamento")
                }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            runCatching { groceryRepository.deleteItem(itemId) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore eliminazione")
                }
        }
    }

    fun deleteAllPurchased() {
        val purchasedIds = _uiState.value.purchased.map { it.id }
        if (purchasedIds.isEmpty()) return
        viewModelScope.launch {
            purchasedIds.forEach { id ->
                runCatching { groceryRepository.deleteItem(id) }
                    .onFailure { err ->
                        _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore eliminazione")
                    }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        groceryRepository.stopRealtime()
        super.onCleared()
    }
}
