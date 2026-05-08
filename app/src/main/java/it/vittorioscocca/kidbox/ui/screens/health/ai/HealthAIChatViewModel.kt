package it.vittorioscocca.kidbox.ui.screens.health.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.ai.HealthContextBuilder
import it.vittorioscocca.kidbox.data.health.ai.computeScopeId
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.TreatmentAttachmentTag
import it.vittorioscocca.kidbox.data.health.VisitAttachmentTag
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.repository.HealthAIChatRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import javax.inject.Inject
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HealthAIChatState(
    val isLoadingContext: Boolean = true,
    val isLoading: Boolean = false,
    val messages: List<KBAIMessage> = emptyList(),
    val inputText: String = "",
    val errorMessage: String? = null,
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
    val subjectName: String = "",
    val activeTreatmentsCount: Int = 0,
    val vaccinesCount: Int = 0,
    val visitsCount: Int = 0,
    val examsCount: Int = 0,
) {
    val canSend: Boolean get() = !isLoading && !isLoadingContext && inputText.isNotBlank()
    val isNearLimit: Boolean get() = dailyLimit > 0 && usageToday >= (dailyLimit * 0.8).toInt()
}

@HiltViewModel
class HealthAIChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: HealthAIChatRepository,
    private val visitRepository: MedicalVisitRepository,
    private val examRepository: MedicalExamRepository,
    private val treatmentRepository: TreatmentRepository,
    private val vaccineRepository: VaccineRepository,
    private val documentDao: KBDocumentDao,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val healthAttachmentService: HealthAttachmentService,
) : ViewModel() {
    private val COMPACTION_THRESHOLD = 0.60
    private var lastCompactionStep: Int = 0
    private var messagesInSession: Int = 0
    private var dailyLimit: Int = 0


    private val _uiState = MutableStateFlow(HealthAIChatState())
    val uiState: StateFlow<HealthAIChatState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""
    private var subjectName = ""
    private var systemPrompt = ""
    private var conversation: KBAIConversation? = null
    private var boundKey = ""
    private var lastExtractionEnsureKey = ""

    fun bind(familyId: String, childId: String) {
        val key = "$familyId:$childId"
        if (boundKey == key) return
        boundKey = key
        this.familyId = familyId
        this.childId = childId
        healthAttachmentService.enqueueBackfillHealthExtraction(familyId)

        viewModelScope.launch {
            subjectName = resolveSubjectName(childId)
            _uiState.value = _uiState.value.copy(subjectName = subjectName)
        }

        var initialized = false

        combine(
            visitRepository.observe(familyId, childId),
            examRepository.observe(familyId, childId),
            treatmentRepository.observe(familyId, childId),
            vaccineRepository.observe(familyId, childId),
            documentDao.observeByFamilyId(familyId),
        ) { visits, exams, treatments, vaccines, documents ->
            HealthData(visits, exams, treatments, vaccines, documents)
        }.onEach { data ->
            val activeVisits = data.visits.filter { !it.isDeleted }
            val activeExams = data.exams.filter { !it.isDeleted }
            val activeTreatments = data.treatments.filter { !it.isDeleted }
            val allDocs = data.documents.filter { !it.isDeleted }
            val relevantDocs = allDocs.filter { doc ->
                activeExams.any { ExamAttachmentTag.matches(doc.notes, it.id) } ||
                    activeVisits.any { VisitAttachmentTag.matches(doc.notes, it.id) } ||
                    activeTreatments.any { TreatmentAttachmentTag.matches(doc.notes, it.id) }
            }
            val pendingRelevantDocsSignature = relevantDocs
                .filter {
                    it.extractedText.isNullOrBlank() ||
                        it.extractionStatusRaw != KBTextExtractionStatus.COMPLETED.rawValue
                }
                .map { "${it.id}:${it.extractionStatusRaw}" }
                .sorted()
            val extractionEnsureKey = (
                activeVisits.map { "v:${it.id}" } +
                    activeExams.map { "e:${it.id}" } +
                    activeTreatments.map { "t:${it.id}" } +
                    pendingRelevantDocsSignature
                )
                .sorted()
                .joinToString("|")
            if (extractionEnsureKey != lastExtractionEnsureKey) {
                activeVisits.forEach { visit ->
                    healthAttachmentService.ensureVisitAttachmentsExtraction(familyId, visit.id)
                }
                activeExams.forEach { exam ->
                    healthAttachmentService.ensureExamAttachmentsExtraction(familyId, exam.id)
                }
                activeTreatments.forEach { treatment ->
                    healthAttachmentService.ensureTreatmentAttachmentsExtraction(familyId, treatment.id)
                }
                lastExtractionEnsureKey = extractionEnsureKey
            }

            val scopeId = computeScopeId(
                subjectId = childId,
                examIds = activeExams.map { it.id },
                visitIds = activeVisits.map { it.id },
                treatmentIds = activeTreatments.map { it.id },
                vaccineIds = data.vaccines.filter { !it.isDeleted }.map { it.id },
            )

            val docsByExamId = buildDocMapByTag(allDocs) { doc ->
                activeExams.firstOrNull { ExamAttachmentTag.matches(doc.notes, it.id) }?.id
            }
            val docsByVisitId = buildDocMapByTag(allDocs) { doc ->
                activeVisits.firstOrNull { VisitAttachmentTag.matches(doc.notes, it.id) }?.id
            }
            val docsByTreatmentId = buildDocMapByTag(allDocs) { doc ->
                activeTreatments.firstOrNull { TreatmentAttachmentTag.matches(doc.notes, it.id) }?.id
            }

            val resolvedName = subjectName.ifBlank { resolveSubjectName(childId) }
            val navSubjectLabel = savedStateHandle.get<String>("subjectName")?.trim().orEmpty()
            val displayName = navSubjectLabel.ifBlank { resolvedName }
            val visitN = activeVisits.size
            val examN = activeExams.size
            val activeCareN = countActiveTreatments(activeTreatments)
            val vaccineN = data.vaccines.count { !it.isDeleted }
            val aggregateIntro = buildAggregateIntro(displayName, visitN, examN, activeCareN, vaccineN)
            val contextBody = HealthContextBuilder.buildSystemPrompt(
                subjectName = resolvedName,
                subjectId = childId,
                exams = data.exams,
                visits = data.visits,
                treatments = data.treatments,
                vaccines = data.vaccines,
                documentsByExamId = docsByExamId,
                documentsByVisitId = docsByVisitId,
                documentsByTreatmentId = docsByTreatmentId,
            )
            val idAppendix = buildIdAppendixFromNavArgs()
            systemPrompt = when {
                idAppendix.isNotBlank() -> "$aggregateIntro\n\n$idAppendix\n\n$contextBody"
                else -> "$aggregateIntro\n\n$contextBody"
            }

            if (!initialized) {
                initialized = true
                val conv = chatRepository.getOrCreateConversation(familyId, childId, scopeId)
                conversation = conv
                lastCompactionStep = if (conv.summary.isNullOrBlank()) 0 else 3

                chatRepository.observeMessages(conv.id)
                    .onEach { msgs -> _uiState.value = _uiState.value.copy(messages = msgs) }
                    .launchIn(viewModelScope)
            }

            _uiState.value = _uiState.value.copy(isLoadingContext = false)
                .copy(
                    activeTreatmentsCount = activeCareN,
                    vaccinesCount = vaccineN,
                    visitsCount = visitN,
                    examsCount = examN,
                )
        }.launchIn(viewModelScope)
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        val conv = conversation ?: return
        _uiState.value = _uiState.value.copy(inputText = "", isLoading = true, errorMessage = null)

        viewModelScope.launch {
            chatRepository.sendMessage(conv, text, systemPrompt)
                .onSuccess { (_, reply) ->
                    messagesInSession = reply.usageToday
                    dailyLimit = reply.dailyLimit
                    maybeCompactIfNeeded(conv)
                    conversation = chatRepository.getOrCreateConversation(familyId, childId, conv.scopeId)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        usageToday = reply.usageToday,
                        dailyLimit = reply.dailyLimit,
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Errore nella comunicazione con l'AI",
                    )
                }
        }
    }

    private suspend fun maybeCompactIfNeeded(conv: KBAIConversation) {
        if (!shouldCompact()) return
        val currentStep = (messagesInSession / (dailyLimit * 0.20)).toInt()
        if (currentStep <= lastCompactionStep) return
        val didCompact = chatRepository.compactConversation(conv)
        if (didCompact) {
            lastCompactionStep = currentStep
        }
    }

    private fun shouldCompact(): Boolean {
        if (dailyLimit <= 0) return false
        return messagesInSession.toDouble() >= dailyLimit.toDouble() * COMPACTION_THRESHOLD
    }

    fun setInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendSuggestion(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        send()
    }

    fun clearConversation() {
        val conv = conversation ?: return
        viewModelScope.launch {
            chatRepository.clearConversation(conv)
            lastCompactionStep = 0
            _uiState.value = _uiState.value.copy(messages = emptyList())
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun resolveSubjectName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "Profilo"
    }

    private fun buildAggregateIntro(
        displayName: String,
        visitCount: Int,
        examCount: Int,
        activeTreatmentCount: Int,
        vaccineCount: Int,
    ): String = """
        Sei un assistente medico per l'app KidBox. Hai accesso alla storia clinica completa di $displayName.
        Include: [$visitCount visite], [$examCount esami], [$activeTreatmentCount cure attive], [$vaccineCount vaccini].
        Rispondi in italiano. Non sostituisci il medico.
    """.trimIndent()

    private fun countActiveTreatments(treatments: List<KBTreatment>): Int {
        val now = System.currentTimeMillis()
        return treatments.count { t ->
            t.isActive && !t.isDeleted &&
                (t.isLongTerm || t.endDateEpochMillis == null || t.endDateEpochMillis >= now)
        }
    }

    private fun jsonIdCount(key: String): Int = runCatching {
        val raw = savedStateHandle.get<String>(key).orEmpty()
        if (raw.isBlank()) 0 else JSONArray(raw).length()
    }.getOrElse { 0 }

    /** Riferimenti ID passati dalla Home (nav); solo etichette, senza dati clinici nel testo. */
    private fun buildIdAppendixFromNavArgs(): String {
        val vn = jsonIdCount("visitIdsJson")
        val en = jsonIdCount("examIdsJson")
        val tn = jsonIdCount("treatmentIdsJson")
        val vacn = jsonIdCount("vaccineIdsJson")
        if (vn + en + tn + vacn == 0) return ""
        return "Contesto navigazione: elenchi ID interni app — visite: $vn, esami: $en, cure: $tn, vaccini: $vacn."
    }

    private fun buildDocMapByTag(
        allDocs: List<KBDocumentEntity>,
        keyExtractor: (KBDocumentEntity) -> String?,
    ): Map<String, List<KBDocumentEntity>> {
        val result = mutableMapOf<String, MutableList<KBDocumentEntity>>()
        allDocs.forEach { doc ->
            val key = keyExtractor(doc) ?: return@forEach
            result.getOrPut(key) { mutableListOf() }.add(doc)
        }
        return result
    }

    private data class HealthData(
        val visits: List<KBMedicalVisit>,
        val exams: List<KBMedicalExam>,
        val treatments: List<KBTreatment>,
        val vaccines: List<KBVaccine>,
        val documents: List<KBDocumentEntity>,
    )
}

typealias HealthAiChatViewModel = HealthAIChatViewModel
