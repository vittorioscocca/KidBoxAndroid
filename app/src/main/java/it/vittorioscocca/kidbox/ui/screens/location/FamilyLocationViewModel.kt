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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import it.vittorioscocca.kidbox.util.KBLocale

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
    private var observeJob: Job? = null
    private var geofenceObserveJob: Job? = null
    private var cachedGeofences: List<it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity> = emptyList()
    private var hasLocationPermission: Boolean = false
    private var currentDisplayName: String = "Utente"
    private var activeFamilyObserverStarted = false

    init {
        // Lo stato della PROPRIA condivisione viene dal telefono
        // (LocationSharingStateStore), non dal documento su Firestore: prima la
        // schermata, trovandolo ancora «in condivisione», riavviava il servizio,
        // e così annullava lo stop dato dalla notifica o la condivisione
        // avviata su un altro dispositivo dello stesso utente.
        viewModelScope.launch {
            LocationSharingStateStore.observe(context).collect { applySharingState() }
        }
    }

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
        applySharingState()
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

    fun startRealtime() = startSharing(
        mode = LocationShareMode.REALTIME,
        expiresAtEpochMillis = null,
        errorFallback = "Errore avvio condivisione",
    )

    fun startTemporary(hours: Int) = startSharing(
        mode = LocationShareMode.TEMPORARY,
        expiresAtEpochMillis = System.currentTimeMillis() + hours * 3_600_000L,
        errorFallback = "Errore condivisione temporanea",
    )

    /**
     * Scrive lo stato su Firestore e solo DOPO segna il telefono come attivo:
     * il servizio, appena parte, ascolta quel documento e si ferma se il
     * server dice «spenta». Tutto in `NonCancellable`: uscendo dalla schermata
     * a metà, prima si restava «in condivisione» per gli altri con il telefono
     * che non inviava niente.
     */
    private fun startSharing(
        mode: LocationShareMode,
        expiresAtEpochMillis: Long?,
        errorFallback: String,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            refreshDisplayName()
            // Ottimistico: lo stato vero arriva da LocationSharingStateStore.
            _uiState.value = _uiState.value.copy(
                isSharing = true,
                myMode = mode,
                myExpiresAtEpochMillis = expiresAtEpochMillis,
                errorMessage = null,
            )
            withContext(NonCancellable) {
                runCatching {
                    repository.startSharing(
                        familyId = familyId,
                        displayName = currentDisplayName,
                        mode = mode,
                        expiresAtEpochMillis = expiresAtEpochMillis,
                    )
                }.onSuccess {
                    val uid = auth.currentUser?.uid.orEmpty()
                    LocationSharingStateStore.markActive(
                        context,
                        familyId = familyId,
                        uid = uid,
                        displayName = currentDisplayName,
                        expiresAtEpochMillis = expiresAtEpochMillis ?: 0L,
                    )
                    LocationSharingWatchdogWorker.enqueue(context)
                    // Servizio e stream della mappa li avvia applySharingState().
                }.onFailure { err ->
                    applySharingState()
                    _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: errorFallback)
                }
            }
            syncGeofenceMonitor()
        }
    }

    /** Riflette nella UI lo stato di [LocationSharingStateStore] per la famiglia mostrata. */
    private fun applySharingState() {
        val state = LocationSharingStateStore.observe(context).value
        val familyId = _uiState.value.familyId
        val here = state.active && familyId.isNotBlank() && state.familyId == familyId
        _uiState.value = _uiState.value.copy(
            isSharing = here,
            myMode = when {
                !here -> null
                state.expiresAtEpochMillis == 0L -> LocationShareMode.REALTIME
                else -> LocationShareMode.TEMPORARY
            },
            myExpiresAtEpochMillis = state.expiresAtEpochMillis.takeIf { here && it != 0L },
            myCurrentAddress = if (here) _uiState.value.myCurrentAddress else null,
        )
        if (here) {
            // Con l'app in primo piano l'avvio del servizio è sempre permesso:
            // se era morto, riparte da qui. È idempotente.
            if (hasLocationPermission) startLocationUpdatesIfNeeded()
        } else {
            stopLocationUpdates()
        }
    }

    fun stopSharing() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
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
        val filtered = users.filter { user ->
            if (user.modeRaw != LocationShareMode.TEMPORARY.raw) return@filter true
            val expires = user.expiresAtEpochMillis ?: return@filter true
            expires > now
        }
        // Solo la mappa: lo stato della propria condivisione non si deduce da
        // qui (vedi applySharingState), e nemmeno il servizio si avvia o si
        // ferma da qui. Se la condivisione viene spenta sul server, il servizio
        // lo vede da sé ascoltando il proprio documento.
        _uiState.value = _uiState.value.copy(
            sharedUsers = filtered,
            isLoading = false,
        )
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
                val geocoder = Geocoder(context, KBLocale.current())
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
        observeJob?.cancel()
        geofenceObserveJob?.cancel()
        repository.stopRealtime()
        geofenceRepository.stopRealtime()
        geofenceMonitor.removeAll()
        super.onCleared()
    }
}
