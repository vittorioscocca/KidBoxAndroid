package it.vittorioscocca.kidbox.ui.screens.location.geofence

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBGeofenceDao
import it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.repository.GeofenceRepository
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import it.vittorioscocca.kidbox.util.decodeStringList
import it.vittorioscocca.kidbox.util.encodeStringList
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeofenceAddressSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
)

data class GeofenceEditUiState(
    val familyId: String = "",
    val geofenceId: String? = null,
    val isOwner: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val name: String = "",
    val selectedEmoji: String = "🏫",
    val latitude: Double = 41.9028,
    val longitude: Double = 12.4964,
    val radius: Float = 200f,
    val notifyOnArrive: Boolean = true,
    val notifyOnLeave: Boolean = false,
    val monitorAllMembers: Boolean = true,
    val monitoredUserIds: Set<String> = emptySet(),
    val notifyAllMembers: Boolean = true,
    val notifyUserIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val addressSuggestions: List<GeofenceAddressSuggestion> = emptyList(),
    val members: List<KBFamilyMemberEntity> = emptyList(),
    val errorMessage: String? = null,
    val saveSucceeded: Boolean = false,
)

@HiltViewModel
class GeofenceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    private val geofenceRepository: GeofenceRepository,
    private val geofenceDao: KBGeofenceDao,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val familySyncCenter: FamilySyncCenter,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GeofenceEditUiState())
    val uiState: StateFlow<GeofenceEditUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        val familyId = savedStateHandle.get<String>("familyId").orEmpty()
        val geofenceId = savedStateHandle.get<String>("geofenceId")?.takeIf { it.isNotBlank() }
        if (familyId.isBlank()) {
            _uiState.value = GeofenceEditUiState(isLoading = false, errorMessage = "Famiglia non valida")
        } else {
            load(familyId, geofenceId)
        }
    }

    private fun load(familyId: String, geofenceId: String?) {
        // Refresh one-shot dei membri da Firestore: garantisce che Room sia aggiornato
        // anche quando questa schermata viene aperta direttamente (deeplink) senza passare
        // per la Home, che è l'unica che avvia FamilySyncCenter.startSync.
        familySyncCenter.refreshMembersOnce(familyId)
        // Avvia l'osservazione dei membri INDIPENDENTEMENTE dalla coroutine di caricamento:
        // evita la race condition in cui _uiState.value = GeofenceEditUiState(...)
        // sovrascriveva l'intero stato (incluso `members`) già popolato dall'osservatore,
        // e il Flow Room non riemetteva perché la DB non era cambiata.
        viewModelScope.launch {
            familyMemberDao.observeActiveByFamilyId(familyId).collectLatest { list ->
                _uiState.update { it.copy(members = list) }
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid.orEmpty()
            val family = familyDao.getById(familyId)
            val member = familyMemberDao.getActiveByFamilyAndUser(familyId, uid)
            val isOwner = member?.role.equals("owner", ignoreCase = true) || family?.createdBy == uid
            if (geofenceId != null) {
                val entity = geofenceDao.getById(geofenceId)
                if (entity == null) {
                    // Usa update { copy } per preservare `members` già caricati dall'osservatore
                    _uiState.update { it.copy(
                        familyId = familyId,
                        isOwner = isOwner,
                        isLoading = false,
                        errorMessage = "Zona non trovata",
                    ) }
                    return@launch
                }
                val monitored = decodeStringList(entity.monitoredMemberIdsJson)
                val notify = decodeStringList(entity.notifyMembersJson)
                _uiState.update { it.copy(
                    familyId = familyId,
                    geofenceId = entity.id,
                    isOwner = isOwner,
                    isLoading = false,
                    name = entity.name,
                    selectedEmoji = entity.emoji?.takeIf { it.isNotBlank() } ?: "📍",
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    radius = entity.radius.toFloat().coerceIn(50f, 2000f),
                    notifyOnArrive = entity.notifyOnArrive,
                    notifyOnLeave = entity.notifyOnLeave,
                    monitorAllMembers = monitored.isEmpty(),
                    monitoredUserIds = monitored.toSet(),
                    notifyAllMembers = notify.isEmpty(),
                    notifyUserIds = notify.toSet(),
                ) }
            } else {
                _uiState.update { it.copy(
                    familyId = familyId,
                    isOwner = isOwner,
                    isLoading = false,
                ) }
            }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }
    fun updateEmoji(value: String) = _uiState.update { it.copy(selectedEmoji = value) }
    fun updateRadius(value: Float) = _uiState.update { it.copy(radius = value) }
    fun updateNotifyOnArrive(value: Boolean) = _uiState.update { it.copy(notifyOnArrive = value) }
    fun updateNotifyOnLeave(value: Boolean) = _uiState.update { it.copy(notifyOnLeave = value) }
    fun updateMonitorAll(value: Boolean) = _uiState.update {
        it.copy(monitorAllMembers = value, monitoredUserIds = if (value) emptySet() else it.monitoredUserIds)
    }
    fun updateNotifyAll(value: Boolean) = _uiState.update {
        it.copy(notifyAllMembers = value, notifyUserIds = if (value) emptySet() else it.notifyUserIds)
    }
    fun toggleMonitoredUser(uid: String) = _uiState.update {
        val next = it.monitoredUserIds.toMutableSet()
        if (next.contains(uid)) next.remove(uid) else next.add(uid)
        it.copy(monitoredUserIds = next, monitorAllMembers = false)
    }
    fun toggleNotifyUser(uid: String) = _uiState.update {
        val next = it.notifyUserIds.toMutableSet()
        if (next.contains(uid)) next.remove(uid) else next.add(uid)
        it.copy(notifyUserIds = next, notifyAllMembers = false)
    }
    fun updateMapPosition(lat: Double, lon: Double) = _uiState.update {
        it.copy(latitude = lat, longitude = lon)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 3) {
            _uiState.update { it.copy(addressSuggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            val suggestions = withContext(Dispatchers.IO) {
                runCatching {
                    if (!Geocoder.isPresent()) return@runCatching emptyList()
                    val geocoder = Geocoder(app, Locale.ITALY)
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query.trim(), 5)
                        ?.mapIndexedNotNull { index, address ->
                            val line = address.getAddressLine(0)?.trim().orEmpty()
                            if (line.isBlank()) return@mapIndexedNotNull null
                            GeofenceAddressSuggestion(
                                id = "${line.hashCode()}_$index",
                                title = line,
                                subtitle = listOfNotNull(
                                    address.locality?.trim()?.takeIf { it.isNotEmpty() },
                                    address.countryName?.trim()?.takeIf { it.isNotEmpty() },
                                ).joinToString(", "),
                                latitude = address.latitude,
                                longitude = address.longitude,
                            )
                        }
                        .orEmpty()
                }.getOrDefault(emptyList())
            }
            if (_uiState.value.searchQuery == query) {
                _uiState.update { it.copy(addressSuggestions = suggestions) }
            }
        }
    }

    fun selectSuggestion(s: GeofenceAddressSuggestion) {
        _uiState.update {
            it.copy(
                searchQuery = s.title,
                addressSuggestions = emptyList(),
                latitude = s.latitude,
                longitude = s.longitude,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isOwner) return
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Inserisci un nome") }
            return
        }
        if (!state.monitorAllMembers && state.monitoredUserIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Seleziona almeno un membro da monitorare") }
            return
        }
        if (!state.notifyAllMembers && state.notifyUserIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Seleziona almeno un destinatario") }
            return
        }
        if (!state.notifyOnArrive && !state.notifyOnLeave) {
            _uiState.update { it.copy(errorMessage = "Attiva almeno arrivo o partenza") }
            return
        }
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Utente non autenticato") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val now = System.currentTimeMillis()
            val id = state.geofenceId ?: UUID.randomUUID().toString()
            val existing = state.geofenceId?.let { geofenceDao.getById(it) }
            val entity = KBGeofenceEntity(
                id = id,
                familyId = state.familyId,
                name = trimmedName,
                emoji = state.selectedEmoji,
                latitude = state.latitude,
                longitude = state.longitude,
                radius = state.radius.toDouble(),
                notifyOnArrive = state.notifyOnArrive,
                notifyOnLeave = state.notifyOnLeave,
                notifyMembersJson = encodeStringList(
                    if (state.notifyAllMembers) emptyList() else state.notifyUserIds.toList(),
                ),
                monitoredMemberIdsJson = encodeStringList(
                    if (state.monitorAllMembers) emptyList() else state.monitoredUserIds.toList(),
                ),
                isActive = existing?.isActive ?: true,
                createdBy = existing?.createdBy?.takeIf { it.isNotBlank() } ?: uid,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                isDeleted = false,
            )
            runCatching { geofenceRepository.upsert(entity) }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSucceeded = true) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Errore salvataggio",
                        )
                    }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
