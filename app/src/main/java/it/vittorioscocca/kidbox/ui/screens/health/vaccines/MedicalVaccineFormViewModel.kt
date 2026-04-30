package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.notifications.VaccineReminderScheduler
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicalVaccineFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val vaccineId: String = UUID.randomUUID().toString(),
    val childName: String = "",
    val name: String = "",
    val vaccineType: KBVaccineType = KBVaccineType.MANDATORY,
    val hasScheduledDate: Boolean = false,
    val scheduledDateEpochMillis: Long = System.currentTimeMillis(),
    val isAdministered: Boolean = false,
    val administeredDateEpochMillis: Long = System.currentTimeMillis(),
    val doctorName: String = "",
    val location: String = "",
    val lotNumber: String = "",
    val notes: String = "",
    val hasNextDose: Boolean = false,
    val nextDoseDateEpochMillis: Long = System.currentTimeMillis(),
    val reminderOn: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class MedicalVaccineFormViewModel @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVaccineFormState())
    val uiState: StateFlow<MedicalVaccineFormState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""

    fun bind(familyId: String, childId: String, vaccineId: String?) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId

        viewModelScope.launch {
            val name = resolveChildName(childId)
            _uiState.value = _uiState.value.copy(childName = name)
        }

        if (vaccineId != null) loadVaccine(vaccineId)
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    private fun loadVaccine(vaccineId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val vaccine = repository.getById(vaccineId)
            if (vaccine != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    vaccineId = vaccine.id,
                    name = vaccine.name,
                    vaccineType = KBVaccineType.fromRaw(vaccine.vaccineTypeRaw) ?: KBVaccineType.MANDATORY,
                    hasScheduledDate = vaccine.scheduledDateEpochMillis != null,
                    scheduledDateEpochMillis = vaccine.scheduledDateEpochMillis ?: System.currentTimeMillis(),
                    isAdministered = vaccine.administeredDateEpochMillis != null,
                    administeredDateEpochMillis = vaccine.administeredDateEpochMillis ?: System.currentTimeMillis(),
                    doctorName = vaccine.doctorName.orEmpty(),
                    location = vaccine.location.orEmpty(),
                    lotNumber = vaccine.lotNumber.orEmpty(),
                    notes = vaccine.notes.orEmpty(),
                    hasNextDose = vaccine.nextDoseDateEpochMillis != null,
                    nextDoseDateEpochMillis = vaccine.nextDoseDateEpochMillis ?: System.currentTimeMillis(),
                    reminderOn = vaccine.reminderOn,
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setName(v: String) { _uiState.value = _uiState.value.copy(name = v) }
    fun setVaccineType(v: KBVaccineType) { _uiState.value = _uiState.value.copy(vaccineType = v) }
    fun setHasScheduledDate(v: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasScheduledDate = v,
            reminderOn = if (!v) false else _uiState.value.reminderOn,
        )
    }
    fun setScheduledDateEpochMillis(v: Long) { _uiState.value = _uiState.value.copy(scheduledDateEpochMillis = v) }
    fun setIsAdministered(v: Boolean) {
        _uiState.value = _uiState.value.copy(
            isAdministered = v,
            reminderOn = if (v) false else _uiState.value.reminderOn,
        )
    }
    fun setAdministeredDateEpochMillis(v: Long) { _uiState.value = _uiState.value.copy(administeredDateEpochMillis = v) }
    fun setDoctorName(v: String) { _uiState.value = _uiState.value.copy(doctorName = v) }
    fun setLocation(v: String) { _uiState.value = _uiState.value.copy(location = v) }
    fun setLotNumber(v: String) { _uiState.value = _uiState.value.copy(lotNumber = v) }
    fun setNotes(v: String) { _uiState.value = _uiState.value.copy(notes = v) }
    fun setHasNextDose(v: Boolean) { _uiState.value = _uiState.value.copy(hasNextDose = v) }
    fun setNextDoseDateEpochMillis(v: Long) { _uiState.value = _uiState.value.copy(nextDoseDateEpochMillis = v) }
    fun setReminderOn(v: Boolean) { _uiState.value = _uiState.value.copy(reminderOn = v) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val scheduledMs = if (s.hasScheduledDate) s.scheduledDateEpochMillis else null
            val administeredMs = if (s.isAdministered) s.administeredDateEpochMillis else null
            val nextDoseMs = if (s.hasNextDose) s.nextDoseDateEpochMillis else null

            val statusRaw = when {
                s.isAdministered -> KBVaccineStatus.ADMINISTERED.rawValue
                scheduledMs != null && scheduledMs < now -> KBVaccineStatus.OVERDUE.rawValue
                else -> KBVaccineStatus.SCHEDULED.rawValue
            }

            val vaccine = KBVaccine(
                id = s.vaccineId,
                familyId = familyId,
                childId = childId,
                name = s.name.trim(),
                vaccineTypeRaw = s.vaccineType.rawValue,
                statusRaw = statusRaw,
                commercialName = null,
                doseNumber = 0,
                totalDoses = 0,
                administeredDateEpochMillis = administeredMs,
                scheduledDateEpochMillis = scheduledMs,
                lotNumber = s.lotNumber.takeIf { it.isNotBlank() },
                doctorName = s.doctorName.takeIf { it.isNotBlank() },
                location = s.location.takeIf { it.isNotBlank() },
                administeredBy = null,
                administrationSiteRaw = null,
                notes = s.notes.takeIf { it.isNotBlank() },
                reminderOn = s.reminderOn && scheduledMs != null && !s.isAdministered,
                nextDoseDateEpochMillis = nextDoseMs,
                isDeleted = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = null,
                createdBy = null,
                syncStateRaw = 0,
                lastSyncError = null,
            )

            runCatching { repository.upsert(vaccine) }
                .fold(
                    onSuccess = { saved ->
                        if (saved.reminderOn && saved.scheduledDateEpochMillis != null &&
                            saved.scheduledDateEpochMillis > System.currentTimeMillis()
                        ) {
                            reminderScheduler.scheduleVaccineReminder(saved, s.childName)
                        } else {
                            reminderScheduler.cancelVaccineReminder(saved.id)
                        }
                        _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
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
