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
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.FamilyLocationRepository
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
    private val repository: FamilyLocationRepository,
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
    private var hasLocationPermission: Boolean = false
    private var currentDisplayName: String = "Utente"
    private var sharingRequestedLocal: Boolean = false

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
            }.onFailure { err ->
                sharingRequestedLocal = false
                _uiState.value = _uiState.value.copy(
                    isSharing = false,
                    myMode = null,
                    myExpiresAtEpochMillis = null,
                )
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore avvio condivisione")
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
            }.onFailure { err ->
                sharingRequestedLocal = false
                expiryJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isSharing = false,
                    myMode = null,
                    myExpiresAtEpochMillis = null,
                )
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore condivisione temporanea")
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
                // Waiting for first remote location update after startSharing.
                return
            }
            expiryJob?.cancel()
            stopLocationUpdates()
            _uiState.value = _uiState.value.copy(myCurrentAddress = null)
        }
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
        repository.stopRealtime()
        super.onCleared()
    }
}
