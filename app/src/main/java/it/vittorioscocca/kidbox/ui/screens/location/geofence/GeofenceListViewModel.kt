package it.vittorioscocca.kidbox.ui.screens.location.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity
import it.vittorioscocca.kidbox.data.repository.GeofenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class GeofenceListUiState(
    val familyId: String = "",
    val geofences: List<KBGeofenceEntity> = emptyList(),
    val isOwner: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class GeofenceListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GeofenceListUiState())
    val uiState: StateFlow<GeofenceListUiState> = _uiState.asStateFlow()

    init {
        val familyId = savedStateHandle.get<String>("familyId").orEmpty()
        if (familyId.isBlank()) {
            _uiState.value = GeofenceListUiState(isLoading = false, errorMessage = "Famiglia non valida")
        } else {
            bind(familyId)
        }
    }

    private fun bind(familyId: String) {
        _uiState.value = _uiState.value.copy(familyId = familyId, isLoading = true)
        geofenceRepository.startRealtime(familyId) { err ->
            _uiState.value = _uiState.value.copy(
                errorMessage = err.localizedMessage ?: "Errore sincronizzazione zone",
            )
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid.orEmpty()
            val family = familyDao.getById(familyId)
            val member = familyMemberDao.getActiveByFamilyAndUser(familyId, uid)
            val isOwnerFromMember = member?.role.equals("owner", ignoreCase = true)
            val isOwnerFromFamily = family?.createdBy == uid
            _uiState.value = _uiState.value.copy(isOwner = isOwnerFromMember || isOwnerFromFamily)
        }
        viewModelScope.launch {
            geofenceRepository.observeGeofences(familyId).collectLatest { list ->
                _uiState.value = _uiState.value.copy(geofences = list, isLoading = false)
            }
        }
    }

    fun toggleActive(geofence: KBGeofenceEntity, active: Boolean) {
        viewModelScope.launch {
            runCatching { geofenceRepository.setActive(geofence, active) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.localizedMessage ?: "Errore aggiornamento zona",
                    )
                }
        }
    }

    fun deleteGeofence(geofence: KBGeofenceEntity) {
        viewModelScope.launch {
            runCatching { geofenceRepository.softDelete(geofence.id, geofence.familyId) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.localizedMessage ?: "Errore eliminazione zona",
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        geofenceRepository.stopRealtime()
        super.onCleared()
    }
}
