package it.vittorioscocca.kidbox.ui.screens.health.ai

import it.vittorioscocca.kidbox.util.KBLog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.ai.AISettingsStore
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.health.ai.HealthContextBuilder
import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.remote.ai.AIRemotePreferences
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendMode
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import it.vittorioscocca.kidbox.data.health.ai.computeScopeId
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
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
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatStreamingDelivery
import it.vittorioscocca.kidbox.ui.screens.ai.planning.FamilyMemoryPromptSection
import it.vittorioscocca.kidbox.ui.screens.ai.planning.FamilyMemoryService
import javax.inject.Inject
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HealthAIChatState(
    val isLoadingContext: Boolean = true,
    val isLoading: Boolean = false,
    val streamingMessageId: String? = null,
    val messages: List<KBAIMessage> = emptyList(),
    val inputText: String = "",
    val errorMessage: String? = null,
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
    val quotaPeriod: AIQuotaPeriod = AIQuotaPeriod.DAILY,
    val subjectName: String = "",
    val activeTreatmentsCount: Int = 0,
    val vaccinesCount: Int = 0,
    val visitsCount: Int = 0,
    val examsCount: Int = 0,
    val actionExecutionSummary: String? = null,
    val autoExecutedMessageIds: Set<String> = emptySet(),
    val estimatedMessageUnits: Int = 1,
    val estimatedCompactMessageUnits: Int = 1,
    val estimatedCompactSetupUnits: Int = 0,
    val hasCompactHealthContextCache: Boolean = false,
    val showContextModeDialog: Boolean = false,
    val pendingSendText: String = "",
    val isPreparingCompactContext: Boolean = false,
    val contextNoticeMessage: String? = null,
) {
    val canSend: Boolean get() = !isLoading && !isLoadingContext && !isPreparingCompactContext && inputText.isNotBlank()
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
    private val healthLinkStore: HealthLinkStore,
    private val familyMemoryService: FamilyMemoryService,
    private val aiSettingsStore: AISettingsStore,
    private val aiRemotePrefs: AIRemotePreferences,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
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
    private var standardSystemPrompt = ""
    private var fullSystemPrompt = ""
    private var conversation: KBAIConversation? = null
    private var boundKey = ""
    private var lastExtractionEnsureKey = ""
    private var compactHealthContextCache: Pair<Int, String>? = null
    private var didShowLargeContextNotice = false

    companion object {
        private const val TAG = "HealthAIChatVM"
    }

    fun bind(familyId: String, childId: String) {
        val key = "$familyId:$childId"
        if (boundKey == key) return
        boundKey = key
        this.familyId = familyId
        this.childId = childId
        healthAttachmentService.enqueueBackfillHealthExtraction(familyId)
        viewModelScope.launch { syncHealthContextSendPreferenceFromRemote() }

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
            val treatmentsForChild = data.treatments.filter { it.petId.isBlank() }
            val activeTreatments = treatmentsForChild.filter { !it.isDeleted }
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
            // Carica lo snapshot Health Connect persistito (null se non collegato o non importato).
            val healthSnapshot = healthLinkStore.load(childId)

            val contextBodyStandard = HealthContextBuilder.buildSystemPrompt(
                subjectName = resolvedName,
                subjectId = childId,
                exams = data.exams,
                visits = data.visits,
                treatments = treatmentsForChild,
                vaccines = data.vaccines,
                documentsByExamId = docsByExamId,
                documentsByVisitId = docsByVisitId,
                documentsByTreatmentId = docsByTreatmentId,
                refertoMaxChars = HealthAiDocumentText.STANDARD_REFERTO_MAX_CHARS,
                healthSnapshot = healthSnapshot,
            )
            val contextBodyFull = HealthContextBuilder.buildSystemPrompt(
                subjectName = resolvedName,
                subjectId = childId,
                exams = data.exams,
                visits = data.visits,
                treatments = treatmentsForChild,
                vaccines = data.vaccines,
                documentsByExamId = docsByExamId,
                documentsByVisitId = docsByVisitId,
                documentsByTreatmentId = docsByTreatmentId,
                refertoMaxChars = null,
                healthSnapshot = healthSnapshot,
            )
            val idAppendix = buildIdAppendixFromNavArgs()
            val standardBase = when {
                idAppendix.isNotBlank() -> "$aggregateIntro\n\n$idAppendix\n\n$contextBodyStandard"
                else -> "$aggregateIntro\n\n$contextBodyStandard"
            }
            val fullBase = when {
                idAppendix.isNotBlank() -> "$aggregateIntro\n\n$idAppendix\n\n$contextBodyFull"
                else -> "$aggregateIntro\n\n$contextBodyFull"
            }
            standardSystemPrompt = FamilyMemoryPromptSection.append(standardBase, familyMemoryService, familyId)
            fullSystemPrompt = FamilyMemoryPromptSection.append(fullBase, familyMemoryService, familyId)
                .ifBlank { standardSystemPrompt }

            if (!initialized) {
                initialized = true
                val conv = chatRepository.getOrCreateConversation(familyId, childId, scopeId)
                conversation = conv
                lastCompactionStep = if (conv.summary.isNullOrBlank()) 0 else 3

                chatRepository.observeMessages(conv.id)
                    .onEach { msgs ->
                        _uiState.value = _uiState.value.copy(messages = msgs)
                        refreshPayloadCostEstimate(
                            messages = msgs,
                            pendingUserText = _uiState.value.inputText,
                        )
                    }
                    .launchIn(viewModelScope)
            }

            refreshPayloadCostEstimate(
                messages = _uiState.value.messages,
                pendingUserText = _uiState.value.inputText,
            )
            KBLog.ai.info("context ready standardChars=${standardSystemPrompt.length} " +
                    "fullChars=${fullSystemPrompt.length} units=${_uiState.value.estimatedMessageUnits}", TAG)

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
        if (conversation == null) return
        refreshPayloadCostEstimate(messages = _uiState.value.messages, pendingUserText = text)
        if (_uiState.value.estimatedMessageUnits > 1) {
            when (val pref = aiSettingsStore.getHealthContextSendPreference()) {
                HealthContextSendPreference.ASK_EACH_TIME -> {
                    _uiState.value = _uiState.value.copy(
                        inputText = "",
                        pendingSendText = text,
                        showContextModeDialog = true,
                    )
                    return
                }
                HealthContextSendPreference.FULL_ACCURACY -> {
                    _uiState.value = _uiState.value.copy(inputText = "")
                    performSend(text, HealthContextSendMode.FULL_ACCURACY)
                    return
                }
                HealthContextSendPreference.COMPACT_SUMMARY -> {
                    _uiState.value = _uiState.value.copy(inputText = "")
                    performSend(text, HealthContextSendMode.COMPACT_SUMMARY)
                    return
                }
            }
        }
        _uiState.value = _uiState.value.copy(inputText = "")
        performSend(text, HealthContextSendMode.FULL_ACCURACY)
    }

    fun cancelPendingSend() {
        _uiState.value = _uiState.value.copy(
            pendingSendText = "",
            showContextModeDialog = false,
        )
    }

    fun confirmSend(mode: HealthContextSendMode) {
        val text = _uiState.value.pendingSendText.trim()
        _uiState.value = _uiState.value.copy(
            pendingSendText = "",
            showContextModeDialog = false,
        )
        if (text.isBlank()) return
        val preference = HealthContextSendPreference.fromSendMode(mode)
        setHealthContextSendPreference(preference)
        performSend(text, mode)
    }

    fun setHealthContextSendPreference(preference: HealthContextSendPreference) {
        aiSettingsStore.setHealthContextSendPreference(preference)
        viewModelScope.launch {
            runCatching { aiRemotePrefs.setHealthContextSendPreference(preference) }
        }
    }

    private suspend fun syncHealthContextSendPreferenceFromRemote() {
        val remote = aiRemotePrefs.fetch()?.healthContextSendPreference ?: return
        if (aiSettingsStore.getHealthContextSendPreference() != remote) {
            aiSettingsStore.setHealthContextSendPreference(remote)
            KBLog.ai.info("synced healthContextSendPreference=${remote.storageValue}", TAG)
        }
    }

    private fun performSend(text: String, mode: HealthContextSendMode) {
        val conv = conversation ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val promptWithMemory = fullSystemPrompt
            val resolvedPrompt = when (mode) {
                HealthContextSendMode.FULL_ACCURACY -> null
                HealthContextSendMode.COMPACT_SUMMARY -> {
                    _uiState.value = _uiState.value.copy(isPreparingCompactContext = true)
                    val summary = resolveCompactHealthSummary(promptWithMemory).getOrElse { err ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isPreparingCompactContext = false,
                            errorMessage = err.message ?: "Impossibile riassumere il contesto.",
                        )
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(isPreparingCompactContext = false)
                    chatRepository.compactSystemPrompt(summary, displaySubjectName(), conv)
                }
            }
            chatRepository.sendMessage(conv, text, promptWithMemory, resolvedPrompt)
                .onSuccess { result ->
                    messagesInSession = result.reply.usageToday
                    dailyLimit = result.reply.dailyLimit
                    maybeCompactIfNeeded(conv)
                    conversation = chatRepository.getOrCreateConversation(familyId, childId, conv.scopeId)
                    val autoIds = if (result.didAutoExecute) {
                        _uiState.value.autoExecutedMessageIds + result.assistantMessage.id
                    } else {
                        _uiState.value.autoExecutedMessageIds
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        streamingMessageId = AIChatStreamingDelivery.beginAssistantReveal(
                            result.assistantMessage.id,
                        ),
                        usageToday = result.reply.usageToday,
                        dailyLimit = result.reply.dailyLimit,
                        quotaPeriod = result.reply.period,
                        actionExecutionSummary = result.executionSummary,
                        autoExecutedMessageIds = autoIds,
                    )
                    refreshPayloadCostEstimate(
                        messages = _uiState.value.messages,
                        pendingUserText = "",
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPreparingCompactContext = false,
                        errorMessage = err.message ?: "Errore nella comunicazione con l'AI",
                    )
                }
        }
    }

    private suspend fun resolveCompactHealthSummary(fullPrompt: String): Result<String> {
        syncCompactCacheValidity()
        compactHealthContextCache?.second?.let { return Result.success(it) }
        return chatRepository.summarizeHealthContext(familyId, fullPrompt).map { summary ->
            compactHealthContextCache = healthContextFingerprint() to summary
            summary
        }
    }

    private fun healthContextFingerprint(): Int {
        var hash = fullSystemPrompt.length
        hash = 31 * hash + standardSystemPrompt.length
        hash = 31 * hash + _uiState.value.visitsCount
        hash = 31 * hash + _uiState.value.examsCount
        return hash
    }

    private fun syncCompactCacheValidity() {
        if (compactHealthContextCache?.first != healthContextFingerprint()) {
            compactHealthContextCache = null
        }
    }

    private fun displaySubjectName(): String =
        _uiState.value.subjectName.ifBlank { subjectName }.ifBlank { "Profilo" }

    private suspend fun maybeCompactIfNeeded(conv: KBAIConversation) {
        if (!shouldCompact()) return
        val currentStep = (messagesInSession / (dailyLimit * 0.20)).toInt()
        if (currentStep <= lastCompactionStep) return
        val messagesForMemory = chatRepository.observeMessages(conv.id).first()
            .sortedBy { it.createdAtEpochMillis }
        val didCompact = chatRepository.compactConversation(conv)
        if (didCompact) {
            lastCompactionStep = currentStep
            viewModelScope.launch {
                familyMemoryService.extractAndStore(
                    familyId = familyId,
                    conversationId = conv.id,
                    transcriptMessages = messagesForMemory,
                )
            }
        }
    }

    private fun shouldCompact(): Boolean {
        if (dailyLimit <= 0) return false
        return messagesInSession.toDouble() >= dailyLimit.toDouble() * COMPACTION_THRESHOLD
    }

    fun setInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        refreshPayloadCostEstimate(
            messages = _uiState.value.messages,
            pendingUserText = text,
        )
    }

    fun dismissContextNotice() {
        _uiState.value = _uiState.value.copy(contextNoticeMessage = null)
    }

    private fun refreshPayloadCostEstimate(messages: List<KBAIMessage>, pendingUserText: String) {
        val conv = conversation ?: return
        if (fullSystemPrompt.isBlank()) return
        syncCompactCacheValidity()
        val promptWithMemory = fullSystemPrompt
        val units = chatRepository.estimatePayloadCost(
            conversation = conv,
            baseSystemPrompt = promptWithMemory,
            allMessages = messages,
            pendingUserText = pendingUserText,
        )
        val compactEstimate = chatRepository.estimateCompactPayloadCost(
            conversation = conv,
            baseSystemPrompt = promptWithMemory,
            allMessages = messages,
            pendingUserText = pendingUserText,
            subjectName = displaySubjectName(),
            compactHealthSummary = compactHealthContextCache?.second,
            healthContextFingerprint = healthContextFingerprint(),
            cachedFingerprint = compactHealthContextCache?.first,
        )
        _uiState.value = _uiState.value.copy(
            estimatedMessageUnits = units,
            estimatedCompactMessageUnits = compactEstimate.askUnits,
            estimatedCompactSetupUnits = compactEstimate.setupUnits,
            hasCompactHealthContextCache = compactHealthContextCache != null,
        )
        presentLargeContextNoticeIfNeeded(units)
    }

    private fun presentLargeContextNoticeIfNeeded(units: Int) {
        if (units <= 1 || didShowLargeContextNotice || _uiState.value.isLoadingContext) return
        didShowLargeContextNotice = true
        _uiState.value = _uiState.value.copy(
            contextNoticeMessage = AIAskAIPayload.transientLargeContextNotice(appContext),
        )
    }

    fun shouldShowLargeContextNotice(): Boolean {
        val units = _uiState.value.estimatedMessageUnits
        return units > 1 && !didShowLargeContextNotice && !_uiState.value.isLoadingContext
    }

    fun markLargeContextNoticeShown() {
        didShowLargeContextNotice = true
    }

    fun resetLargeContextNoticeOnClear() {
        didShowLargeContextNotice = false
    }

    fun sendSuggestion(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        send()
    }

    fun finishStreaming(messageId: String) {
        _uiState.value = _uiState.value.copy(
            streamingMessageId = AIChatStreamingDelivery.finishReveal(
                messageId,
                _uiState.value.streamingMessageId,
            ),
        )
    }

    fun clearActionExecutionSummary() {
        _uiState.value = _uiState.value.copy(actionExecutionSummary = null)
    }

    fun clearConversation() {
        val conv = conversation ?: return
        viewModelScope.launch {
            chatRepository.clearConversation(conv)
            lastCompactionStep = 0
            compactHealthContextCache = null
            resetLargeContextNoticeOnClear()
            _uiState.value = _uiState.value.copy(
                messages = emptyList(),
                streamingMessageId = null,
                autoExecutedMessageIds = emptySet(),
                contextNoticeMessage = null,
            )
            refreshPayloadCostEstimate(
                messages = emptyList(),
                pendingUserText = _uiState.value.inputText,
            )
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun resolveSubjectName(id: String): String {
        childDao.getById(id)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        memberDao.getAnyById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
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
