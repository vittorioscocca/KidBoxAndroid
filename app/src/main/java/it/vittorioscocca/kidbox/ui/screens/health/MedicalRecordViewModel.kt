package it.vittorioscocca.kidbox.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeEmergencyContacts
import it.vittorioscocca.kidbox.data.local.mapper.decodeOfficeHours
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import it.vittorioscocca.kidbox.data.sync.PediatricProfileSyncCenter
import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBEmergencyContact
import it.vittorioscocca.kidbox.domain.model.ReferenceDoctorDraft
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    val hasHealthLink: Boolean = false,
    val linkedBirthDateEpochMillis: Long? = null,
    val linkedAgeDescription: String? = null,
)

@HiltViewModel
class MedicalRecordViewModel @Inject constructor(
    private val repository: PediatricProfileRepository,
    private val syncCenter: PediatricProfileSyncCenter,
    private val childDao: KBChildDao,
    private val healthLinkStore: HealthLinkStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalRecordState())
    val uiState: StateFlow<MedicalRecordState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var observeJobStarted = false
    private val firestore get() = FirebaseFirestore.getInstance()

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

    private suspend fun applyProfile(
        profile: it.vittorioscocca.kidbox.domain.model.KBPediatricProfile?,
        isChild: Boolean,
        fromRemote: Boolean,
    ) {
        if (fromRemote && (_uiState.value.isSaving || _uiState.value.saveError != null)) {
            return
        }

        val (blood, birthMillis, hasLink, ageDesc) = resolveBloodGroupAndBirth(profile)

        if (profile == null) {
            _uiState.value = MedicalRecordState(
                isLoading = false,
                isChild = isChild,
                bloodGroup = blood,
                hasHealthLink = hasLink,
                linkedBirthDateEpochMillis = birthMillis,
                linkedAgeDescription = ageDesc,
                saveError = _uiState.value.saveError,
                savedAt = _uiState.value.savedAt,
            )
            return
        }

        _uiState.value = MedicalRecordState(
            isLoading = false,
            isChild = isChild,
            bloodGroup = blood,
            allergies = profile.allergies.orEmpty(),
            medicalNotes = profile.medicalNotes.orEmpty(),
            referenceDoctor = ReferenceDoctorDraft(
                name = profile.doctorName.orEmpty(),
                email = profile.doctorEmail.orEmpty(),
                address = profile.doctorAddress.orEmpty(),
                website = profile.doctorWebsite.orEmpty(),
                officeHours = profile.decodeOfficeHours(),
            ),
            emergencyContacts = profile.decodeEmergencyContacts(),
            saveError = _uiState.value.saveError,
            savedAt = _uiState.value.savedAt,
            hasHealthLink = hasLink,
            linkedBirthDateEpochMillis = birthMillis,
            linkedAgeDescription = ageDesc,
        )
    }

    private suspend fun resolveBloodGroupAndBirth(
        profile: it.vittorioscocca.kidbox.domain.model.KBPediatricProfile?,
    ): BirthResolveResult {
        var blood = profile?.bloodGroup?.takeIf { it.isNotBlank() } ?: "Non specificato"
        val linked = healthLinkStore.load(childId)
        if (blood == "Non specificato") {
            linked?.bloodGroup?.takeIf { it in BLOOD_GROUPS }?.let { blood = it }
        }

        val child = childDao.getById(childId)
        val birthMillis = linked?.birthDateEpochMillis ?: child?.birthDateEpochMillis
        val ageDesc = birthMillis?.let(HealthAgeFormatting::ageDescriptionFromBirth)
        return BirthResolveResult(blood, birthMillis, linked != null, ageDesc)
    }

    fun setBloodGroup(v: String) { _uiState.value = _uiState.value.copy(bloodGroup = v) }

    fun setLinkedBirthDate(epochMillis: Long) {
        _uiState.value = _uiState.value.copy(
            linkedBirthDateEpochMillis = epochMillis,
            linkedAgeDescription = HealthAgeFormatting.ageDescriptionFromBirth(epochMillis),
        )
    }

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
                persistBirthDate(s.linkedBirthDateEpochMillis)
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
                    refreshFromLocal()
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

    private suspend fun persistBirthDate(birthMillis: Long?) {
        val millis = birthMillis ?: return
        val child = childDao.getById(childId) ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val now = System.currentTimeMillis()
        val updated = child.copy(
            birthDateEpochMillis = millis,
            updatedBy = uid.ifBlank { child.updatedBy },
            updatedAtEpochMillis = now,
        )
        childDao.upsert(updated)
        val data = hashMapOf<String, Any?>(
            "birthDate" to com.google.firebase.Timestamp(
                millis / 1000,
                ((millis % 1000) * 1_000_000).toInt(),
            ),
            "updatedBy" to (updated.updatedBy ?: uid),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        firestore.collection("families").document(familyId)
            .collection("children").document(child.id)
            .set(data, SetOptions.merge())
            .await()

        val existing = healthLinkStore.load(childId)
        val snapshot = (existing ?: HealthImportSnapshot(syncedAtEpochMillis = now)).copy(
            birthDateEpochMillis = millis,
            syncedAtEpochMillis = now,
        )
        healthLinkStore.save(childId, snapshot)
    }

    private data class BirthResolveResult(
        val bloodGroup: String,
        val birthMillis: Long?,
        val hasHealthLink: Boolean,
        val ageDescription: String?,
    )

    private companion object {
        private val BLOOD_GROUPS = setOf(
            "Non specificato", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-",
        )
    }
}
