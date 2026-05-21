package it.vittorioscocca.kidbox.ui.screens.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.location.GeofenceMonitorService
import it.vittorioscocca.kidbox.data.repository.FamilyLocationRepository
import it.vittorioscocca.kidbox.data.repository.GeofenceRepository
import it.vittorioscocca.kidbox.data.repository.LocationShareMode
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FamilyLocationUiState(
    val familyId: String = "",
    val sharedUsers: List<KBSharedLocationEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSharing: Boolean = false,
    val myMode: LocationShareMode? = null,
    val myExpiresAtEpochMillis: Long? = null,
    val myCurrentAddress: String? = null,
    val deviceLatitude: Double? = null,
    val deviceLongitude: Double? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class FamilyLocationViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familySessionPreferences: FamilySessionPreferences,
    private val repository: FamilyLocationRepository,
    private val geofenceRepository: GeofenceRepository,
    private val geofenceMonitor: GeofenceMonitorService,
    private val profileDao: KBUserProfileDao,
    private val countersService: CountersService,
    private val homeBadgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FamilyLocationUiState())
    val uiState: StateFlow<FamilyLocationUiState> = _uiState.asStateFlow()

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private var locationCallback: LocationCallback? = null
    private var expiryJob: Job? = null
    private var observeJob: Job? = null
    private var geofenceObserveJob: Job? = null
    private var cachedGeofences: List<it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity> = emptyList()
    private var hasLocationPermission: Boolean = false
    private var currentDisplayName: String = "Utente"
    private var sharingRequestedLocal: Boolean = false
    private var activeFamilyObserverStarted = false

    fun startObservingActiveFamily(routeFamilyId: String = "") {
        if (activeFamilyObserverStarted) return
        activeFamilyObserverStarted = true
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                val effective = ActiveFamilyResolver.resolveFamilyId(
                    families,
                    familySessionPreferences.getActiveFamilyId(),
                ).ifBlank { routeFamilyId.trim() }
                if (effective.isNotBlank()) bindFamily(effective)
            }
        }
    }

    fun bindFamily(familyId: String) {
        if (familyId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                familyId = "",
                isLoading = false,
                errorMessage = "Nessuna famiglia attiva",
            )
            return
        }
        if (_uiState.value.familyId == familyId && !_uiState.value.isLoading) return
        observeJob?.cancel()
        geofenceObserveJob?.cancel()
        repository.stopRealtime()
        geofenceRepository.stopRealtime()
        _uiState.value = _uiState.value.copy(familyId = familyId, isLoading = true, errorMessage = null)
        viewModelScope.launch { refreshDisplayName() }
        repository.startRealtime(
            familyId = familyId,
            onError = { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore sincronizzazione posizione")
            },
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeSharedUsers(familyId).collectLatest { users ->
                applyUsers(users)
            }
        }
        geofenceRepository.startRealtime(familyId) { err ->
            _uiState.value = _uiState.value.copy(
                errorMessage = err.localizedMessage ?: "Errore sincronizzazione zone",
            )
        }
        geofenceObserveJob?.cancel()
        geofenceObserveJob = viewModelScope.launch {
            geofenceRepository.observeGeofences(familyId).collectLatest { list ->
                cachedGeofences = list
                syncGeofenceMonitor()
            }
        }
        onLocationOpened()
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        hasLocationPermission = granted
        if (granted) {
            refreshCurrentDeviceLocation()
            if (_uiState.value.isSharing) {
                startLocationUpdatesIfNeeded()
            }
        } else if (!granted) {
            stopLocationUpdates()
        }
    }

    fun startRealtime() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            refreshDisplayName()
            sharingRequestedLocal = true
            _uiState.value = _uiState.value.copy(
                isSharing = true,
                myMode = LocationShareMode.REALTIME,
                myExpiresAtEpochMillis = null,
                errorMessage = null,
            )
            runCatching {
                repository.startSharing(
                    familyId = familyId,
                    displayName = currentDisplayName,
                    mode = LocationShareMode.REALTIME,
                )
            }.onSuccess {
                if (hasLocationPermission) startLocationUpdatesIfNeeded()
                syncGeofenceMonitor()
            }.onFailure { err ->
                sharingRequestedLocal = false
                _uiState.value = _uiState.value.copy(
                    isSharing = false,
                    myMode = null,
                    myExpiresAtEpochMillis = null,
                )
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore avvio condivisione")
                syncGeofenceMonitor()
            }
        }
    }

    fun startTemporary(hours: Int) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        val expiresAt = System.currentTimeMillis() + hours * 3_600_000L
        viewModelScope.launch {
            refreshDisplayName()
            sharingRequestedLocal = true
            _uiState.value = _uiState.value.copy(
                isSharing = true,
                myMode = LocationShareMode.TEMPORARY,
                myExpiresAtEpochMillis = expiresAt,
                errorMessage = null,
            )
            scheduleLocalExpiryStop(expiresAt)
            runCatching {
                repository.startSharing(
                    familyId = familyId,
                    displayName = currentDisplayName,
                    mode = LocationShareMode.TEMPORARY,
                    expiresAtEpochMillis = expiresAt,
                )
            }.onSuccess {
                if (hasLocationPermission) startLocationUpdatesIfNeeded()
                syncGeofenceMonitor()
            }.onFailure { err ->
                sharingRequestedLocal = false
                expiryJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isSharing = false,
                    myMode = null,
                    myExpiresAtEpochMillis = null,
                )
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore condivisione temporanea")
                syncGeofenceMonitor()
            }
        }
    }

    fun stopSharing() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        sharingRequestedLocal = false
        expiryJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSharing = false,
            myMode = null,
            myExpiresAtEpochMillis = null,
            myCurrentAddress = null,
        )
        viewModelScope.launch {
            runCatching { repository.stopSharing(familyId) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore stop condivisione")
                }
        }
        stopLocationUpdates()
        syncGeofenceMonitor()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onLocationOpened() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        homeBadgeManager.clearLocal(CounterField.LOCATION)
        viewModelScope.launch {
            runCatching { countersService.reset(familyId, CounterField.LOCATION) }
        }
    }

    private fun applyUsers(users: List<KBSharedLocationEntity>) {
        val now = System.currentTimeMillis()
        val myUid = auth.currentUser?.uid
        val filtered = users.filter { user ->
            if (user.modeRaw != LocationShareMode.TEMPORARY.raw) return@filter true
            val expires = user.expiresAtEpochMillis ?: return@filter true
            expires > now
        }
        val me = myUid?.let { uid -> filtered.firstOrNull { it.id == uid } }
        val mode = when (me?.modeRaw) {
            LocationShareMode.REALTIME.raw -> LocationShareMode.REALTIME
            LocationShareMode.TEMPORARY.raw -> LocationShareMode.TEMPORARY
            else -> null
        }
        _uiState.value = _uiState.value.copy(
            sharedUsers = filtered,
            isLoading = false,
            isSharing = me != null,
            myMode = mode,
            myExpiresAtEpochMillis = me?.expiresAtEpochMillis,
        )
        if (me != null) {
            sharingRequestedLocal = true
            if (hasLocationPermission) startLocationUpdatesIfNeeded()
            scheduleTemporaryExpiryStop(me)
        } else {
            if (sharingRequestedLocal) {
                syncGeofenceMonitor()
                return
            }
            expiryJob?.cancel()
            stopLocationUpdates()
            _uiState.value = _uiState.value.copy(myCurrentAddress = null)
        }
        syncGeofenceMonitor()
    }

    private fun syncGeofenceMonitor() {
        val familyId = _uiState.value.familyId
        val uid = auth.currentUser?.uid.orEmpty()
        if (familyId.isBlank() || uid.isBlank()) {
            geofenceMonitor.removeAll()
            return
        }
        val sharing = _uiState.value.isSharing || sharingRequestedLocal
        geofenceMonitor.syncMonitoring(
            familyId = familyId,
            uid = uid,
            displayName = currentDisplayName,
            isSharing = sharing,
            geofences = cachedGeofences,
            hasBackgroundLocation = hasBackgroundLocationPermission(),
        )
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermissionNow()
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun scheduleTemporaryExpiryStop(me: KBSharedLocationEntity) {
        expiryJob?.cancel()
        if (me.modeRaw != LocationShareMode.TEMPORARY.raw) return
        val expiresAt = me.expiresAtEpochMillis ?: return
        val delayMs = expiresAt - System.currentTimeMillis()
        if (delayMs <= 0L) {
            stopSharing()
            return
        }
        expiryJob = viewModelScope.launch {
            delay(delayMs)
            stopSharing()
        }
    }

    private fun scheduleLocalExpiryStop(expiresAtEpochMillis: Long) {
        expiryJob?.cancel()
        val delayMs = expiresAtEpochMillis - System.currentTimeMillis()
        if (delayMs <= 0L) {
            stopSharing()
            return
        }
        expiryJob = viewModelScope.launch {
            delay(delayMs)
            stopSharing()
        }
    }

    private suspend fun refreshDisplayName() {
        val uid = auth.currentUser?.uid ?: return
        val profile = profileDao.getByUid(uid)
        currentDisplayName = profile?.displayName?.trim()?.takeIf { it.isNotBlank() } ?: "Utente"
        val familyId = _uiState.value.familyId
        if (_uiState.value.isSharing && familyId.isNotBlank()) {
            repository.updateDisplayName(familyId, currentDisplayName)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesIfNeeded() {
        if (!hasLocationPermission || !_uiState.value.isSharing || locationCallback != null) return
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation ?: return
                val familyId = _uiState.value.familyId
                if (familyId.isBlank()) return
                viewModelScope.launch {
                    runCatching {
                        repository.updateMyLocation(
                            familyId = familyId,
                            latitude = last.latitude,
                            longitude = last.longitude,
                            accuracy = last.accuracy.toDouble(),
                            displayName = currentDisplayName,
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        deviceLatitude = last.latitude,
                        deviceLongitude = last.longitude,
                    )
                    updateAddress(last.latitude, last.longitude)
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()
        locationCallback = callback
        runCatching {
            fusedClient.lastLocation.addOnSuccessListener { last ->
                if (last != null) {
                    viewModelScope.launch {
                        val familyId = _uiState.value.familyId
                        if (familyId.isNotBlank()) {
                            runCatching {
                                repository.updateMyLocation(
                                    familyId = familyId,
                                    latitude = last.latitude,
                                    longitude = last.longitude,
                                    accuracy = last.accuracy.toDouble(),
                                    displayName = currentDisplayName,
                                )
                            }
                            _uiState.value = _uiState.value.copy(
                                deviceLatitude = last.latitude,
                                deviceLongitude = last.longitude,
                            )
                            updateAddress(last.latitude, last.longitude)
                        }
                    }
                }
            }
        }
        runCatching {
            fusedClient.requestLocationUpdates(request, callback, context.mainLooper)
                .addOnFailureListener {
                    locationCallback = null
                    _uiState.value = _uiState.value.copy(errorMessage = "Impossibile avviare aggiornamento posizione")
                }
        }.onFailure {
            locationCallback = null
            _uiState.value = _uiState.value.copy(errorMessage = "Impossibile avviare aggiornamento posizione")
        }
    }

    private fun stopLocationUpdates() {
        val callback = locationCallback ?: return
        runCatching { fusedClient.removeLocationUpdates(callback) }
        locationCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun refreshCurrentDeviceLocation() {
        if (!hasLocationPermission) return
        runCatching {
            fusedClient.lastLocation
                .addOnSuccessListener { last ->
                    if (last != null) {
                        _uiState.value = _uiState.value.copy(
                            deviceLatitude = last.latitude,
                            deviceLongitude = last.longitude,
                        )
                    }
                }
                .addOnFailureListener { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.localizedMessage ?: "Impossibile leggere la posizione attuale",
                    )
                }
        }
    }

    private suspend fun updateAddress(
        lat: Double,
        lon: Double,
    ) {
        val address = withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context, Locale.ITALY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "$lat, $lon"
                } else {
                    @Suppress("DEPRECATION")
                    val line = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0)
                    line ?: "$lat, $lon"
                }
            }.getOrDefault("$lat, $lon")
        }
        _uiState.value = _uiState.value.copy(myCurrentAddress = address)
    }

    fun hasLocationPermissionNow(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onCleared() {
        stopLocationUpdates()
        expiryJob?.cancel()
        observeJob?.cancel()
        geofenceObserveJob?.cancel()
        repository.stopRealtime()
        geofenceRepository.stopRealtime()
        geofenceMonitor.removeAll()
        super.onCleared()
    }
}
