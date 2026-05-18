package it.vittorioscocca.kidbox.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.mapper.decodeEmergencyContacts
import it.vittorioscocca.kidbox.data.local.mapper.decodeOfficeHours
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import it.vittorioscocca.kidbox.data.sync.PediatricProfileSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBEmergencyContact
import it.vittorioscocca.kidbox.domain.model.ReferenceDoctorDraft
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class MedicalRecordState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isChild: Boolean = true,
    val bloodGroup: String = "Non specificato",
    val allergies: String = "",
    val medicalNotes: String = "",
    val referenceDoctor: ReferenceDoctorDraft = ReferenceDoctorDraft(),
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
    private var observeJobStarted = false

    fun bind(familyId: String, childId: String) {
        val idsChanged = this.familyId != familyId || this.childId != childId
        this.familyId = familyId
        this.childId = childId
        syncCenter.start(familyId, childId)

        if (!observeJobStarted || idsChanged) {
            observeJobStarted = true
            viewModelScope.launch {
                val isChild = childDao.getById(childId) != null
                repository.observe(familyId, childId)
                    .map { profile -> profile to isChild }
                    .distinctUntilChanged()
                    .collect { (profile, childFlag) ->
                        applyProfile(profile, childFlag, fromRemote = true)
                    }
            }
        }

        refreshFromLocal()
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank() && childId.isNotBlank()) {
            syncCenter.stop(familyId, childId)
        }
    }

    private fun refreshFromLocal() {
        viewModelScope.launch {
            val isChild = childDao.getById(childId) != null
            val profile = repository.loadOnce(childId)
            applyProfile(profile, isChild, fromRemote = false)
        }
    }

    private fun applyProfile(
        profile: it.vittorioscocca.kidbox.domain.model.KBPediatricProfile?,
        isChild: Boolean,
        fromRemote: Boolean,
    ) {
        if (profile == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isChild = isChild,
            )
            return
        }

        // Non sovrascrivere modifiche in corso con snapshot Firestore.
        if (fromRemote && (_uiState.value.isSaving || _uiState.value.saveError != null)) {
            return
        }

        _uiState.value = MedicalRecordState(
            isLoading = false,
            isChild = isChild,
            bloodGroup = profile.bloodGroup?.takeIf { it.isNotBlank() } ?: "Non specificato",
            allergies = profile.allergies.orEmpty(),
            medicalNotes = profile.medicalNotes.orEmpty(),
            referenceDoctor = ReferenceDoctorDraft(
                name = profile.doctorName.orEmpty(),
                address = profile.doctorAddress.orEmpty(),
                website = profile.doctorWebsite.orEmpty(),
                officeHours = profile.decodeOfficeHours(),
            ),
            emergencyContacts = profile.decodeEmergencyContacts(),
            saveError = _uiState.value.saveError,
            savedAt = _uiState.value.savedAt,
        )
    }

    fun setBloodGroup(v: String) { _uiState.value = _uiState.value.copy(bloodGroup = v) }
    fun setAllergies(v: String) { _uiState.value = _uiState.value.copy(allergies = v) }
    fun setMedicalNotes(v: String) { _uiState.value = _uiState.value.copy(medicalNotes = v) }
    fun setReferenceDoctor(draft: ReferenceDoctorDraft) {
        _uiState.value = _uiState.value.copy(referenceDoctor = draft)
    }

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
                    bloodGroup = s.bloodGroup,
                    allergies = s.allergies,
                    medicalNotes = s.medicalNotes,
                    referenceDoctor = s.referenceDoctor,
                    emergencyContacts = s.emergencyContacts,
                )
            }.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedAt = System.currentTimeMillis(),
                        saveError = null,
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
