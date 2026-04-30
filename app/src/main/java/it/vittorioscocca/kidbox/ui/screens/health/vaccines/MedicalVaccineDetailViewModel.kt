package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.notifications.VaccineReminderScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicalVaccineDetailState(
    val isLoading: Boolean = true,
    val vaccine: KBVaccine? = null,
    val childName: String = "",
    val error: String? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class MedicalVaccineDetailViewModel @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVaccineDetailState())
    val uiState: StateFlow<MedicalVaccineDetailState> = _uiState.asStateFlow()

    private var familyId: String = ""

    fun bind(familyId: String, childId: String, vaccineId: String) {
        this.familyId = familyId
        _uiState.value = MedicalVaccineDetailState(isLoading = true)
        viewModelScope.launch {
            val childName = resolveChildName(childId)
            val vaccine = repository.getById(vaccineId)
            _uiState.value = MedicalVaccineDetailState(
                isLoading = false,
                vaccine = vaccine,
                childName = childName,
                error = if (vaccine == null) "Vaccino non trovato" else null,
            )
        }
    }

    fun requestDelete() { _uiState.value = _uiState.value.copy(confirmDelete = true) }
    fun cancelDelete() { _uiState.value = _uiState.value.copy(confirmDelete = false) }

    fun confirmDelete() {
        val vaccine = _uiState.value.vaccine ?: return
        _uiState.value = _uiState.value.copy(confirmDelete = false)
        viewModelScope.launch {
            runCatching { repository.softDelete(vaccine) }
            reminderScheduler.cancelVaccineReminder(vaccine.id)
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }

    fun markAdministered() {
        val vaccine = _uiState.value.vaccine ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = vaccine.copy(
                administeredDateEpochMillis = now,
                statusRaw = KBVaccineStatus.ADMINISTERED.rawValue,
                reminderOn = false,
            )
            runCatching { repository.upsert(updated) }
                .onSuccess { saved ->
                    reminderScheduler.cancelVaccineReminder(saved.id)
                    _uiState.value = _uiState.value.copy(vaccine = saved)
                }
        }
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }
}
