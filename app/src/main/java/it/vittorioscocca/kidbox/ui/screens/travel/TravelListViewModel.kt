package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.TravelProfile
import it.vittorioscocca.kidbox.data.local.TravelProfilePreferences
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripLegDao
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.TripRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TravelListViewModel @Inject constructor(
    private val tripDao: KBTripDao,
    private val tripLegDao: KBTripLegDao,
    familySessionPreferences: FamilySessionPreferences,
    private val subscriptionRepository: SubscriptionRepository,
    private val travelProfilePreferences: TravelProfilePreferences,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _needsOnboarding = MutableStateFlow<Boolean?>(null)
    val needsOnboarding = _needsOnboarding.asStateFlow()

    private val _travelProfile = MutableStateFlow<TravelProfile?>(null)
    val travelProfile = _travelProfile.asStateFlow()

    private val familyIdFlow = MutableStateFlow(familySessionPreferences.getActiveFamilyId().orEmpty())
    private val _familyPlan = MutableStateFlow(KBPlan.FREE)
    val familyPlan = _familyPlan.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedTripIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTripIds = _selectedTripIds.asStateFlow()

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    /** Pull-to-refresh: rilegge i viaggi da Firestore. */
    fun forceRefresh() = pullToRefresh.refresh {
        val familyId = familyIdFlow.value
        if (familyId.isBlank()) return@refresh
        tripRepository.awaitForceRestartRealtime(familyId)
    }

    fun setFamilyId(familyId: String) {
        familyIdFlow.value = familyId
        observeFamilyPlan(familyId)
        refreshOnboardingGate()
    }

    fun refreshOnboardingGate() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        _needsOnboarding.value = if (uid.isBlank()) {
            false
        } else {
            !travelProfilePreferences.hasCompletedOnboarding(uid)
        }
        refreshTravelProfile()
    }

    fun refreshTravelProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        _travelProfile.value = travelProfilePreferences.loadProfile(uid)
    }

    fun completeOnboarding(profile: TravelProfile) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            travelProfilePreferences.saveProfile(uid, profile)
        }
        _needsOnboarding.value = false
        _travelProfile.value = profile
    }

    private fun observeFamilyPlan(familyId: String) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (familyId.isBlank() || uid.isBlank()) return@launch
            subscriptionRepository.planFlow(familyId, uid).collect { _familyPlan.value = it }
        }
    }

    val trips: StateFlow<List<KBTripEntity>> = familyIdFlow
        .flatMapLatest { familyId ->
            if (familyId.isBlank()) flowOf(emptyList()) else tripDao.observeAll(familyId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredTrips: StateFlow<List<KBTripEntity>> = combine(trips, searchQuery) { list, query ->
        filterTripsBySearch(list, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchAndSelection() {
        _searchQuery.value = ""
        _selectedTripIds.value = emptySet()
        _isSelectionMode.value = false
    }

    private fun filterTripsBySearch(trips: List<KBTripEntity>, query: String): List<KBTripEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return trips
        return trips.filter { trip ->
            trip.name.lowercase().contains(q) ||
                TravelTripDateRangeFormatter.format(trip.startDateEpoch, trip.endDateEpoch)
                    .lowercase()
                    .contains(q)
        }
    }

    val legsByTripId: StateFlow<Map<String, List<KBTripLegEntity>>> = familyIdFlow
        .flatMapLatest { familyId ->
            if (familyId.isBlank()) {
                flowOf(emptyMap())
            } else {
                tripLegDao.observeByFamily(familyId).map { legs ->
                    legs.groupBy { it.tripId }.mapValues { (_, tripLegs) ->
                        tripLegs.sortedBy { it.order }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) _selectedTripIds.value = emptySet()
    }

    fun toggleTripSelection(tripId: String) {
        _selectedTripIds.update { current ->
            if (tripId in current) current - tripId else current + tripId
        }
    }

    fun selectAllTrips(tripIds: List<String>) {
        _selectedTripIds.value = tripIds.toSet()
    }

    fun clearSelection() {
        _selectedTripIds.value = emptySet()
    }

    fun deleteSelectedTrips(allTrips: List<KBTripEntity>) {
        viewModelScope.launch {
            allTrips.filter { it.id in _selectedTripIds.value }.forEach { trip ->
                tripRepository.deleteTrip(trip)
            }
            _selectedTripIds.value = emptySet()
            _isSelectionMode.value = false
        }
    }

}
