package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TravelPlaceInfoUiState(
    val isLoading: Boolean = true,
    val info: TravelPlaceInfo? = null,
    val loadFailed: Boolean = false,
)

@HiltViewModel
class TravelPlaceInfoViewModel @Inject constructor(
    private val infoService: TravelPlaceInfoService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TravelPlaceInfoUiState())
    val uiState: StateFlow<TravelPlaceInfoUiState> = _uiState.asStateFlow()

    private var lastPlace: String? = null

    fun load(placeName: String, familyId: String) {
        if (lastPlace == placeName) return
        lastPlace = placeName
        viewModelScope.launch {
            _uiState.value = TravelPlaceInfoUiState(isLoading = true)
            val info = infoService.info(placeName, familyId)
            _uiState.value = if (info != null) {
                TravelPlaceInfoUiState(isLoading = false, info = info)
            } else {
                TravelPlaceInfoUiState(isLoading = false, loadFailed = true)
            }
        }
    }
}
