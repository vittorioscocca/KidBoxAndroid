package it.vittorioscocca.kidbox.ui.screens.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.remote.user.AvatarRemoteStore
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AddressSuggestion(val id: String, val title: String, val subtitle: String)

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val familyAddress: String = "",
    val addressQuery: String = "",
    val addressSuggestions: List<AddressSuggestion> = emptyList(),
    val email: String = "",
    val avatarUrl: String? = null,
    val pickedAvatar: ByteArray? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isResolvingLocation: Boolean = false,
    val saveError: String? = null,
    val saveSucceeded: Boolean = false,
    val isDirty: Boolean = false,
    val planLabel: String = "Piano Free",
    val storageUsedBytes: Long = 0L,
    val storageTotalBytes: Long = 1_000_000_000L,
)

private data class SavedSnapshot(
    val firstName: String,
    val lastName: String,
    val familyAddress: String,
    val avatarFingerprint: Int,
    val avatarUrl: String?,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    application: Application,
    private val userProfileRepository: UserProfileRepository,
    private val familyDao: KBFamilyDao,
    private val avatarRemoteStore: AvatarRemoteStore,
    private val auth: FirebaseAuth,
    private val logoutUseCase: LogoutUseCase,
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val db get() = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val fusedLocation by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private var addressAutocompleteJob: Job? = null

    private var savedSnapshot = SavedSnapshot("", "", "", -1, null)

    fun onScreenVisible() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            userProfileRepository.ensureSeededFromAuth()
            val email = auth.currentUser?.email.orEmpty()
            val local = userProfileRepository.getByUid(uid)
            var first = local?.firstName.orEmpty()
            var last = local?.lastName.orEmpty()
            var addr = local?.familyAddress.orEmpty()
            var avatarUrl: String? = null
            userProfileRepository.fetchRemoteProfileFields(uid)?.let { r ->
                if (first.isBlank() && !r.firstName.isNullOrBlank()) first = r.firstName
                if (last.isBlank() && !r.lastName.isNullOrBlank()) last = r.lastName
                if (addr.isBlank() && !r.familyAddress.isNullOrBlank()) addr = r.familyAddress
            }
            runCatching {
                db.collection("users").document(uid).get().await()
            }.getOrNull()?.data?.let { d ->
                avatarUrl = (d["avatarURL"] as? String)?.takeIf { it.isNotBlank() }
            }
            savedSnapshot = SavedSnapshot(first.trim(), last.trim(), addr.trim(), -1, avatarUrl)
            _uiState.update {
                it.copy(
                    firstName = first,
                    lastName = last,
                    familyAddress = addr,
                    addressQuery = addr,
                    email = email,
                    avatarUrl = avatarUrl,
                    isLoading = false,
                    saveSucceeded = false,
                    saveError = null,
                    isDirty = false,
                )
            }
        }
    }

    fun setFirstName(value: String) = updateAndRecompute { it.copy(firstName = value, saveSucceeded = false) }

    fun setLastName(value: String) = updateAndRecompute { it.copy(lastName = value, saveSucceeded = false) }

    fun setFamilyAddress(value: String) = updateAndRecompute {
        it.copy(familyAddress = value, addressQuery = value, saveSucceeded = false)
    }

    fun setAddressQuery(value: String) {
        _uiState.update {
            it.copy(
                familyAddress = value,
                addressQuery = value,
                addressSuggestions = if (value.length < 3) emptyList() else it.addressSuggestions,
                saveSucceeded = false,
            )
        }
        fetchAddressSuggestions(value)
        recomputeDirty()
    }

    private fun fetchAddressSuggestions(query: String) {
        addressAutocompleteJob?.cancel()
        if (query.trim().length < 3) return
        addressAutocompleteJob = viewModelScope.launch {
            delay(280)
            val suggestions = withContext(Dispatchers.IO) {
                runCatching {
                    if (!Geocoder.isPresent()) return@runCatching emptyList<AddressSuggestion>()
                    val geocoder = Geocoder(getApplication(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query.trim(), 5)
                        ?.mapIndexedNotNull { index, address ->
                            val line = address.getAddressLine(0)?.trim().orEmpty()
                            if (line.isBlank()) return@mapIndexedNotNull null
                            val subtitle = listOfNotNull(
                                address.locality?.trim()?.takeIf { it.isNotEmpty() },
                                address.countryName?.trim()?.takeIf { it.isNotEmpty() },
                            ).joinToString(", ")
                            AddressSuggestion(
                                id = "${line.hashCode()}_$index",
                                title = line,
                                subtitle = subtitle,
                            )
                        }
                        ?.distinctBy { it.title.lowercase(Locale.getDefault()) }
                        .orEmpty()
                }.getOrElse {
                    Log.w(TAG, "autocomplete geocoder failed: ${it.message}")
                    emptyList()
                }
            }
            if (_uiState.value.addressQuery == query) {
                _uiState.update { it.copy(addressSuggestions = suggestions) }
            }
        }
    }

    fun selectAddressSuggestion(s: AddressSuggestion) = updateAndRecompute {
        it.copy(
            familyAddress = "${s.title}, ${s.subtitle}",
            addressQuery = "${s.title}, ${s.subtitle}",
            addressSuggestions = emptyList(),
            saveSucceeded = false,
        )
    }

    fun onAvatarPicked(bytes: ByteArray) = updateAndRecompute {
        it.copy(pickedAvatar = bytes, saveSucceeded = false)
    }

    fun requestCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            _requestLocationPermission.value = true
            return
        }
        resolveCurrentLocation()
    }

    private val _requestLocationPermission = MutableStateFlow(false)
    val requestLocationPermission: StateFlow<Boolean> = _requestLocationPermission.asStateFlow()

    fun consumeLocationPermissionRequest() {
        _requestLocationPermission.value = false
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) resolveCurrentLocation()
    }

    private fun resolveCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingLocation = true, saveError = null) }
            try {
                val location = fusedLocation
                    .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token,
                    )
                    .await()
                    ?: fusedLocation.lastLocation.await()
                if (location == null) error("Posizione non disponibile")
                val addr = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(getApplication(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                        ?.getAddressLine(0)
                } ?: "${location.latitude}, ${location.longitude}"
                updateAndRecompute {
                    it.copy(
                        familyAddress = addr,
                        addressQuery = addr,
                        addressSuggestions = emptyList(),
                        isResolvingLocation = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingLocation = false,
                        saveError = e.localizedMessage ?: "Errore rilevamento posizione",
                    )
                }
            }
        }
    }

    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null, saveSucceeded = false) }
            try {
                userProfileRepository.saveLocalProfile(s.firstName, s.lastName, s.familyAddress)
                val uid = auth.currentUser?.uid
                if (uid != null && s.pickedAvatar != null) {
                    val familyId = familyDao.observeAll().first().firstOrNull()?.id
                    val avatarUrl = avatarRemoteStore.uploadAvatar(uid, s.pickedAvatar, familyId)
                    db.collection("users").document(uid).set(
                        mapOf("avatarURL" to avatarUrl, "updatedAt" to FieldValue.serverTimestamp()),
                        com.google.firebase.firestore.SetOptions.merge(),
                    ).await()
                    _uiState.update { it.copy(avatarUrl = avatarUrl, pickedAvatar = null) }
                }
                savedSnapshot = SavedSnapshot(
                    firstName = _uiState.value.firstName.trim(),
                    lastName = _uiState.value.lastName.trim(),
                    familyAddress = _uiState.value.familyAddress.trim(),
                    // pickedAvatar has already been cleared to null at this point,
                    // so use -1 (the "nothing picked" sentinel) to match recomputeDirty().
                    avatarFingerprint = -1,
                    avatarUrl = _uiState.value.avatarUrl,
                )
                _uiState.update { it.copy(isSaving = false, saveSucceeded = true) }
                recomputeDirty()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, saveError = e.localizedMessage ?: "Errore salvataggio")
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase.logout()
            onDone()
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, saveError = null) }
            try {
                functions.getHttpsCallable("deleteAccount").call(hashMapOf<String, Any>()).await()
                logoutUseCase.logoutAndWipeLocalData()
                _uiState.update { it.copy(isDeleting = false) }
                onDone()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDeleting = false, saveError = e.localizedMessage ?: "Errore eliminazione account")
                }
            }
        }
    }

    private inline fun updateAndRecompute(block: (ProfileUiState) -> ProfileUiState) {
        _uiState.update(block)
        recomputeDirty()
    }

    private fun recomputeDirty() {
        val s = _uiState.value
        // Use -1 as the "nothing picked" sentinel so that a real ByteArray
        // whose contentHashCode() happens to be 0 still marks the form as dirty.
        val currentAvatarFingerprint = s.pickedAvatar?.contentHashCode() ?: -1
        val dirty = s.firstName.trim() != savedSnapshot.firstName ||
            s.lastName.trim() != savedSnapshot.lastName ||
            s.familyAddress.trim() != savedSnapshot.familyAddress ||
            currentAvatarFingerprint != savedSnapshot.avatarFingerprint
        _uiState.update { it.copy(isDirty = dirty) }
    }
}
