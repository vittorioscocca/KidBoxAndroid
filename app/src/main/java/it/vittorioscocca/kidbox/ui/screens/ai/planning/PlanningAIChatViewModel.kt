package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.ai.AISettingsStore
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendMode
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.remote.ai.AIRemotePreferences
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.data.repository.KBAIRepository
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.calendar.occurrencesIn
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTodoItem
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatStreamingDelivery
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanningChatUiState(
    val messages: List<KBAIMessage> = emptyList(),
    val streamingMessageId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingContext: Boolean = false,
    val errorMessage: String? = null,
    val inputText: String = "",
    val usageToday: Int = 0,
    val dailyLimit: Int = 30,
    val quotaPeriod: AIQuotaPeriod = AIQuotaPeriod.DAILY,
    val isSubscribed: Boolean = true,
    val conversationReady: Boolean = false,
    val familyName: String = "",
    val upcomingEventsCount: Int = 0,
    val pendingGroceryCount: Int = 0,
    val todayEventsCount: Int = 0,
    val urgentTodosCount: Int = 0,
    val todayDosesCount: Int = 0,
    val parserOpenTodos: List<KBTodoItem> = emptyList(),
    val parserVisits: List<KBMedicalVisit> = emptyList(),
    val parserTreatments: List<KBTreatment> = emptyList(),
    val parserFamilyId: String = "",
    val actionExecutionSummary: String? = null,
    val autoExecutedMessageIds: Set<String> = emptySet(),
    /** Da dove si è aperto l'assistente (pulsanti di Salute); null = dalla Home. */
    val focus: AgentFocus? = null,
    /** Quaderno più grande di un messaggio: la stessa scelta della chat Salute. */
    val showContextModeDialog: Boolean = false,
    val pendingSendText: String = "",
    val choiceFullUnits: Int = 1,
    val choiceReducedUnits: Int = 1,
) {
    val canSend: Boolean get() = inputText.isNotBlank() && !isLoading && !isLoadingContext
    val isNearLimit: Boolean get() = dailyLimit > 0 && usageToday >= (dailyLimit * 0.8).toInt()
}

/**
 * L'assistente unico di KidBox: si apre dalla Home e, con un focus, dai pulsanti
 * di Salute (persona, visite, singola visita, esami).
 *
 * - Il contesto è il quaderno di schede ([AgentMemoryBook]), ricostruito dai dati
 *   di Room a ogni domanda.
 * - Una sola conversazione per famiglia (`planning-agent-{familyId}`).
 * - Quaderno più grande di un messaggio: stessa scelta della chat Salute
 *   (`HealthContextSendPreference`); la versione ridotta non costa un riassunto.
 *
 * Disegno in `internal/assistente-unico.md`.
 */
@HiltViewModel
class PlanningAIChatViewModel @Inject constructor(
    private val kbAIRepository: KBAIRepository,
    private val actionPipeline: KidBoxAIActionPipeline,
    private val aiService: AIService,
    private val subscriptionRepository: SubscriptionRepository,
    private val familyDao: KBFamilyDao,
    private val familySessionPreferences: FamilySessionPreferences,
    private val groceryItemDao: KBGroceryItemDao,
    private val agentMemoryRepository: AgentMemoryRepository,
    private val healthAttachmentService: HealthAttachmentService,
    private val familyMemoryService: FamilyMemoryService,
    private val aiSettingsStore: AISettingsStore,
    private val aiRemotePrefs: AIRemotePreferences,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val routeFamilyId: String = savedStateHandle["familyId"] ?: ""
    private var effectiveFamilyId: String = routeFamilyId.ifBlank {
        familySessionPreferences.getActiveFamilyId().orEmpty()
    }
    private var scopeId: String = "planning-agent-$effectiveFamilyId"
    private val routeFamilyName: String = savedStateHandle["familyName"] ?: ""

    private val _uiState = MutableStateFlow(PlanningChatUiState(focus = focusFrom(savedStateHandle)))
    val uiState: StateFlow<PlanningChatUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var conversation: KBAIConversation? = null
    private var snapshot: AgentMemorySnapshot? = null
    private var pendingPlan: ContextPlan? = null

    private val COMPACTION_THRESHOLD = 0.60
    /** I contatori in [PlanningChatUiState] vengono dal server (non sono i default). */
    private var usageKnown = false
    private var lastCompactionStep = 0
    private var messagesInSession = 0
    private var dailyLimit = 0

    init {
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                val resolved = ActiveFamilyResolver.resolveFamilyId(
                    families,
                    familySessionPreferences.getActiveFamilyId(),
                ).ifBlank { routeFamilyId }
                if (resolved.isBlank() || resolved == effectiveFamilyId) return@collectLatest
                rebindActiveFamily(resolved)
            }
        }
    }

    private fun rebindActiveFamily(familyId: String) {
        observeJob?.cancel()
        conversation = null
        snapshot = null
        effectiveFamilyId = familyId
        scopeId = "planning-agent-$familyId"
        usageKnown = false
        _uiState.value = PlanningChatUiState(focus = _uiState.value.focus)
        loadOrCreateConversation()
    }

    fun loadOrCreateConversation() {
        if (_uiState.value.isLoadingContext || _uiState.value.conversationReady) return
        viewModelScope.launch {
            KBLog.ai.debug("init focus=${_uiState.value.focus?.scope}", TAG)
            _uiState.update { it.copy(isLoadingContext = true, errorMessage = null) }
            val plan = if (effectiveFamilyId.isBlank()) {
                null
            } else {
                runCatching { subscriptionRepository.getPlan(effectiveFamilyId) }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    // Free ha accesso all'AI finché non esaurisce il bonus di 5 messaggi
                    // una tantum: il blocco è reattivo (CurrentPlanStore.aiAccessBlocked),
                    // non più legato staticamente al piano.
                    isSubscribed = !CurrentPlanStore.aiAccessBlocked.value,
                    dailyLimit = plan?.aiMessageLimit ?: 30,
                    quotaPeriod = plan?.aiQuotaPeriod ?: AIQuotaPeriod.DAILY,
                )
            }
            dailyLimit = _uiState.value.dailyLimit
            // Quanti messaggi restano: decide se il contesto ridotto parte da solo.
            if (effectiveFamilyId.isNotBlank()) {
                aiService.fetchUsage(effectiveFamilyId).onSuccess { usage ->
                    usageKnown = true
                    _uiState.update {
                        it.copy(usageToday = usage.usageToday, dailyLimit = usage.dailyLimit, quotaPeriod = usage.period)
                    }
                }
            }
            runCatching {
                conversation = kbAIRepository.getOrCreateConversation(scopeId, effectiveFamilyId)
                val snap = loadSnapshot()
                observeJob?.cancel()
                observeJob = viewModelScope.launch {
                    kbAIRepository.observeMessages(conversation!!.id).collectLatest { msgs ->
                        _uiState.update { it.copy(messages = msgs) }
                    }
                }
                // Il briefing e gli insight sono della Home: aperto da Salute
                // l'assistente non li mette davanti alla domanda sulla persona.
                if (_uiState.value.focus == null) injectPendingRecapFromStores()
                enqueuePendingExtractions()
                _uiState.update { it.copy(isLoadingContext = false, conversationReady = true).withSnapshot(snap) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingContext = false, errorMessage = err.message ?: "Errore contesto") }
            }
        }
    }

    /** Inietta briefing/recap da tap notifica (anche se la chat era già aperta). */
    fun injectPendingRecapFromStores() {
        val conv = conversation ?: return
        viewModelScope.launch {
            val pendingRecap = HealthPatternDraftStore.consume(context)
                ?: WeeklySummaryDraftStore.consume(context)
                ?: DailyBriefingDraftStore.consume(context)
            if (!pendingRecap.isNullOrBlank()) {
                kbAIRepository.addMessage(
                    conversationId = conv.id,
                    role = AIMessageRole.ASSISTANT,
                    content = pendingRecap,
                )
            }
        }
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading || conversation == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", errorMessage = null, isLoading = true) }
            val plan = runCatching { prepareContext(text) }.getOrElse { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Errore contesto") }
                return@launch
            }
            val reduced = plan.reducedPrompt
            if (reduced == null || plan.fullUnits <= 1) {
                performSend(text, plan.fullPrompt, "full")
                return@launch
            }
            if (mustUseReduced(plan.fullUnits)) {
                performSend(text, reduced, "reduced-auto")
                return@launch
            }
            when (aiSettingsStore.getHealthContextSendPreference()) {
                HealthContextSendPreference.ASK_EACH_TIME -> {
                    pendingPlan = plan
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showContextModeDialog = true,
                            pendingSendText = text,
                            choiceFullUnits = plan.fullUnits,
                            choiceReducedUnits = plan.reducedUnits,
                        )
                    }
                }
                HealthContextSendPreference.FULL_ACCURACY -> performSend(text, plan.fullPrompt, "full")
                HealthContextSendPreference.COMPACT_SUMMARY -> performSend(text, reduced, "reduced")
            }
        }
    }

    /**
     * Contesto ridotto senza chiedere, qualunque sia la preferenza: sul Free, dove
     * i messaggi sono 5 in tutto, e sugli altri piani quando il completo costerebbe
     * più dei messaggi rimasti (il server lo rifiuterebbe per intero). Stessa
     * regola su iOS e web.
     */
    private fun mustUseReduced(fullUnits: Int): Boolean {
        val s = _uiState.value
        if (s.quotaPeriod == AIQuotaPeriod.LIFETIME) return true
        if (!usageKnown || s.dailyLimit <= 0) return false
        return s.dailyLimit - s.usageToday < fullUnits
    }

    /** Scelta dal dialogo: diventa la preferenza, come nella chat Salute. */
    fun confirmSend(mode: HealthContextSendMode) {
        val plan = pendingPlan ?: return
        val text = _uiState.value.pendingSendText
        pendingPlan = null
        _uiState.update { it.copy(showContextModeDialog = false, pendingSendText = "", isLoading = true) }
        if (text.isBlank()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val preference = HealthContextSendPreference.fromSendMode(mode)
        aiSettingsStore.setHealthContextSendPreference(preference)
        viewModelScope.launch {
            runCatching { aiRemotePrefs.setHealthContextSendPreference(preference) }
            when (mode) {
                HealthContextSendMode.FULL_ACCURACY -> performSend(text, plan.fullPrompt, "full")
                HealthContextSendMode.COMPACT_SUMMARY -> performSend(text, plan.reducedPrompt ?: plan.fullPrompt, "reduced")
            }
        }
    }

    /** Annullato: la domanda torna nel campo, non si perde. */
    fun cancelPendingSend() {
        val text = _uiState.value.pendingSendText
        pendingPlan = null
        _uiState.update {
            it.copy(
                showContextModeDialog = false,
                pendingSendText = "",
                inputText = if (it.inputText.isBlank()) text else it.inputText,
            )
        }
    }

    private suspend fun performSend(text: String, systemPrompt: AgentSystemPrompt, mode: String) {
        val conv = conversation ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching {
            KBLog.ai.debug("send mode=$mode promptChars=${systemPrompt.length}", TAG)
            val userMessage = kbAIRepository.addMessage(conv.id, AIMessageRole.USER, text)
            // La domanda entra esplicitamente: lo stato osservato da Room potrebbe
            // non averla ancora, e uno storico che finisce con l'assistente perde la domanda.
            val payload = buildApiMessages(conversation = conv, pendingUser = userMessage)
            val reply = aiService.sendMessage(
                payload,
                systemPrompt.volatile,
                effectiveFamilyId,
                purpose = AGENT_PURPOSE,
                systemPromptStable = systemPrompt.stable,
                systemPromptTail = systemPrompt.tail,
            ).getOrThrow()
            val outcome = actionPipeline.processReply(
                reply = reply.reply,
                familyId = effectiveFamilyId,
                defaultChildId = defaultChildId(),
            )
            val assistantMsg = kbAIRepository.addMessage(conv.id, AIMessageRole.ASSISTANT, outcome.displayText)
            val autoExecutedIds = if (outcome.didAutoExecute) {
                _uiState.value.autoExecutedMessageIds + assistantMsg.id
            } else {
                _uiState.value.autoExecutedMessageIds
            }
            // Con il focus di Salute vale come la vecchia chat Salute: la serie
            // dell'evento resta confrontabile con quella di prima.
            AppAnalytics.aiMessageSent(
                context,
                if (_uiState.value.focus == null) "assistente" else "salute",
                CurrentPlanStore.plan.value.rawValue,
            )
            messagesInSession = reply.usageToday
            dailyLimit = reply.dailyLimit
            usageKnown = true
            maybeCompactIfNeeded(conv.id, reply.usageToday, reply.dailyLimit)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    streamingMessageId = AIChatStreamingDelivery.beginAssistantReveal(assistantMsg.id),
                    usageToday = reply.usageToday,
                    dailyLimit = reply.dailyLimit,
                    quotaPeriod = reply.period,
                    // Il bonus Free può esaurirsi proprio con questo messaggio: rifletti
                    // subito lo stato aggiornato di CurrentPlanStore.
                    isSubscribed = !CurrentPlanStore.aiAccessBlocked.value,
                    actionExecutionSummary = outcome.executionSummary,
                    autoExecutedMessageIds = autoExecutedIds,
                    pendingGroceryCount = groceryItemDao.observeByFamilyId(effectiveFamilyId).first()
                        .count { g -> !g.isPurchased && !g.isDeleted },
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isLoading = false, errorMessage = localizeError(err)) }
        }
    }

    /** Il figlio a cui attribuire un to-do creato dall'assistente: quello del focus, se è un figlio. */
    private fun defaultChildId(): String? {
        val children = snapshot?.children.orEmpty()
        val focusId = _uiState.value.focus?.personId
        return children.firstOrNull { it.id == focusId }?.id ?: children.firstOrNull()?.id
    }

    // ── Contesto ─────────────────────────────────────────────────────────────

    private data class ContextPlan(
        val fullPrompt: AgentSystemPrompt,
        val fullUnits: Int,
        /** null se la versione completa sta già in un messaggio. */
        val reducedPrompt: AgentSystemPrompt?,
        val reducedUnits: Int,
    )

    /** Quaderno completo e, se non sta in un messaggio, quello ridotto per questa domanda. */
    private suspend fun prepareContext(question: String): ContextPlan {
        val snap = loadSnapshot()
        val familyName = snap.familyName
        val focus = _uiState.value.focus
        val builder = AgentMemoryBookBuilder(snap)
        val history = conversation?.let { buildApiMessages(it, pendingUser = null) }.orEmpty()

        val fullPrompt = AgentPrompt.systemPrompt(familyName, builder.build(docAllowance = null), focus)
        val fullChars = AIAskAIPayload.totalChars(fullPrompt.length, history, question)
        val fullUnits = AIAskAIPayload.messageUnits(fullChars)
        if (fullUnits <= 1) {
            KBLog.ai.debug("context full chars=$fullChars units=1", TAG)
            return ContextPlan(fullPrompt, 1, null, 1)
        }

        // Ridotto: si misura lo scheletro (tutti i testi a zero) e si divide il resto
        // del messaggio fra i documenti, i più utili per primi.
        val textDocs = builder.textDocuments()
        val zero = textDocs.associate { it.doc.id to 0 }
        val skeleton = AgentPrompt.systemPrompt(familyName, builder.build(docAllowance = zero), focus)
        val skeletonChars = AIAskAIPayload.totalChars(skeleton.length, history, question)
        // Di solito lo scheletro sta in un messaggio e il ridotto costa 1. Se già lo
        // scheletro non ci sta (02/10/2026: una famiglia con 30 esami e 88 documenti,
        // 50.449 caratteri senza un rigo di testo letto), il ridotto paga i messaggi
        // che servono allo scheletro e li riempie di testi.
        val target = AIAskAIPayload.messageUnits(skeletonChars + UNIT_SAFETY_MARGIN) * AIAskAIPayload.STANDARD_CHARS -
            UNIT_SAFETY_MARGIN
        val personNames = snap.children.associate { it.id to it.name } +
            snap.members.mapNotNull { m -> m.displayName?.takeIf { it.isNotBlank() }?.let { m.userId to it } }
        var reducedPrompt: AgentSystemPrompt? = null
        var reducedChars = 0

        // Base + appendice: nelle schede ogni testo ha una base che dipende solo
        // dai dati, uguale per ogni domanda, così resta nella cache; i testi
        // scelti per la domanda vanno in coda, in domanda.md. Misurato il
        // 05/10/2026: col budget diviso per domanda le due domande condividevano
        // 2.165 caratteri e la cache non serviva mai. Stesso giro su iOS e web.
        val base = AgentContextFitter.baseAllowances(textDocs, skeleton.stable.length, UNIT_SAFETY_MARGIN)
        if (base != null) {
            val withBase = AgentPrompt.systemPrompt(familyName, builder.build(docAllowance = base), focus)
            var budget = target - AIAskAIPayload.totalChars(withBase.length, history, question) -
                AgentContextFitter.APPENDIX_OVERHEAD
            var attempt = 0
            while (budget >= 0 && attempt < 3) {
                attempt += 1
                val appendix = AgentContextFitter.allowances(
                    textDocs, question, focus, personNames, budget, targetedOnly = true, floor = base,
                )
                val prompt = AgentPrompt.systemPrompt(familyName, builder.build(docAllowance = base, appendix = appendix), focus)
                reducedPrompt = prompt
                reducedChars = AIAskAIPayload.totalChars(prompt.length, history, question)
                if (reducedChars <= target || budget == 0) break
                budget = (budget - (reducedChars - target)).coerceAtLeast(0)
            }
            if (reducedChars > target) reducedPrompt = null
        }

        // Senza spazio per la base (storico lungo, scheletro enorme): il budget si
        // divide per domanda come prima, e la parte dei testi esce dalla cache.
        if (reducedPrompt == null) {
            var available = (target - skeletonChars).coerceAtLeast(0)
            reducedPrompt = skeleton
            reducedChars = skeletonChars
            // Il contorno stimato per documento non basta quando un allegato ha molte
            // righe (ognuna indentata): se sfora, lo sforamento esce dal budget e si
            // ridistribuisce. Stesso giro su iOS e web.
            for (attempt in 0 until 3) {
                val allowance = AgentContextFitter.allowances(textDocs, question, focus, personNames, available)
                val prompt = AgentPrompt.systemPrompt(familyName, builder.build(docAllowance = allowance), focus)
                reducedPrompt = prompt
                reducedChars = AIAskAIPayload.totalChars(prompt.length, history, question)
                if (reducedChars <= target || available == 0) break
                available = (available - (reducedChars - target)).coerceAtLeast(0)
            }
        }
        val reducedUnits = AIAskAIPayload.messageUnits(reducedChars)
        KBLog.ai.debug(
            "context full chars=$fullChars units=$fullUnits reduced units=$reducedUnits docs=${textDocs.size}",
            TAG,
        )
        // Un ridotto che costa quanto il completo non è una scelta: si manda il completo.
        if (reducedUnits >= fullUnits) return ContextPlan(fullPrompt, fullUnits, null, fullUnits)
        return ContextPlan(fullPrompt, fullUnits, reducedPrompt, reducedUnits)
    }

    private suspend fun loadSnapshot(): AgentMemorySnapshot {
        val familyName = routeFamilyName.ifBlank {
            if (effectiveFamilyId.isNotBlank()) familyDao.getById(effectiveFamilyId)?.name.orEmpty() else ""
        }.ifBlank { "Famiglia" }
        val snap = agentMemoryRepository.load(effectiveFamilyId, familyName)
        snapshot = snap
        _uiState.update { it.withSnapshot(snap) }
        return snap
    }

    private fun PlanningChatUiState.withSnapshot(snap: AgentMemorySnapshot): PlanningChatUiState {
        val now = System.currentTimeMillis()
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + 24L * 60 * 60 * 1000
        return copy(
            familyName = snap.familyName,
            upcomingEventsCount = snap.events.count { it.startDateEpochMillis >= now },
            pendingGroceryCount = snap.pendingGrocery.size,
            todayEventsCount = snap.events.sumOf { e -> e.occurrencesIn(startOfDay, endOfDay).size },
            urgentTodosCount = snap.openTodos.count { t ->
                (t.priorityRaw ?: 0) == 1 || (t.dueAtEpochMillis ?: Long.MAX_VALUE) < now
            },
            todayDosesCount = snap.activeTreatments.sumOf { t -> t.scheduleTimesList().size },
            parserOpenTodos = snap.openTodos.map { it.toDomain() },
            parserVisits = snap.allVisits.filter { (it.nextVisitDateEpochMillis ?: 0L) > now },
            parserTreatments = snap.activeTreatments,
            parserFamilyId = snap.familyId,
        )
    }

    /**
     * Fa leggere (OCR) i documenti ancora senza testo: salute e allegati di casa,
     * auto e animali col recupero di sempre, più pochi documenti generici alla
     * volta. I documenti d'identità del Wallet mai.
     */
    private fun enqueuePendingExtractions() {
        if (effectiveFamilyId.isBlank()) return
        healthAttachmentService.enqueueBackfillHealthExtraction(effectiveFamilyId)
        healthAttachmentService.enqueueGeneralDocumentsExtraction(effectiveFamilyId, maxDocs = EXTRACTION_BATCH_SIZE)
    }

    fun clearFocus() = _uiState.update { it.copy(focus = null) }

    fun executeCardAction(action: PlanningAction) {
        viewModelScope.launch {
            val dto = when (action.kind) {
                PlanningActionKind.CREATE_GROCERY -> PlanningExecutableActionDto(
                    type = "grocery_add",
                    items = action.groceryItems,
                )
                PlanningActionKind.CREATE_TODO -> PlanningExecutableActionDto(
                    type = "todo_add",
                    title = action.prefilledTodoTitle ?: action.title,
                )
                PlanningActionKind.CREATE_NOTE -> PlanningExecutableActionDto(
                    type = "note_add",
                    title = action.title,
                    body = action.noteBody ?: action.title,
                )
                PlanningActionKind.CREATE_EVENT -> PlanningExecutableActionDto(
                    type = "event_add",
                    title = action.prefilledEventTitle ?: action.title,
                    startAt = java.time.Instant.ofEpochMilli(System.currentTimeMillis() + 86_400_000L).toString(),
                )
                else -> return@launch
            }
            val summary = actionPipeline.executeActions(
                familyId = effectiveFamilyId,
                actions = listOf(dto),
                defaultChildId = defaultChildId(),
            )
            _uiState.update { it.copy(actionExecutionSummary = summary) }
        }
    }

    fun clearActionExecutionSummary() = _uiState.update { it.copy(actionExecutionSummary = null) }

    fun finishStreaming(messageId: String) {
        _uiState.update {
            it.copy(
                streamingMessageId = AIChatStreamingDelivery.finishReveal(messageId, it.streamingMessageId),
            )
        }
    }

    fun onInputChanged(text: String) = _uiState.update { it.copy(inputText = text) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun consumeError() = clearError()
    fun setInput(text: String) = onInputChanged(text)
    fun sendSuggestion(text: String) {
        onInputChanged(text)
        send()
    }
    fun bind(familyId: String, familyName: String) {
        if (!_uiState.value.conversationReady) loadOrCreateConversation()
    }
    fun clearConversation() {
        val conv = conversation ?: return
        viewModelScope.launch {
            kbAIRepository.clearConversation(conv.id)
            val refreshed = kbAIRepository.getOrCreateConversation(scopeId, effectiveFamilyId)
            conversation = refreshed
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    errorMessage = null,
                    streamingMessageId = null,
                    autoExecutedMessageIds = emptySet(),
                )
            }
        }
    }

    private suspend fun maybeCompactIfNeeded(
        conversationId: String,
        messagesInSession: Int,
        dailyLimit: Int,
    ) {
        this.messagesInSession = messagesInSession
        this.dailyLimit = dailyLimit
        if (!shouldCompact()) return
        val stepBase = dailyLimit * 0.20
        if (stepBase <= 0.0) return
        val currentStep = (messagesInSession / stepBase).toInt()
        if (currentStep <= lastCompactionStep) return
        val conv = conversation ?: return
        val all = kbAIRepository.observeMessages(conversationId).first().sortedBy { it.createdAtEpochMillis }
        if (all.isEmpty()) return
        val messagesForMemory = all
        val summary = aiService.sendMessage(all, SUMMARY_SYSTEM_PROMPT, effectiveFamilyId).getOrThrow().reply
        kbAIRepository.clearAndSeedSummary(conversationId, summary)
        conversation = conv.copy(summary = summary, summarizedMessageCount = 0)
        lastCompactionStep = currentStep
        viewModelScope.launch {
            familyMemoryService.extractAndStore(
                familyId = effectiveFamilyId,
                conversationId = conversationId,
                transcriptMessages = messagesForMemory,
            )
        }
    }

    private fun shouldCompact(): Boolean {
        if (dailyLimit <= 0) return false
        return messagesInSession.toDouble() >= dailyLimit.toDouble() * COMPACTION_THRESHOLD
    }

    /**
     * Ultimi 6 messaggi (più l'eventuale riassunto), con [pendingUser] in coda se
     * c'è. Senza [pendingUser] serve alla stima del costo prima di salvare la domanda.
     */
    private fun buildApiMessages(
        conversation: KBAIConversation,
        pendingUser: KBAIMessage?,
    ): List<KBAIMessage> {
        val summary = conversation.summary?.takeIf { it.isNotBlank() }
        val base = _uiState.value.messages
            .filterNot { it.id == pendingUser?.id }
            .filterNot { msg -> summary != null && msg.roleRaw == "assistant" && msg.content == summary }
            .sortedBy { it.createdAtEpochMillis }
        val recent = (base + listOfNotNull(pendingUser)).takeLast(6)
        if (summary == null) return recent
        val summaryMessage = KBAIMessage(
            id = "summary-${conversation.id}",
            conversationId = conversation.id,
            roleRaw = AIMessageRole.ASSISTANT.value,
            content = summary,
            createdAtEpochMillis = System.currentTimeMillis(),
            isSummary = true,
        )
        return (listOf(summaryMessage) + recent).take(7)
    }

    private fun localizeError(error: Throwable): String {
        val mapped = (error as? AIServiceException)?.serviceError
        return when (mapped) {
            AIServiceError.RateLimitReached -> if (_uiState.value.quotaPeriod == AIQuotaPeriod.LIFETIME) {
                "Hai usato tutti i messaggi AI gratuiti inclusi nel piano Free. Passa a Pro per continuare a usare l'assistente."
            } else {
                error.message ?: "Limite giornaliero raggiunto."
            }
            AIServiceError.NetworkError -> "Errore di rete."
            is AIServiceError.ServerError -> mapped.message
            null -> error.message ?: "Errore inatteso."
        }
    }

    companion object {
        private const val TAG = "PlanningAIChatVM"
        /** Riconosce l'assistente in log e analytics del server (`askAI`). */
        private const val AGENT_PURPOSE = "familyAgent"
        /** Margine sotto i 50.000 caratteri di un messaggio, per le righe di contorno. */
        private const val UNIT_SAFETY_MARGIN = 1_500
        /** Documenti generici da far leggere a ogni apertura: le letture successive al giro dopo. */
        private const val EXTRACTION_BATCH_SIZE = 5
        private const val SUMMARY_SYSTEM_PROMPT =
            "Riassumi in modo conciso ma completo la conversazione seguente, mantenendo i punti chiave, le decisioni prese e il contesto importante. Il riassunto sarà usato come contesto per continuare la conversazione."

        /** Il focus passato dalla rotta (`AppDestination.AiChat`), se c'è. */
        private fun focusFrom(handle: SavedStateHandle): AgentFocus? {
            val personId = handle.get<String>("focusPersonId")?.takeIf { it.isNotBlank() } ?: return null
            val scope = AgentFocus.Scope.fromRaw(handle.get<String>("focusScope")) ?: AgentFocus.Scope.PERSON
            return AgentFocus(
                personId = personId,
                personName = handle.get<String>("focusName").orEmpty(),
                scope = scope,
                itemId = handle.get<String>("focusItemId")?.takeIf { it.isNotBlank() },
                detail = handle.get<String>("focusDetail")?.takeIf { it.isNotBlank() },
            )
        }
    }
}

private fun KBTodoItemEntity.toDomain() = KBTodoItem(
    id = id,
    familyId = familyId,
    childId = childId,
    title = title,
    notes = notes,
    dueAtEpochMillis = dueAtEpochMillis,
    dueHasTime = dueHasTime,
    isDone = isDone,
    doneAtEpochMillis = doneAtEpochMillis,
    doneBy = doneBy,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    isDeleted = isDeleted,
    listId = listId,
    reminderEnabled = reminderEnabled,
    reminderId = reminderId,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
    assignedTo = assignedTo,
    createdBy = createdBy,
    priorityRaw = priorityRaw,
)
