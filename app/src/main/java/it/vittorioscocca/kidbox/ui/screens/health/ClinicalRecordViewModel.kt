package it.vittorioscocca.kidbox.ui.screens.health

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import it.vittorioscocca.kidbox.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordAIUsageInfo
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordContentBuilder
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordOrchestrator
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordPdfGenerator
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordReport
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordReportArea
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordSectionPolicy
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordSnapshot
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordStore
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordSummaryBuilder
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClinicalRecordUiState(
    val snapshot: ClinicalRecordSnapshot? = null,
    val report: ClinicalRecordReport? = null,
    val selectedArea: ClinicalRecordReportArea? = null,
    val visitCount: Int = 0,
    val examCount: Int = 0,
    val treatmentCount: Int = 0,
    val isRefreshing: Boolean = false,
    val refreshStatusMessage: String = "Integrazione dati e sintesi…",
    val pendingAIUnits: Int? = null,
    val lastAIUsage: ClinicalRecordAIUsageInfo? = null,
    val isExporting: Boolean = false,
    val message: String? = null,
    val exportPdfIntent: Intent? = null,
)

@HiltViewModel
class ClinicalRecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clinicalRecordStore: ClinicalRecordStore,
    private val childDao: KBChildDao,
    private val treatmentDao: KBTreatmentDao,
    private val visitDao: KBMedicalVisitDao,
    private val examDao: KBMedicalExamDao,
    private val vaccineDao: KBVaccineDao,
    private val documentDao: KBDocumentDao,
    private val profileRepository: PediatricProfileRepository,
    private val healthLinkStore: HealthLinkStore,
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClinicalRecordUiState())
    val uiState: StateFlow<ClinicalRecordUiState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""
    private var subjectName = ""
    private var exportLines: List<String> = emptyList()

    fun bind(familyId: String, childId: String, subjectName: String) {
        this.familyId = familyId
        this.childId = childId
        this.subjectName = subjectName
        if (_uiState.value.snapshot == null) {
            loadCachedReportIfNeeded()
        }
    }

    fun refreshContent() {
        if (familyId.isBlank() || childId.isBlank()) return
        viewModelScope.launch {
            val useAI = shouldUseAI()
            _uiState.value = _uiState.value.copy(
                isRefreshing = true,
                refreshStatusMessage = "Lettura visite, esami e referti…",
                message = null,
                lastAIUsage = null,
                pendingAIUnits = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val inputs = loadClinicalInputs()
                    if (useAI) {
                        val estimate = ClinicalRecordOrchestrator.estimateAIMessageUnits(
                            subjectName = inputs.name,
                            birthMillis = inputs.birthMillis,
                            residence = null,
                            profile = inputs.profile,
                            health = inputs.health,
                            healthLabel = "Health Connect",
                            treatments = inputs.treatments,
                            vaccines = inputs.vaccines,
                            visits = inputs.visits,
                            exams = inputs.exams,
                            documents = inputs.documents,
                        )
                        if (estimate != null) {
                            _uiState.value = _uiState.value.copy(
                                pendingAIUnits = estimate.messageUnits,
                                refreshStatusMessage = if (estimate.isLargeContext) {
                                    "Sintesi AI in corso (${estimate.messageUnits} messaggi, contesto ampio)…"
                                } else {
                                    val unitLabel = if (estimate.messageUnits == 1) "messaggio" else "messaggi"
                                    "Sintesi clinica narrativa in corso (${estimate.messageUnits} $unitLabel)…"
                                },
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                refreshStatusMessage = "Sintesi clinica narrativa in corso…",
                            )
                        }
                    }
                    buildBundle(inputs, useAI)
                }
            }.onSuccess { result ->
                clinicalRecordStore.saveReport(childId, result.bundle.report)
                applyBundle(result)
                _uiState.value = _uiState.value.copy(isRefreshing = false, pendingAIUnits = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    pendingAIUnits = null,
                    message = it.message?.takeIf { msg -> msg.isNotBlank() }
                        ?: "Impossibile aggiornare la cartella. Riprova.",
                )
            }
        }
    }

    fun exportPdf() {
        if (childId.isBlank()) return
        viewModelScope.launch {
            if (exportLines.isEmpty()) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val inputs = loadClinicalInputs()
                        buildBundle(inputs, useAI = false)
                    }
                }.onSuccess { exportLines = it.exportLines }
            }
            if (exportLines.isEmpty()) return@launch
            _uiState.value = _uiState.value.copy(isExporting = true, message = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = clinicalRecordStore.pdfFile(childId)
                    ClinicalRecordPdfGenerator.writePdf(exportLines, file)
                    file
                }
            }.onSuccess { file ->
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportPdfIntent = buildViewIntent(file),
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    message = "Impossibile esportare il PDF. Riprova.",
                )
            }
        }
    }

    fun consumeExportIntent() {
        _uiState.value = _uiState.value.copy(exportPdfIntent = null)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun openSection(sectionId: String) {
        val section = _uiState.value.snapshot?.sections?.firstOrNull { it.id == sectionId }
        val areaId = section?.reportAreaId ?: sectionId
        val area = _uiState.value.report?.areas?.firstOrNull { it.id == areaId }
        _uiState.value = _uiState.value.copy(selectedArea = area)
    }

    fun dismissSection() {
        _uiState.value = _uiState.value.copy(selectedArea = null)
    }

    private fun loadCachedReportIfNeeded() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                clinicalRecordStore.loadReport(childId)?.let { report ->
                    ClinicalRecordOrchestrator.filterExcludedAreas(
                        report.copy(
                            areas = report.areas.filter {
                                ClinicalRecordSectionPolicy.shouldGenerateStandaloneSection(it.id)
                            },
                        ),
                    )
                }
            } ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    val inputs = loadClinicalInputs()
                    val snap = ClinicalRecordSummaryBuilder.build(
                        subjectName = inputs.name,
                        childBirthDateEpochMillis = inputs.birthMillis,
                        profile = inputs.profile,
                        healthSnapshot = inputs.health,
                        healthSourceLabel = "Health Connect",
                        treatments = inputs.treatments,
                        vaccines = inputs.vaccines,
                        visits = inputs.visits,
                        exams = inputs.exams,
                        report = cached,
                    )
                    BuildResult(
                        bundle = ClinicalRecordOrchestrator.Bundle(
                            report = cached,
                            snapshot = snap,
                            exportLines = cached.fullDocumentLines,
                            aiUsage = null,
                        ),
                        visitCount = inputs.visits.size,
                        examCount = inputs.exams.size,
                        treatmentCount = inputs.activeTreatments.size,
                        exportLines = cached.fullDocumentLines,
                    )
                }
            }.onSuccess { applyBundle(it) }
        }
    }

    private data class ClinicalInputs(
        val name: String,
        val birthMillis: Long?,
        val profile: it.vittorioscocca.kidbox.domain.model.KBPediatricProfile?,
        val health: it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot?,
        val treatments: List<it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity>,
        val vaccines: List<it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity>,
        val visits: List<it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity>,
        val exams: List<it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity>,
        val documents: List<KBDocumentEntity>,
        val activeTreatments: List<it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity>,
        val byExam: Map<String, List<KBDocumentEntity>>,
        val byVisit: Map<String, List<KBDocumentEntity>>,
        val byTreatment: Map<String, List<KBDocumentEntity>>,
    )

    private data class BuildResult(
        val bundle: ClinicalRecordOrchestrator.Bundle,
        val visitCount: Int,
        val examCount: Int,
        val treatmentCount: Int,
        val exportLines: List<String>,
    )

    private suspend fun loadClinicalInputs(): ClinicalInputs {
        val child = childDao.getById(childId)
        val profile = profileRepository.loadOnce(childId)
        val health = healthLinkStore.load(childId)
        val treatments = treatmentDao.observeByFamilyAndChild(familyId, childId).first()
        val visits = visitDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val exams = examDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val vaccines = vaccineDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val activeTreatments = treatments.filter { it.isActive && !it.isDeleted && it.petId.isBlank() }
        val docs = documentDao.observeByFamilyId(familyId).first()
            .filter { !it.isDeleted && (it.childId == null || it.childId == childId) }
        val (byExam, byVisit, byTreatment) = groupHealthDocuments(docs)
        val name = subjectName.ifBlank { child?.name ?: "Profilo" }
        return ClinicalInputs(
            name = name,
            birthMillis = child?.birthDateEpochMillis,
            profile = profile,
            health = health,
            treatments = treatments,
            vaccines = vaccines,
            visits = visits,
            exams = exams,
            documents = docs,
            activeTreatments = activeTreatments,
            byExam = byExam,
            byVisit = byVisit,
            byTreatment = byTreatment,
        )
    }

    private suspend fun buildBundle(inputs: ClinicalInputs, useAI: Boolean): BuildResult {
        val bundle = ClinicalRecordOrchestrator.build(
            subjectName = inputs.name,
            birthMillis = inputs.birthMillis,
            residence = null,
            profile = inputs.profile,
            health = inputs.health,
            healthLabel = "Health Connect",
            treatments = inputs.treatments,
            vaccines = inputs.vaccines,
            visits = inputs.visits,
            exams = inputs.exams,
            documents = inputs.documents,
            useAI = useAI,
            familyId = familyId,
            aiRepository = aiRepository,
        )
        val export = bundle.exportLines.ifEmpty {
            ClinicalRecordContentBuilder.buildLines(
                subjectName = inputs.name,
                childBirthDateEpochMillis = inputs.birthMillis,
                profile = inputs.profile,
                healthSnapshot = inputs.health,
                healthSourceLabel = "Health Connect",
                treatments = inputs.treatments,
                vaccines = inputs.vaccines,
                visits = inputs.visits,
                exams = inputs.exams,
                documentsByExamId = inputs.byExam,
                documentsByVisitId = inputs.byVisit,
                documentsByTreatmentId = inputs.byTreatment,
            )
        }
        return BuildResult(
            bundle = bundle.copy(exportLines = export),
            visitCount = inputs.visits.size,
            examCount = inputs.exams.size,
            treatmentCount = inputs.activeTreatments.size,
            exportLines = export,
        )
    }

    private fun applyBundle(result: BuildResult) {
        exportLines = result.exportLines
        _uiState.value = _uiState.value.copy(
            snapshot = result.bundle.snapshot,
            report = result.bundle.report,
            visitCount = result.visitCount,
            examCount = result.examCount,
            treatmentCount = result.treatmentCount,
            lastAIUsage = result.bundle.aiUsage,
        )
    }

    private fun shouldUseAI(): Boolean = BuildConfig.AI_ENABLED

    private fun groupHealthDocuments(
        docs: List<KBDocumentEntity>,
    ): Triple<Map<String, List<KBDocumentEntity>>, Map<String, List<KBDocumentEntity>>, Map<String, List<KBDocumentEntity>>> {
        val byExam = mutableMapOf<String, MutableList<KBDocumentEntity>>()
        val byVisit = mutableMapOf<String, MutableList<KBDocumentEntity>>()
        val byTreatment = mutableMapOf<String, MutableList<KBDocumentEntity>>()
        docs.forEach { doc ->
            val tag = doc.notes ?: return@forEach
            when {
                tag.startsWith("exam:") -> byExam.getOrPut(tag.removePrefix("exam:")) { mutableListOf() }.add(doc)
                tag.startsWith("visit:") -> byVisit.getOrPut(tag.removePrefix("visit:")) { mutableListOf() }.add(doc)
                tag.startsWith("treatment:") -> byTreatment.getOrPut(tag.removePrefix("treatment:")) { mutableListOf() }.add(doc)
            }
        }
        return Triple(byExam, byVisit, byTreatment)
    }

    private fun buildViewIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
