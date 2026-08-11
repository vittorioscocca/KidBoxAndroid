package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.remote.travel.TravelPlacesService
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TravelCategoryResultsViewModel @Inject constructor(
    private val placesService: TravelPlacesService,
    val ratingStore: TravelPlaceRatingStore,
) : ViewModel() {

    private val _placesResults = MutableStateFlow<List<TravelPlaceSummary>>(emptyList())
    val placesResults: StateFlow<List<TravelPlaceSummary>> = _placesResults.asStateFlow()

    private val _isLoadingPlaces = MutableStateFlow(true)
    val isLoadingPlaces: StateFlow<Boolean> = _isLoadingPlaces.asStateFlow()

    private var lastQueryKey: String? = null

    fun searchPlaces(destinationTitle: String, kind: TravelPlaceSearchKind, familyId: String) {
        val key = "$destinationTitle|${kind.rawValue}"
        if (key == lastQueryKey) return
        lastQueryKey = key

        if (destinationTitle.isBlank()) {
            _isLoadingPlaces.value = false
            _placesResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoadingPlaces.value = true
            _placesResults.value = placesService.searchPlaces(
                locationContext = destinationTitle,
                kind = kind,
                familyId = familyId,
            )
            _isLoadingPlaces.value = false
        }
    }
}
