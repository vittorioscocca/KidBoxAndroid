package it.vittorioscocca.kidbox.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import it.vittorioscocca.kidbox.data.sync.PediatricProfileSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBEmergencyContact
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicalRecordState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isChild: Boolean = true,
    val bloodGroup: String = "Non specificato",
    val allergies: String = "",
    val medicalNotes: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val emergencyContacts: List<KBEmergencyContact> = emptyList(),
    val saveError: String? = null,
    val savedAt: Long? = null,
)

@HiltViewModel
class MedicalRecordViewModel @Inject constructor(
    private val repository: PediatricProfileRepository,
    private val syncCenter: PediatricProfileSyncCenter,
    private val childDao: KBChildDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalRecordState())
    val uiState: StateFlow<MedicalRecordState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        syncCenter.start(familyId, childId)
        viewModelScope.launch {
            val isChild = childDao.getById(childId) != null
            val profile = repository.loadOnce(childId)
            if (profile == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, isChild = isChild)
                return@launch
            }
            _uiState.value = MedicalRecordState(
                isLoading = false,
                isChild = isChild,
                bloodGroup = profile.bloodGroup ?: "Non specificato",
                allergies = profile.allergies.orEmpty(),
                medicalNotes = profile.medicalNotes.orEmpty(),
                doctorName = profile.doctorName.orEmpty(),
                doctorPhone = profile.doctorPhone.orEmpty(),
                emergencyContacts = repository.decodeContacts(profile),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank() && childId.isNotBlank()) {
            syncCenter.stop(familyId, childId)
        }
    }

    fun setBloodGroup(v: String) { _uiState.value = _uiState.value.copy(bloodGroup = v) }
    fun setAllergies(v: String) { _uiState.value = _uiState.value.copy(allergies = v) }
    fun setMedicalNotes(v: String) { _uiState.value = _uiState.value.copy(medicalNotes = v) }
    fun setDoctorName(v: String) { _uiState.value = _uiState.value.copy(doctorName = v) }
    fun setDoctorPhone(v: String) { _uiState.value = _uiState.value.copy(doctorPhone = v) }

    fun upsertContact(contact: KBEmergencyContact) {
        val current = _uiState.value.emergencyContacts.toMutableList()
        val idx = current.indexOfFirst { it.id == contact.id }
        if (idx >= 0) current[idx] = contact else current.add(contact)
        _uiState.value = _uiState.value.copy(emergencyContacts = current.toList())
    }

    fun removeContact(id: String) {
        _uiState.value = _uiState.value.copy(
            emergencyContacts = _uiState.value.emergencyContacts.filterNot { it.id == id },
        )
    }

    fun save() {
        val s = _uiState.value
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            runCatching {
                repository.save(
                    familyId = familyId,
                    childId = childId,
                    bloodGroup = s.bloodGroup.takeIf { it != "Non specificato" },
                    allergies = s.allergies,
                    medicalNotes = s.medicalNotes,
                    doctorName = s.doctorName,
                    doctorPhone = s.doctorPhone,
                    emergencyContacts = s.emergencyContacts,
                )
            }.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedAt = System.currentTimeMillis(),
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveError = err.message ?: "Errore sconosciuto",
                    )
                },
            )
        }
    }
}
