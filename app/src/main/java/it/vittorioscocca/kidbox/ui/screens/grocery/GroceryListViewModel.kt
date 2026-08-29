package it.vittorioscocca.kidbox.ui.screens.grocery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBShoppingTripEntity
import it.vittorioscocca.kidbox.data.repository.GroceryRepository
import it.vittorioscocca.kidbox.data.repository.ShoppingTripRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * I tre filtri della testata. Sostituiscono le sezioni fisse
 * "da acquistare / acquistati": con la lista lunga, scorrere fino in fondo per
 * vedere cosa è già stato preso era il gesto più frequente.
 */
enum class GroceryFilter { ALL, TO_BUY, PURCHASED }

data class GroceryListUiState(
    val familyId: String = "",
    val items: List<KBGroceryItemEntity> = emptyList(),
    val trips: List<KBShoppingTripEntity> = emptyList(),
    val filter: GroceryFilter = GroceryFilter.TO_BUY,
    val isLoading: Boolean = true,
    val isSavingTrip: Boolean = false,
    val errorMessage: String? = null,
) {
    val toBuy: List<KBGroceryItemEntity> get() = items.filter { !it.isPurchased }
    val purchased: List<KBGroceryItemEntity> get() = items.filter { it.isPurchased }

    /** Quello che il filtro attivo lascia passare. */
    val visibleItems: List<KBGroceryItemEntity> get() = when (filter) {
        GroceryFilter.ALL -> items
        GroceryFilter.TO_BUY -> toBuy
        GroceryFilter.PURCHASED -> purchased
    }

    /**
     * Raggruppa per categoria: è l'ordine in cui si gira il supermercato, e resta
     * il motivo per cui la categoria esiste nel modello.
     */
    val groupedVisible: Map<String, List<KBGroceryItemEntity>> get() = visibleItems.groupBy {
        it.category?.trim()?.takeIf { value -> value.isNotEmpty() } ?: GroceryCategory.OTHER.key
    }.toSortedMap()
}

@HiltViewModel
class GroceryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groceryRepository: GroceryRepository,
    private val shoppingTripRepository: ShoppingTripRepository,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val _uiState = MutableStateFlow(GroceryListUiState(familyId = familyId))
    val uiState: StateFlow<GroceryListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var tripsJob: Job? = null

    init {
        start()
    }

    fun start() {
        if (familyId.isBlank() || observeJob != null) return
        groceryRepository.startRealtime(familyId)
        shoppingTripRepository.startRealtime(familyId)
        observeJob = viewModelScope.launch {
            groceryRepository.observeByFamilyId(familyId).collectLatest { entities ->
                _uiState.value = _uiState.value.copy(
                    items = entities.sortedByDescending { it.createdAtEpochMillis },
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
        tripsJob = viewModelScope.launch {
            shoppingTripRepository.observeByFamilyId(familyId).collectLatest { trips ->
                _uiState.value = _uiState.value.copy(trips = trips)
            }
        }
        viewModelScope.launch { runCatching { shoppingTripRepository.flushPending(familyId) } }
    }

    fun setFilter(filter: GroceryFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun addItem(name: String, category: String?, notes: String?, quantity: Int?) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                groceryRepository.addItem(
                    familyId = familyId,
                    name = name.trim(),
                    category = category?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    quantity = quantity?.takeIf { it > 1 },
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore salvataggio")
            }
        }
    }

    fun updateItem(itemId: String, name: String, category: String?, notes: String?, quantity: Int?) {
        viewModelScope.launch {
            runCatching {
                groceryRepository.updateItem(
                    itemId = itemId,
                    name = name.trim(),
                    category = category?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    // 1 si salva come `null`: è il valore implicito di ogni riga,
                    // e tenerlo fuori dal dato evita di filtrarlo a ogni lettura.
                    quantity = quantity?.takeIf { it > 1 },
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

    /**
     * Archivia i prodotti spuntati come scontrino e crea la spesa collegata.
     * @param fallbackTitle titolo della spesa quando il negozio non è indicato.
     */
    fun saveTrip(
        storeName: String,
        total: Double,
        dateEpochMillis: Long,
        fallbackTitle: String,
        onSaved: () -> Unit,
    ) {
        val purchased = _uiState.value.purchased
        if (familyId.isBlank() || purchased.isEmpty() || _uiState.value.isSavingTrip) return
        _uiState.value = _uiState.value.copy(isSavingTrip = true)
        viewModelScope.launch {
            runCatching {
                shoppingTripRepository.saveTrip(
                    familyId = familyId,
                    storeName = storeName,
                    total = total,
                    dateEpochMillis = dateEpochMillis,
                    purchasedItems = purchased,
                    fallbackTitle = fallbackTitle,
                )
            }.onSuccess {
                // Archiviato lo scontrino la lista resta senza "presi":
                // si torna dove si stava guardando prima.
                _uiState.value = _uiState.value.copy(
                    isSavingTrip = false,
                    filter = GroceryFilter.TO_BUY,
                )
                onSaved()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isSavingTrip = false,
                    errorMessage = err.message ?: "Errore salvataggio",
                )
            }
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            runCatching { shoppingTripRepository.deleteTrip(tripId) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Errore eliminazione")
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        groceryRepository.stopRealtime()
        shoppingTripRepository.stopRealtime()
        super.onCleared()
    }
}
