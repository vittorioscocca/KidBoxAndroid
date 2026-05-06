package it.vittorioscocca.kidbox.ui.screens.health.visits

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.VisitAttachmentTag
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.KBDoctorSpecialization
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.data.local.mapper.decodeAsNeededDrugs
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.decodeTherapyTypes
import it.vittorioscocca.kidbox.data.local.mapper.encodeAsNeededDrugs
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeTherapyTypes
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.domain.model.KBAsNeededDrug
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTherapyType
import it.vittorioscocca.kidbox.notifications.VisitReminderScheduler
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MedicalVisitFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val childName: String = "",
    /** Id visita (sempre valorizzato: nuovo = UUID generato in bind). */
    val visitId: String = UUID.randomUUID().toString(),
    val navigationVisitId: String? = null,
    val currentStep: Int = 0,
    val doctorSearchText: String = "",
    val selectedDoctorName: String = "",
    val selectedSpec: KBDoctorSpecialization? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val reason: String = "",
    val showNewDoctorForm: Boolean = false,
    val visitStatus: KBVisitStatus = KBVisitStatus.PENDING,
    val visitReminderOn: Boolean = false,
    val diagnosis: String = "",
    val recommendations: String = "",
    val linkedTreatmentIds: List<String> = emptyList(),
    val asNeededDrugs: List<KBAsNeededDrug> = emptyList(),
    val therapyTypes: List<KBTherapyType> = emptyList(),
    val linkedExamIds: List<String> = emptyList(),
    val prescriptionsTab: Int = 0,
    val notes: String = "",
    val pendingAttachmentUris: List<Uri> = emptyList(),
    val hasNextVisit: Boolean = false,
    val nextVisitDateMillis: Long = startOfTodayMillis(),
    val nextVisitReminder: Boolean = true,
    val recentDoctors: List<Pair<String, String?>> = emptyList(),
    val linkedTreatmentSummaries: Map<String, String> = emptyMap(),
    val linkedExamSummaries: Map<String, Pair<String, Boolean>> = emptyMap(),
    val visitAttachments: List<KBDocumentEntity> = emptyList(),
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val totalSteps: Int get() = 5
    val canAdvance: Boolean get() = if (currentStep == 0) reason.isNotBlank() else true
    val canSave: Boolean get() = reason.isNotBlank()
    val prescriptionsBadgeCount: Int
        get() = linkedTreatmentIds.size + asNeededDrugs.size + therapyTypes.size + linkedExamIds.size
}

private fun startOfTodayMillis(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@HiltViewModel
class MedicalVisitFormViewModel @Inject constructor(
    private val repository: MedicalVisitRepository,
    private val treatmentRepository: TreatmentRepository,
    private val examRepository: MedicalExamRepository,
    private val reminderScheduler: VisitReminderScheduler,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val attachmentService: HealthAttachmentService,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalVisitFormState())
    val uiState: StateFlow<MedicalVisitFormState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var attachmentsJob: Job? = null

    fun bind(familyId: String, childId: String, visitId: String?) {
        this.familyId = familyId
        this.childId = childId
        attachmentsJob?.cancel()
        attachmentsJob = null

        viewModelScope.launch {
            val name = resolveChildName(childId)
            if (visitId != null) {
                _uiState.value = MedicalVisitFormState(
                    isLoading = true,
                    childName = name,
                    visitId = visitId,
                    navigationVisitId = visitId,
                )
                loadVisit(visitId, name)
                startAttachmentObservation(visitId)
            } else {
                _uiState.value = MedicalVisitFormState(
                    childName = name,
                    visitId = UUID.randomUUID().toString(),
                    navigationVisitId = null,
                    visitAttachments = emptyList(),
                )
                refreshRecentDoctors()
            }
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }

    private fun startAttachmentObservation(visitId: String) {
        documentRepository.startRealtime(familyId)
        attachmentsJob = documentRepository.observeAllDocuments(familyId)
            .map { docs -> docs.filter { VisitAttachmentTag.matches(it.notes, visitId) } }
            .onEach { list -> _uiState.value = _uiState.value.copy(visitAttachments = list) }
            .launchIn(viewModelScope)
    }

    private suspend fun resolveChildName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    private suspend fun refreshRecentDoctors() {
        val visits = repository.listRecentVisitsForChild(familyId, childId, 30)
        val seen = mutableSetOf<String>()
        val rows = mutableListOf<Pair<String, String?>>()
        for (v in visits) {
            val n = v.doctorName?.trim().orEmpty()
            if (n.isEmpty() || !seen.add(n)) continue
            rows.add(n to v.doctorSpecializationRaw)
            if (rows.size >= 5) break
        }
        _uiState.value = _uiState.value.copy(recentDoctors = rows)
    }

    private suspend fun loadVisit(visitId: String, childName: String) {
        val visit = repository.loadOnce(visitId)
        if (visit != null) {
            refreshRecentDoctors()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                childName = childName,
                visitId = visit.id,
                reason = visit.reason,
                selectedDoctorName = visit.doctorName.orEmpty(),
                doctorSearchText = "",
                selectedSpec = KBDoctorSpecialization.fromRaw(visit.doctorSpecializationRaw),
                dateMillis = visit.dateEpochMillis,
                visitStatus = KBVisitStatus.fromRaw(visit.visitStatusRaw),
                visitReminderOn = visit.reminderOn,
                diagnosis = visit.diagnosis.orEmpty(),
                recommendations = visit.recommendations.orEmpty(),
                linkedTreatmentIds = decodeStringList(visit.linkedTreatmentIdsJson),
                linkedExamIds = decodeStringList(visit.linkedExamIdsJson),
                asNeededDrugs = decodeAsNeededDrugs(visit.asNeededDrugsJson),
                therapyTypes = decodeTherapyTypes(visit.therapyTypesJson),
                notes = visit.notes.orEmpty(),
                hasNextVisit = visit.nextVisitDateEpochMillis != null,
                nextVisitDateMillis = visit.nextVisitDateEpochMillis ?: startOfTodayMillis(),
                nextVisitReminder = visit.nextVisitReminderOn,
                pendingAttachmentUris = emptyList(),
            )
            refreshPrescriptionSummaries()
        } else {
            refreshRecentDoctors()
            _uiState.value = _uiState.value.copy(isLoading = false, childName = childName)
        }
    }

    fun setCurrentStep(step: Int) {
        _uiState.value = _uiState.value.copy(currentStep = step.coerceIn(0, _uiState.value.totalSteps - 1))
    }

    fun setDoctorSearchText(v: String) { _uiState.value = _uiState.value.copy(doctorSearchText = v) }
    fun setSelectedDoctorName(v: String) { _uiState.value = _uiState.value.copy(selectedDoctorName = v) }
    fun setSelectedSpec(v: KBDoctorSpecialization?) { _uiState.value = _uiState.value.copy(selectedSpec = v) }
    fun setShowNewDoctorForm(v: Boolean) { _uiState.value = _uiState.value.copy(showNewDoctorForm = v) }
    fun clearSelectedDoctor() {
        _uiState.value = _uiState.value.copy(
            selectedDoctorName = "",
            selectedSpec = null,
            doctorSearchText = "",
            showNewDoctorForm = false,
        )
    }

    fun pickRecentDoctor(name: String, specRaw: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDoctorName = name,
            selectedSpec = KBDoctorSpecialization.fromRaw(specRaw),
            showNewDoctorForm = false,
            doctorSearchText = "",
        )
    }

    fun confirmNewDoctorForm() {
        _uiState.value = _uiState.value.copy(showNewDoctorForm = false)
    }

    fun setDateMillis(v: Long) { _uiState.value = _uiState.value.copy(dateMillis = v) }
    fun setReason(v: String) { _uiState.value = _uiState.value.copy(reason = v) }
    fun setVisitStatus(v: KBVisitStatus) { _uiState.value = _uiState.value.copy(visitStatus = v) }
    fun setVisitReminderOn(v: Boolean) { _uiState.value = _uiState.value.copy(visitReminderOn = v) }
    fun setDiagnosis(v: String) { _uiState.value = _uiState.value.copy(diagnosis = v) }
    fun setRecommendations(v: String) { _uiState.value = _uiState.value.copy(recommendations = v) }
    fun setNotes(v: String) { _uiState.value = _uiState.value.copy(notes = v) }
    fun setPrescriptionsTab(tab: Int) { _uiState.value = _uiState.value.copy(prescriptionsTab = tab.coerceIn(0, 3)) }

    fun setHasNextVisit(v: Boolean) { _uiState.value = _uiState.value.copy(hasNextVisit = v) }
    fun setNextVisitDateMillis(v: Long) { _uiState.value = _uiState.value.copy(nextVisitDateMillis = v) }
    fun setNextVisitReminder(v: Boolean) { _uiState.value = _uiState.value.copy(nextVisitReminder = v) }

    fun appendLinkedTreatmentId(id: String) {
        if (_uiState.value.linkedTreatmentIds.contains(id)) return
        _uiState.value = _uiState.value.copy(linkedTreatmentIds = _uiState.value.linkedTreatmentIds + id)
        refreshPrescriptionSummaries()
    }

    fun removeLinkedTreatmentId(id: String) {
        viewModelScope.launch {
            treatmentRepository.getById(id)?.let { treatmentRepository.softDelete(it) }
            _uiState.value = _uiState.value.copy(
                linkedTreatmentIds = _uiState.value.linkedTreatmentIds.filterNot { it == id },
            )
            refreshPrescriptionSummaries()
        }
    }

    fun appendLinkedExamId(id: String) {
        if (_uiState.value.linkedExamIds.contains(id)) return
        _uiState.value = _uiState.value.copy(linkedExamIds = _uiState.value.linkedExamIds + id)
        refreshPrescriptionSummaries()
    }

    fun removeLinkedExamId(id: String) {
        viewModelScope.launch {
            examRepository.softDeleteById(id)
            _uiState.value = _uiState.value.copy(
                linkedExamIds = _uiState.value.linkedExamIds.filterNot { it == id },
            )
            refreshPrescriptionSummaries()
        }
    }

    private fun refreshPrescriptionSummaries() {
        viewModelScope.launch {
            val s = _uiState.value
            val tMap = mutableMapOf<String, String>()
            for (id in s.linkedTreatmentIds) {
                treatmentRepository.getById(id)?.let { t ->
                    val d = if (t.dosageValue % 1.0 == 0.0) "%.0f".format(t.dosageValue) else "%.1f".format(t.dosageValue)
                    tMap[id] = "${t.drugName} · $d ${t.dosageUnit}"
                }
            }
            val eMap = mutableMapOf<String, Pair<String, Boolean>>()
            for (id in s.linkedExamIds) {
                examRepository.getById(id)?.let { e ->
                    eMap[id] = e.name to e.isUrgent
                }
            }
            _uiState.value = _uiState.value.copy(
                linkedTreatmentSummaries = tMap,
                linkedExamSummaries = eMap,
            )
        }
    }

    fun setAsNeededDrugs(list: List<KBAsNeededDrug>) {
        _uiState.value = _uiState.value.copy(asNeededDrugs = list)
    }

    fun addOrUpdateAsNeededDrug(drug: KBAsNeededDrug) {
        val list = _uiState.value.asNeededDrugs.toMutableList()
        val idx = list.indexOfFirst { it.id == drug.id }
        if (idx >= 0) list[idx] = drug else list.add(drug)
        _uiState.value = _uiState.value.copy(asNeededDrugs = list)
    }

    fun removeAsNeededDrug(id: String) {
        _uiState.value = _uiState.value.copy(asNeededDrugs = _uiState.value.asNeededDrugs.filterNot { it.id == id })
    }

    fun toggleTherapyType(t: KBTherapyType) {
        val cur = _uiState.value.therapyTypes
        _uiState.value = _uiState.value.copy(
            therapyTypes = if (t in cur) cur - t else cur + t,
        )
    }

    fun addPendingAttachment(uri: Uri) {
        val cur = _uiState.value.pendingAttachmentUris
        if (cur.size >= 5) return
        if (uri in cur) return
        _uiState.value = _uiState.value.copy(pendingAttachmentUris = cur + uri)
    }

    fun removePendingAttachment(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            pendingAttachmentUris = _uiState.value.pendingAttachmentUris.filterNot { it == uri },
        )
    }

    fun deleteVisitAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch { attachmentService.deleteAttachment(doc) }
    }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.value = s.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = repository.loadOnce(s.visitId)
            val createdAt = existing?.createdAtEpochMillis ?: now
            val nextDate = if (s.hasNextVisit) s.nextVisitDateMillis else null
            val nextReminderOn = s.hasNextVisit && s.nextVisitReminder

            val visit = KBMedicalVisit(
                id = s.visitId,
                familyId = familyId,
                childId = childId,
                dateEpochMillis = s.dateMillis,
                doctorName = s.selectedDoctorName.takeIf { it.isNotBlank() },
                doctorSpecializationRaw = s.selectedSpec?.rawValue,
                travelDetailsJson = null,
                reason = s.reason.trim(),
                diagnosis = s.diagnosis.takeIf { it.isNotBlank() },
                recommendations = s.recommendations.takeIf { it.isNotBlank() },
                linkedTreatmentIdsJson = encodeStringList(s.linkedTreatmentIds),
                linkedExamIdsJson = encodeStringList(s.linkedExamIds),
                asNeededDrugsJson = encodeAsNeededDrugs(s.asNeededDrugs),
                therapyTypesJson = encodeTherapyTypes(s.therapyTypes),
                prescribedExamsJson = encodeStringList(s.linkedExamIds),
                photoUrlsJson = existing?.photoUrlsJson ?: "[]",
                notes = s.notes.takeIf { it.isNotBlank() },
                nextVisitDateEpochMillis = nextDate,
                nextVisitReason = existing?.nextVisitReason,
                visitStatusRaw = s.visitStatus.rawValue,
                reminderOn = s.visitReminderOn,
                nextVisitReminderOn = nextReminderOn,
                isDeleted = false,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = now,
                updatedBy = null,
                createdBy = existing?.createdBy,
                syncStateRaw = 0,
                lastSyncError = null,
            )

            runCatching {
                // La visita deve esistere in SQLite prima di aggiornare gli esami (FK prescribingVisitId → kb_medical_visits).
                repository.save(visit)
                linkExamsToVisit(visit.id, s.linkedExamIds)
                linkTreatmentsToVisit(visit.id, s.linkedTreatmentIds)
                for (uri in s.pendingAttachmentUris) {
                    attachmentService.uploadVisitAttachment(uri, visit.id, familyId, childId)
                }
            }.fold(
                onSuccess = {
                    scheduleReminders(visit)
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

    private suspend fun linkExamsToVisit(visitId: String, examIds: List<String>) {
        for (eid in examIds) {
            val e = examRepository.getById(eid) ?: continue
            if (e.prescribingVisitId == null) {
                examRepository.upsert(
                    e.copy(
                        prescribingVisitId = visitId,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun linkTreatmentsToVisit(visitId: String, treatmentIds: List<String>) {
        for (tid in treatmentIds) {
            val t = treatmentRepository.getById(tid) ?: continue
            if (t.prescribingVisitId != null) continue
            treatmentRepository.upsert(
                t.copy(
                    prescribingVisitId = visitId,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun scheduleReminders(visit: KBMedicalVisit) {
        val visitTitle = buildString {
            append(visit.reason)
            visit.doctorName?.takeIf { it.isNotBlank() }?.let { append(" – $it") }
        }
        if (visit.reminderOn) {
            reminderScheduler.schedule(
                reminderKey = "${visit.id}_reminder",
                visitDateMillis = visit.dateEpochMillis,
                title = visitTitle,
                visitId = visit.id,
                familyId = visit.familyId,
                childId = visit.childId,
            )
        } else {
            reminderScheduler.cancel("${visit.id}_reminder", visit.id)
        }
        val nextDate = visit.nextVisitDateEpochMillis
        if (visit.nextVisitReminderOn && nextDate != null) {
            reminderScheduler.schedule(
                reminderKey = "${visit.id}_next_reminder",
                visitDateMillis = nextDate,
                title = "Prossima visita: ${visit.reason}",
                visitId = visit.id,
                familyId = visit.familyId,
                childId = visit.childId,
            )
        } else {
            reminderScheduler.cancel("${visit.id}_next_reminder", visit.id)
        }
    }
}
