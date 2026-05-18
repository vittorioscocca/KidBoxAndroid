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

data class TravelPlaceDetailUiState(
    val isLoading: Boolean = true,
    val details: TravelPlaceDetails? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class TravelPlaceDetailViewModel @Inject constructor(
    private val placesService: TravelPlacesService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TravelPlaceDetailUiState())
    val uiState: StateFlow<TravelPlaceDetailUiState> = _uiState.asStateFlow()

    fun load(context: TravelItineraryStopContext, familyId: String) {
        viewModelScope.launch {
            _uiState.value = TravelPlaceDetailUiState(isLoading = true, errorMessage = null)
            placesService.fetchDetails(context.placeName, context.locationContext, familyId)
                .fold(
                    onSuccess = { details ->
                        _uiState.value = TravelPlaceDetailUiState(isLoading = false, details = details)
                    },
                    onFailure = { error ->
                        _uiState.value = TravelPlaceDetailUiState(
                            isLoading = false,
                            errorMessage = error.localizedMessage,
                        )
                    },
                )
        }
    }
}
