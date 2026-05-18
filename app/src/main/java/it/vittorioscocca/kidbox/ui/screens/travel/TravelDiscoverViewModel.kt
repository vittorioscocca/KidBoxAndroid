package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.TravelProfilePreferences
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TravelDiscoverViewModel @Inject constructor(
    private val aiService: AIService,
    private val travelProfilePreferences: TravelProfilePreferences,
) : ViewModel() {

    private val _destinations = MutableStateFlow<List<TravelDestination>>(emptyList())
    val destinations = _destinations.asStateFlow()

    private val _profileSummary = MutableStateFlow("")
    val profileSummary = _profileSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var familyId: String = ""

    fun setFamilyId(id: String) {
        familyId = id
    }

    fun subtitle(): String {
        val summary = _profileSummary.value
        if (summary.isNotBlank()) return summary
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return travelProfilePreferences.loadProfile(uid)?.discoverSubtitle().orEmpty()
    }

    fun destinationById(id: String): TravelDestination? =
        _destinations.value.firstOrNull { it.id == id }

    fun loadSuggestions(force: Boolean = false) {
        if (_isLoading.value) return
        if (!force && _destinations.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val profile = travelProfilePreferences.loadProfile(uid)
            if (profile == null) {
                _error.value = "Completa prima le preferenze di viaggio."
                _isLoading.value = false
                return@launch
            }
            aiService.suggestTravelDestinations(profile.familyContextValue(), familyId)
                .onSuccess { result ->
                    _destinations.value = result.destinations
                    _profileSummary.value = result.profileSummary
                }
                .onFailure { e ->
                    _error.value = e.message
                }
            _isLoading.value = false
        }
    }
}
