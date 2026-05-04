package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.data.local.mapper.displayTitle
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.notifications.VaccineReminderScheduler
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Allineato a iOS `VaccineStatus` per il form. */
enum class VaccineFormStatus {
    ADMINISTERED,
    SCHEDULED,
    PLANNED,
}

data class MedicalVaccineFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val vaccineId: String = UUID.randomUUID().toString(),
    /** In modifica: conserva createdAt / createdBy dal record. */
    val createdAtEpochMillis: Long? = null,
    val createdBy: String? = null,
    val childName: String = "",
    val formStatus: VaccineFormStatus = VaccineFormStatus.ADMINISTERED,
    val vaccineType: KBVaccineType = KBVaccineType.ESAVALENTE,
    val commercialName: String = "",
    val doseNumber: Int = 1,
    val totalDoses: Int = 1,
    val administeredDateEpochMillis: Long = System.currentTimeMillis(),
    val scheduledDateEpochMillis: Long = System.currentTimeMillis(),
    val lotNumber: String = "",
    val administeredBy: String = "",
    val administrationSite: String = "",
    val notes: String = "",
    /** Promemoria locale solo per stato «Da programmare». */
    val reminderOn: Boolean = false,
    val nextDoseDateEpochMillis: Long? = null,
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val canSave: Boolean get() = true
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
    private var boundKey: String? = null

    fun bind(familyId: String, childId: String, vaccineId: String?) {
        val key = "$familyId|$childId|${vaccineId ?: "new"}"
        if (boundKey == key) return
        boundKey = key
        this.familyId = familyId
        this.childId = childId

        viewModelScope.launch {
            val name = resolveChildName(childId)
            if (vaccineId != null) {
                val vaccine = repository.getById(vaccineId)
                if (vaccine != null) {
                    val formStatus = when (vaccine.statusRaw) {
                        "scheduled" -> VaccineFormStatus.SCHEDULED
                        "planned" -> VaccineFormStatus.PLANNED
                        else -> VaccineFormStatus.ADMINISTERED
                    }
                    _uiState.value = MedicalVaccineFormState(
                        isLoading = false,
                        vaccineId = vaccine.id,
                        createdAtEpochMillis = vaccine.createdAtEpochMillis,
                        createdBy = vaccine.createdBy,
                        childName = name,
                        formStatus = formStatus,
                        vaccineType = KBVaccineType.fromRaw(vaccine.vaccineTypeRaw),
                        commercialName = vaccine.commercialName.orEmpty(),
                        doseNumber = vaccine.doseNumber.coerceIn(1, 10),
                        totalDoses = vaccine.totalDoses.coerceIn(1, 10),
                        administeredDateEpochMillis = vaccine.administeredDateEpochMillis ?: System.currentTimeMillis(),
                        scheduledDateEpochMillis = vaccine.scheduledDateEpochMillis ?: System.currentTimeMillis(),
                        lotNumber = vaccine.lotNumber.orEmpty(),
                        administeredBy = vaccine.administeredBy.orEmpty(),
                        administrationSite = vaccine.administrationSiteRaw.orEmpty(),
                        notes = vaccine.notes.orEmpty(),
                        reminderOn = vaccine.reminderOn && formStatus == VaccineFormStatus.PLANNED,
                        nextDoseDateEpochMillis = vaccine.nextDoseDateEpochMillis,
                    )
                } else {
                    _uiState.value = MedicalVaccineFormState(childName = name, isLoading = false)
                }
            } else {
                _uiState.value = MedicalVaccineFormState(childName = name)
            }
        }
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    fun setFormStatus(v: VaccineFormStatus) {
        val cur = _uiState.value
        _uiState.value = cur.copy(
            formStatus = v,
            reminderOn = if (v == VaccineFormStatus.PLANNED) cur.reminderOn else false,
            nextDoseDateEpochMillis = if (v == VaccineFormStatus.PLANNED) cur.nextDoseDateEpochMillis else null,
        )
    }

    fun setReminderOn(v: Boolean) {
        val cur = _uiState.value
        val defaultNext = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }.timeInMillis
        _uiState.value = cur.copy(
            reminderOn = v,
            nextDoseDateEpochMillis = when {
                !v -> null
                cur.nextDoseDateEpochMillis != null -> cur.nextDoseDateEpochMillis
                else -> defaultNext
            },
        )
    }

    fun setNextDoseDateEpochMillis(v: Long) {
        _uiState.value = _uiState.value.copy(nextDoseDateEpochMillis = v)
    }

    fun setVaccineType(v: KBVaccineType) {
        _uiState.value = _uiState.value.copy(vaccineType = v)
    }

    fun setCommercialName(v: String) {
        _uiState.value = _uiState.value.copy(commercialName = v)
    }

    fun setDoseNumber(v: Int) {
        _uiState.value = _uiState.value.copy(doseNumber = v.coerceIn(1, 10))
    }

    fun setTotalDoses(v: Int) {
        _uiState.value = _uiState.value.copy(totalDoses = v.coerceIn(1, 10))
    }

    fun setAdministeredDateEpochMillis(v: Long) {
        _uiState.value = _uiState.value.copy(administeredDateEpochMillis = v)
    }

    fun setScheduledDateEpochMillis(v: Long) {
        _uiState.value = _uiState.value.copy(scheduledDateEpochMillis = v)
    }

    fun setLotNumber(v: String) {
        _uiState.value = _uiState.value.copy(lotNumber = v)
    }

    fun setAdministeredBy(v: String) {
        _uiState.value = _uiState.value.copy(administeredBy = v)
    }

    fun setAdministrationSite(v: String) {
        _uiState.value = _uiState.value.copy(administrationSite = v)
    }

    fun toggleAdministrationSite(site: String) {
        val cur = _uiState.value.administrationSite
        _uiState.value = _uiState.value.copy(
            administrationSite = if (cur == site) "" else site,
        )
    }

    fun setNotes(v: String) {
        _uiState.value = _uiState.value.copy(notes = v)
    }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val createdAt = s.createdAtEpochMillis ?: now
            val statusRaw = when (s.formStatus) {
                VaccineFormStatus.ADMINISTERED -> "administered"
                VaccineFormStatus.SCHEDULED -> "scheduled"
                VaccineFormStatus.PLANNED -> "planned"
            }
            val administeredMs = if (s.formStatus == VaccineFormStatus.ADMINISTERED) {
                s.administeredDateEpochMillis
            } else {
                null
            }
            val scheduledMs = if (s.formStatus == VaccineFormStatus.SCHEDULED) {
                s.scheduledDateEpochMillis
            } else {
                null
            }
            val plannedReminder = s.formStatus == VaccineFormStatus.PLANNED &&
                s.reminderOn &&
                s.nextDoseDateEpochMillis != null &&
                s.nextDoseDateEpochMillis > now
            val reminderOn = plannedReminder
            val nextDoseMs = if (plannedReminder) s.nextDoseDateEpochMillis else null

            val vaccineDraft = KBVaccine(
                id = s.vaccineId,
                familyId = familyId,
                childId = childId,
                name = "",
                vaccineTypeRaw = s.vaccineType.rawValue,
                statusRaw = statusRaw,
                commercialName = s.commercialName.trim().takeIf { it.isNotEmpty() },
                doseNumber = s.doseNumber,
                totalDoses = s.totalDoses,
                administeredDateEpochMillis = administeredMs,
                scheduledDateEpochMillis = scheduledMs,
                lotNumber = s.lotNumber.trim().takeIf { it.isNotEmpty() },
                doctorName = null,
                location = null,
                administeredBy = s.administeredBy.trim().takeIf { it.isNotEmpty() },
                administrationSiteRaw = s.administrationSite.trim().takeIf { it.isNotEmpty() },
                notes = s.notes.trim().takeIf { it.isNotEmpty() },
                reminderOn = reminderOn,
                nextDoseDateEpochMillis = nextDoseMs,
                isDeleted = false,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = now,
                updatedBy = null,
                createdBy = s.createdBy,
                syncStateRaw = 0,
                lastSyncError = null,
            )
            val displayName = vaccineDraft.displayTitle()
            val vaccine = vaccineDraft.copy(name = displayName)

            runCatching { repository.upsert(vaccine) }
                .fold(
                    onSuccess = { saved ->
                        if (saved.reminderOn && saved.statusRaw == "planned" &&
                            saved.nextDoseDateEpochMillis != null &&
                            saved.nextDoseDateEpochMillis > System.currentTimeMillis()
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
