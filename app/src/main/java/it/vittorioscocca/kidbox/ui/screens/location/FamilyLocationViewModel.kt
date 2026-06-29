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
import it.vittorioscocca.kidbox.data.location.LocationSharingService
import it.vittorioscocca.kidbox.data.location.LocationSharingStateStore
import it.vittorioscocca.kidbox.data.location.LocationSharingWatchdogWorker
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

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
                LocationSharingStateStore.markActive(context, currentDisplayName, expiresAtEpochMillis = 0L)
                LocationSharingWatchdogWorker.enqueue(context)
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
                LocationSharingStateStore.markActive(context, currentDisplayName, expiresAtEpochMillis = expiresAt)
                LocationSharingWatchdogWorker.enqueue(context)
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
        stopSharingService()
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
        // Se `me` non è ancora in DB (es. Firestore ha scritto isSharing=true ma il primo
        // fix GPS non è ancora arrivato), rispettiamo `sharingRequestedLocal` per non
        // resettare l'UI a "non condivido" prima ancora che il GPS risponda.
        val effectiveSharing = me != null || sharingRequestedLocal
        _uiState.value = _uiState.value.copy(
            sharedUsers = filtered,
            isLoading = false,
            isSharing = effectiveSharing,
            myMode = if (me != null) mode else _uiState.value.myMode,
            myExpiresAtEpochMillis = if (me != null) me.expiresAtEpochMillis else _uiState.value.myExpiresAtEpochMillis,
        )
        if (me != null) {
            sharingRequestedLocal = true
            if (hasLocationPermission) startLocationUpdatesIfNeeded()
            scheduleTemporaryExpiryStop(me)
        } else {
            if (sharingRequestedLocal) {
                // Stiamo aspettando il primo fix GPS: avvia comunque gli aggiornamenti
                // in modo che possano produrre le prime coordinate.
                if (hasLocationPermission) startLocationUpdatesIfNeeded()
                syncGeofenceMonitor()
                return
            }
            expiryJob?.cancel()
            stopLocationUpdates()
            stopSharingService()
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
        // Indipendente da isSharing: le zone vanno monitorate sempre (vedi GeofenceMonitorService).
        geofenceMonitor.syncMonitoring(
            familyId = familyId,
            uid = uid,
            displayName = currentDisplayName,
            geofences = cachedGeofences,
        )
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
        if (!hasLocationPermission || !_uiState.value.isSharing) return
        // Il foreground service è il writer autoritativo verso Firestore: continua a
        // inviare la posizione anche quando l'app è in background o chiusa.
        startSharingService()
        // Lo stream interno al ViewModel serve solo ad aggiornare la UI (marker + indirizzo)
        // mentre la schermata è aperta; non scrive su Firestore per evitare doppi writer.
        if (locationCallback != null) return
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation ?: return
                viewModelScope.launch {
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
            fusedClient.requestLocationUpdates(request, callback, context.mainLooper)
                .addOnFailureListener {
                    locationCallback = null
                }
        }.onFailure {
            locationCallback = null
        }
    }

    private fun startSharingService() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        runCatching {
            LocationSharingService.start(context, familyId, currentDisplayName)
        }.onFailure { err ->
            _uiState.value = _uiState.value.copy(
                errorMessage = err.localizedMessage ?: "Impossibile avviare la condivisione in background",
            )
        }
    }

    private fun stopSharingService() {
        LocationSharingStateStore.markInactive(context)
        LocationSharingWatchdogWorker.cancel(context)
        runCatching { LocationSharingService.stop(context) }
    }

    /** Ferma solo lo stream UI interno al ViewModel; il foreground service resta attivo. */
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
        val fallback = "$lat, $lon"
        val address = withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context, Locale.ITALY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API async introdotta in Android 13
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            cont.resume(
                                addresses.firstOrNull()?.getAddressLine(0) ?: fallback,
                            )
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val line = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0)
                    line ?: fallback
                }
            }.getOrDefault(fallback)
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
