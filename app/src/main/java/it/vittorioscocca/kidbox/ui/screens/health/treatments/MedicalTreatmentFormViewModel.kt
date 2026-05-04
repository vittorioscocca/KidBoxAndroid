package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import java.io.File
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.health.TreatmentAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val DEFAULT_TIMES_BY_FREQ = mapOf(
    1 to listOf("08:00"),
    2 to listOf("08:00", "20:00"),
    3 to listOf("08:00", "14:00", "20:00"),
    4 to listOf("08:00", "13:00", "19:00", "23:00"),
)

data class MedicalTreatmentFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val treatmentId: String = UUID.randomUUID().toString(),
    val childName: String = "",
    val drugName: String = "",
    val activeIngredient: String = "",
    val dosageValue: String = "1",
    val dosageUnit: String = "ml",
    val isLongTerm: Boolean = false,
    val durationDays: Int = 5,
    val startDateEpochMillis: Long = System.currentTimeMillis(),
    val dailyFrequency: Int = 1,
    val scheduleTimes: List<String> = listOf("08:00"),
    val notes: String = "",
    val reminderEnabled: Boolean = false,
    val attachments: List<KBDocumentEntity> = emptyList(),
    val isUploading: Boolean = false,
    val openFileEvent: Pair<String, File>? = null,
    val uploadError: String? = null,
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val canSave: Boolean get() = drugName.isNotBlank() && scheduleTimes.size == dailyFrequency

    val endDateEpochMillis: Long get() =
        startDateEpochMillis + TimeUnit.DAYS.toMillis((durationDays - 1).toLong())
}

@HiltViewModel
class MedicalTreatmentFormViewModel @Inject constructor(
    private val repository: TreatmentRepository,
    private val notifManager: TreatmentNotificationManager,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val attachmentService: HealthAttachmentService,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalTreatmentFormState())
    val uiState: StateFlow<MedicalTreatmentFormState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""

    fun bind(familyId: String, childId: String, treatmentId: String?) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId

        viewModelScope.launch {
            val name = resolveChildName(childId)
            _uiState.value = _uiState.value.copy(childName = name)
        }

        documentRepository.startRealtime(familyId)
        documentRepository.observeAllDocuments(familyId)
            .map { docs -> docs.filter { TreatmentAttachmentTag.matches(it.notes, _uiState.value.treatmentId) } }
            .onEach { docs -> _uiState.value = _uiState.value.copy(attachments = docs) }
            .launchIn(viewModelScope)

        if (treatmentId != null) loadTreatment(treatmentId)
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    private fun loadTreatment(id: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val t = repository.getById(id)
            if (t != null) {
                val times = t.scheduleTimesData.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    treatmentId = t.id,
                    drugName = t.drugName,
                    activeIngredient = t.activeIngredient.orEmpty(),
                    dosageValue = if (t.dosageValue % 1.0 == 0.0) "%.0f".format(t.dosageValue)
                    else "%.1f".format(t.dosageValue),
                    dosageUnit = t.dosageUnit,
                    isLongTerm = t.isLongTerm,
                    durationDays = t.durationDays,
                    startDateEpochMillis = t.startDateEpochMillis,
                    dailyFrequency = t.dailyFrequency,
                    scheduleTimes = times.ifEmpty { listOf("08:00") },
                    notes = t.notes.orEmpty(),
                    reminderEnabled = t.reminderEnabled,
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setDrugName(v: String) { _uiState.value = _uiState.value.copy(drugName = v) }
    fun setActiveIngredient(v: String) { _uiState.value = _uiState.value.copy(activeIngredient = v) }
    fun setDosageValue(v: String) { _uiState.value = _uiState.value.copy(dosageValue = v) }
    fun setDosageUnit(v: String) { _uiState.value = _uiState.value.copy(dosageUnit = v) }
    fun setIsLongTerm(v: Boolean) { _uiState.value = _uiState.value.copy(isLongTerm = v) }
    fun setDurationDays(v: Int) { _uiState.value = _uiState.value.copy(durationDays = v.coerceIn(1, 365)) }
    fun setStartDate(ms: Long) { _uiState.value = _uiState.value.copy(startDateEpochMillis = ms) }
    fun setNotes(v: String) { _uiState.value = _uiState.value.copy(notes = v) }
    fun setReminderEnabled(v: Boolean) { _uiState.value = _uiState.value.copy(reminderEnabled = v) }

    fun setDailyFrequency(freq: Int) {
        val defaults = DEFAULT_TIMES_BY_FREQ[freq] ?: List(freq) { "08:00" }
        _uiState.value = _uiState.value.copy(
            dailyFrequency = freq,
            scheduleTimes = defaults,
        )
    }

    fun setScheduleTime(index: Int, time: String) {
        val updated = _uiState.value.scheduleTimes.toMutableList()
        if (index < updated.size) updated[index] = time
        _uiState.value = _uiState.value.copy(scheduleTimes = updated)
    }

    fun uploadAttachment(uri: Uri) {
        val s = _uiState.value
        _uiState.value = s.copy(isUploading = true, uploadError = null)
        viewModelScope.launch {
            attachmentService.uploadTreatmentAttachment(
                uri = uri,
                treatmentId = _uiState.value.treatmentId,
                familyId = familyId,
                childId = childId,
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isUploading = false)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadError = err.message ?: "Errore upload allegato",
                )
            }
        }
    }

    fun deleteAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch { attachmentService.deleteAttachment(doc) }
    }

    fun openAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch {
            attachmentService.downloadAttachment(doc)
                .onSuccess { file -> _uiState.value = _uiState.value.copy(openFileEvent = doc.mimeType to file) }
                .onFailure { err -> _uiState.value = _uiState.value.copy(uploadError = err.message ?: "Errore apertura allegato") }
        }
    }

    fun consumeOpenFileEvent() { _uiState.value = _uiState.value.copy(openFileEvent = null) }
    fun consumeUploadError() { _uiState.value = _uiState.value.copy(uploadError = null) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dosage = s.dosageValue.toDoubleOrNull() ?: 0.0
            val endDate = if (s.isLongTerm) null else s.endDateEpochMillis

            val treatment = KBTreatment(
                id = s.treatmentId,
                familyId = familyId,
                childId = childId,
                drugName = s.drugName.trim(),
                activeIngredient = s.activeIngredient.takeIf { it.isNotBlank() },
                dosageValue = dosage,
                dosageUnit = s.dosageUnit,
                isLongTerm = s.isLongTerm,
                durationDays = s.durationDays,
                startDateEpochMillis = s.startDateEpochMillis,
                endDateEpochMillis = endDate,
                dailyFrequency = s.dailyFrequency,
                scheduleTimesData = s.scheduleTimes.joinToString(","),
                isActive = true,
                notes = s.notes.takeIf { it.isNotBlank() },
                reminderEnabled = s.reminderEnabled,
                isDeleted = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = "",
                createdBy = "",
                syncStatus = 0,
                lastSyncError = null,
                syncStateRaw = 0,
            )
            runCatching { repository.upsert(treatment) }
                .fold(
                    onSuccess = { saved ->
                        if (saved.reminderEnabled && saved.isActive) {
                            notifManager.schedule(saved, s.childName)
                        } else {
                            notifManager.cancel(saved.id)
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
