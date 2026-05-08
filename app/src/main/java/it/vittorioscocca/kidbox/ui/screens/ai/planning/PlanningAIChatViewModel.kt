package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBChatMessageDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBRoutineCheckDao
import it.vittorioscocca.kidbox.data.local.dao.KBRoutineDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBChatMessageEntity
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPediatricProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineCheckEntity
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.data.repository.KBAIRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBCalendarEvent
import it.vittorioscocca.kidbox.domain.model.KBChatMessage
import it.vittorioscocca.kidbox.domain.model.KBChild
import it.vittorioscocca.kidbox.domain.model.KBDocument
import it.vittorioscocca.kidbox.domain.model.KBExpense
import it.vittorioscocca.kidbox.domain.model.KBGroceryItem
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBNote
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import it.vittorioscocca.kidbox.domain.model.KBRoutine
import it.vittorioscocca.kidbox.domain.model.KBRoutineCheck
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.KBTodoItem
import it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
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
    val isLoading: Boolean = false,
    val isLoadingContext: Boolean = false,
    val errorMessage: String? = null,
    val inputText: String = "",
    val usageToday: Int = 0,
    val dailyLimit: Int = 30,
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
) {
    val canSend: Boolean get() = inputText.isNotBlank() && !isLoading && !isLoadingContext
    val isNearLimit: Boolean get() = dailyLimit > 0 && usageToday >= (dailyLimit * 0.8).toInt()
}

@HiltViewModel
class PlanningAIChatViewModel @Inject constructor(
    private val kbAIRepository: KBAIRepository,
    private val aiService: AIService,
    private val subscriptionRepository: SubscriptionRepository,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val calendarEventDao: KBCalendarEventDao,
    private val todoItemDao: KBTodoItemDao,
    private val routineDao: KBRoutineDao,
    private val routineCheckDao: KBRoutineCheckDao,
    private val treatmentRepository: TreatmentRepository,
    private val medicalVisitRepository: MedicalVisitRepository,
    private val medicalExamRepository: MedicalExamRepository,
    private val vaccineRepository: VaccineRepository,
    private val noteDao: KBNoteDao,
    private val expenseDao: KBExpenseDao,
    private val expenseCategoryDao: KBExpenseCategoryDao,
    private val groceryItemDao: KBGroceryItemDao,
    private val chatMessageDao: KBChatMessageDao,
    private val documentDao: KBDocumentDao,
    private val walletTicketDao: WalletTicketDao,
    private val pediatricProfileDao: KBPediatricProfileDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val familyId: String = savedStateHandle["familyId"] ?: ""
    private val effectiveFamilyId: String = familyId.ifBlank {
        val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
        prefs.getString("active_family_id", null)
            ?: prefs.getString("family_id", "")
            ?: ""
    }
    private val familyName: String = savedStateHandle["familyName"] ?: ""
    private val memberNames: List<String> = savedStateHandle.get<ArrayList<String>>("memberNames")?.toList().orEmpty()
    private val horizonDays: Int = savedStateHandle["horizonDays"] ?: 14
    private val scopeId = "planning-agent-$effectiveFamilyId"

    private val _uiState = MutableStateFlow(PlanningChatUiState())
    val uiState: StateFlow<PlanningChatUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var conversation: KBAIConversation? = null
    private var systemPrompt: String = ""
    private var lastInput: PlanningContextInput? = null

    private val COMPACTION_THRESHOLD = 0.60
    private var lastCompactionStep = 0
    private var messagesInSession = 0
    private var dailyLimit = 0

    fun loadOrCreateConversation(input: PlanningContextInput) {
        lastInput = input
        viewModelScope.launch {
            Log.d("PlanningAIChatVM", "init")
            _uiState.update { it.copy(isLoadingContext = true, errorMessage = null) }
            val plan = if (effectiveFamilyId.isBlank()) {
                null
            } else {
                runCatching { subscriptionRepository.getPlan(effectiveFamilyId) }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    // Temporary business override: FREE users are elevated to AI access too.
                    // Keep paywall disabled here to match Health AI behavior.
                    isSubscribed = true,
                    dailyLimit = if (plan == KBPlan.MAX) 100 else 30,
                )
            }
            dailyLimit = _uiState.value.dailyLimit
            runCatching {
                val enrichedInput = buildContextInput(input)
                lastInput = enrichedInput
                conversation = kbAIRepository.getOrCreateConversation(scopeId, effectiveFamilyId)
                systemPrompt = PlanningContextBuilder.build(
                    enrichedInput,
                )
                observeJob?.cancel()
                observeJob = viewModelScope.launch {
                    kbAIRepository.observeMessages(conversation!!.id).collectLatest { msgs ->
                        _uiState.update { it.copy(messages = msgs) }
                    }
                }
                val pendingWeeklyRecap = WeeklySummaryDraftStore.consume(context)
                if (!pendingWeeklyRecap.isNullOrBlank()) {
                    kbAIRepository.addMessage(
                        conversationId = conversation!!.id,
                        role = AIMessageRole.ASSISTANT,
                        content = pendingWeeklyRecap,
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoadingContext = false,
                        conversationReady = true,
                        familyName = enrichedInput.familyName,
                        upcomingEventsCount = enrichedInput.calendarEvents.size,
                        pendingGroceryCount = enrichedInput.pendingGroceryItems.size,
                        todayEventsCount = enrichedInput.calendarEvents.count { event ->
                            val c = java.util.Calendar.getInstance()
                            c.timeInMillis = event.startDateEpochMillis
                            val d = java.util.Calendar.getInstance()
                            c.get(java.util.Calendar.YEAR) == d.get(java.util.Calendar.YEAR) &&
                                c.get(java.util.Calendar.DAY_OF_YEAR) == d.get(java.util.Calendar.DAY_OF_YEAR)
                        },
                        urgentTodosCount = enrichedInput.openTodos.count { todo ->
                            val isHighPriority = (todo.priorityRaw ?: 0) == 1
                            val isOverdue = (todo.dueAtEpochMillis ?: Long.MAX_VALUE) < System.currentTimeMillis()
                            isHighPriority || isOverdue
                        },
                        todayDosesCount = enrichedInput.activeTreatments.sumOf { treatment ->
                            treatment.scheduleTimesList().size
                        },
                        parserOpenTodos = enrichedInput.openTodos,
                        parserVisits = enrichedInput.visitsWithNextDate,
                        parserTreatments = enrichedInput.activeTreatments,
                        parserFamilyId = effectiveFamilyId,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingContext = false, errorMessage = err.message ?: "Errore contesto") }
            }
        }
    }

    fun send(input: PlanningContextInput) {
        lastInput = input
        val text = _uiState.value.inputText.trim()
        val conv = conversation ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, inputText = "") }
            runCatching {
                val enrichedInput = buildContextInput(input)
                lastInput = enrichedInput
                kbAIRepository.addMessage(conv.id, AIMessageRole.USER, text)
                systemPrompt = PlanningContextBuilder.build(enrichedInput)
                val payload = buildApiMessages(conversation = conv, latestUserText = text)
                val reply = aiService.sendMessage(payload, systemPrompt, effectiveFamilyId).getOrThrow()
                kbAIRepository.addMessage(conv.id, AIMessageRole.ASSISTANT, reply.reply)
                messagesInSession = reply.usageToday
                dailyLimit = reply.dailyLimit
                maybeCompactIfNeeded(conv.id, reply.usageToday, reply.dailyLimit)
                _uiState.update { it.copy(isLoading = false, usageToday = reply.usageToday, dailyLimit = reply.dailyLimit) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = localizeError(err)) }
            }
        }
    }

    fun onInputChanged(text: String) = _uiState.update { it.copy(inputText = text) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun consumeError() = clearError()
    fun setInput(text: String) = onInputChanged(text)
    fun sendSuggestion(text: String) {
        onInputChanged(text)
        send(lastInput ?: emptyPlanningContext())
    }
    fun send() = send(lastInput ?: emptyPlanningContext())
    fun bind(familyId: String, familyName: String) {
        if (!_uiState.value.conversationReady) {
            loadOrCreateConversation(lastInput ?: emptyPlanningContext())
        }
    }
    fun clearConversation() {
        val conv = conversation ?: return
        viewModelScope.launch {
            kbAIRepository.clearConversation(conv.id)
            val refreshed = kbAIRepository.getOrCreateConversation(scopeId, effectiveFamilyId)
            conversation = refreshed
            _uiState.update { it.copy(messages = emptyList(), errorMessage = null) }
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
        val summary = aiService.sendMessage(all, SUMMARY_SYSTEM_PROMPT, effectiveFamilyId).getOrThrow().reply
        kbAIRepository.clearAndSeedSummary(conversationId, summary)
        conversation = conv.copy(summary = summary, summarizedMessageCount = 0)
        lastCompactionStep = currentStep
    }

    private fun shouldCompact(): Boolean {
        if (dailyLimit <= 0) return false
        return messagesInSession.toDouble() >= dailyLimit.toDouble() * COMPACTION_THRESHOLD
    }

    private fun buildApiMessages(
        conversation: KBAIConversation,
        latestUserText: String,
    ): List<KBAIMessage> {
        val baseMessages = _uiState.value.messages
            .sortedBy { it.createdAtEpochMillis }
            .filterNot { msg ->
                val summary = conversation.summary
                summary != null && msg.roleRaw == "assistant" && msg.content == summary
            }
            .takeLast(6)
        val messages = if (baseMessages.isEmpty()) {
            // Safety net: avoid empty payload when Room/Flow update is slightly delayed.
            listOf(
                KBAIMessage(
                    id = "fallback-user-${System.currentTimeMillis()}",
                    conversationId = conversation.id,
                    roleRaw = AIMessageRole.USER.value,
                    content = latestUserText,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            baseMessages
        }

        val summary = conversation.summary?.takeIf { it.isNotBlank() } ?: return messages.take(7)
        val summaryMessage = KBAIMessage(
            id = "summary-${conversation.id}",
            conversationId = conversation.id,
            roleRaw = AIMessageRole.ASSISTANT.value,
            content = summary,
            createdAtEpochMillis = System.currentTimeMillis(),
            isSummary = true,
        )
        return (listOf(summaryMessage) + messages).take(7)
    }

    private suspend fun buildContextInput(seed: PlanningContextInput): PlanningContextInput {
        val family = if (effectiveFamilyId.isNotBlank()) familyDao.getById(effectiveFamilyId) else null
        val childrenEntities = if (effectiveFamilyId.isNotBlank()) childDao.getChildrenByFamilyId(effectiveFamilyId) else emptyList()

        val calendarEvents = if (effectiveFamilyId.isNotBlank()) {
            calendarEventDao.observeByFamilyId(effectiveFamilyId).first()
                .filterNot { it.isDeleted }
                .map { it.toDomain() }
        } else {
            emptyList()
        }

        val allTodos = childrenEntities.flatMap { child ->
            todoItemDao.getByFamilyAndChild(effectiveFamilyId, child.id)
        }.filterNot { it.isDeleted }

        val allRoutines = childrenEntities.flatMap { child ->
            routineDao.observeByFamilyAndChild(effectiveFamilyId, child.id).first()
        }.filterNot { it.isDeleted }.map { it.toDomain() }

        val allRoutineChecks = childrenEntities.flatMap { child ->
            allRoutines.filter { it.childId == child.id }.flatMap { routine ->
                routineCheckDao.observeByRoutine(effectiveFamilyId, routine.id).first()
            }
        }.filterNot { it.isDeleted }.map { it.toDomain() }

        val allTreatments = childrenEntities.flatMap { child ->
            treatmentRepository.listByFamilyAndChild(effectiveFamilyId, child.id)
        }

        val allVisits = childrenEntities.flatMap { child ->
            medicalVisitRepository.listRecentVisitsForChild(effectiveFamilyId, child.id, limit = 500)
        }

        val allExams = childrenEntities.flatMap { child ->
            medicalExamRepository.listByFamilyAndChild(effectiveFamilyId, child.id)
        }

        val allVaccines = childrenEntities.flatMap { child ->
            vaccineRepository.observe(effectiveFamilyId, child.id).first()
        }

        val notes = if (effectiveFamilyId.isNotBlank()) {
            noteDao.observeByFamilyId(effectiveFamilyId).first()
                .filterNot { it.isDeleted }
                .map { it.toDomain() }
        } else {
            emptyList()
        }

        val expenses = if (effectiveFamilyId.isNotBlank()) {
            expenseDao.getAllByFamilyId(effectiveFamilyId)
                .filterNot { it.isDeleted }
                .map { it.toDomain() }
        } else {
            emptyList()
        }

        val expenseCategoryNames = if (effectiveFamilyId.isNotBlank()) {
            expenseCategoryDao.getAllByFamilyId(effectiveFamilyId)
                .filterNot { it.isDeleted }
                .map { it.name }
        } else {
            emptyList()
        }

        val groceryItems = if (effectiveFamilyId.isNotBlank()) {
            groceryItemDao.observeByFamilyId(effectiveFamilyId).first()
                .filterNot { it.isDeleted }
                .map { it.toDomain() }
        } else {
            emptyList()
        }

        val chatMessages = if (effectiveFamilyId.isNotBlank()) {
            chatMessageDao.getAllByFamilyId(effectiveFamilyId)
                .filterNot { it.isDeleted }
                .map { it.toDomain() }
        } else {
            emptyList()
        }

        val documents = if (effectiveFamilyId.isNotBlank()) {
            documentDao.getAllByFamilyId(effectiveFamilyId)
                .filterNot { it.isDeleted }
                .map { it.toDomain() }
        } else {
            emptyList()
        }

        val pediatricProfiles = if (effectiveFamilyId.isNotBlank()) {
            pediatricProfileDao.observeByFamilyId(effectiveFamilyId).first().map { it.toDomain() }
        } else {
            emptyList()
        }

        val memberNamesResolved = if (seed.memberNames.isNotEmpty()) {
            seed.memberNames
        } else if (effectiveFamilyId.isNotBlank()) {
            familyMemberDao.observeActiveByFamilyId(effectiveFamilyId).first()
                .filterNot { it.isDeleted }
                .mapNotNull { it.displayName?.takeIf(String::isNotBlank) }
        } else {
            emptyList()
        }

        val children = childrenEntities.map { it.toDomain() }
        val now = System.currentTimeMillis()

        return seed.copy(
            familyName = seed.familyName.ifBlank { family?.name ?: familyName },
            memberNames = memberNamesResolved.ifEmpty { memberNames },
            horizonDays = if (seed.horizonDays <= 0) horizonDays else seed.horizonDays,
            calendarEvents = calendarEvents,
            openTodos = allTodos.map { it.toDomain() },
            activeRoutines = allRoutines,
            todayChecks = allRoutineChecks,
            childNames = children.map { it.name },
            activeTreatments = allTreatments.filter { it.isActive },
            visitsWithNextDate = allVisits.filter { (it.nextVisitDateEpochMillis ?: 0L) > now },
            visitsWithPendingExams = allVisits.filter {
                it.linkedExamIdsJson.isNotBlank() || !it.prescribedExamsJson.isNullOrBlank()
            },
            upcomingVaccines = allVaccines.filter { (it.scheduledDateEpochMillis ?: 0L) >= now },
            recentNotes = notes.sortedByDescending { it.updatedAtEpochMillis }.take(10),
            recentExpenses = expenses.sortedByDescending { it.dateEpochMillis }.take(50),
            expenseCategoryNames = expenseCategoryNames,
            pendingGroceryItems = groceryItems.filter { !it.isPurchased },
            recentChatMessages = chatMessages.sortedByDescending { it.createdAtEpochMillis }.take(15),
            recentDocuments = documents.sortedByDescending { it.updatedAtEpochMillis }.take(10),
            recentWalletTickets = if (effectiveFamilyId.isNotBlank()) walletTicketDao.getActiveByFamilyId(effectiveFamilyId).take(10) else emptyList(),
            children = children,
            pediatricProfiles = pediatricProfiles,
            allVisits = allVisits,
            allExams = allExams,
            allVaccines = allVaccines,
        )
    }

    private fun localizeError(error: Throwable): String {
        val mapped = (error as? AIServiceException)?.serviceError
        return when (mapped) {
            AIServiceError.RateLimitReached -> "Limite giornaliero raggiunto."
            AIServiceError.NetworkError -> "Errore di rete."
            is AIServiceError.ServerError -> mapped.message
            null -> error.message ?: "Errore inatteso."
        }
    }

    companion object {
        private const val SUMMARY_SYSTEM_PROMPT =
            "Riassumi in modo conciso ma completo la conversazione seguente, mantenendo i punti chiave, le decisioni prese e il contesto importante. Il riassunto sarà usato come contesto per continuare la conversazione."
    }
}

private fun KBCalendarEventEntity.toDomain() = KBCalendarEvent(
    id = id,
    familyId = familyId,
    childId = childId,
    title = title,
    notes = notes,
    location = location,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    isAllDay = isAllDay,
    categoryRaw = categoryRaw,
    recurrenceRaw = recurrenceRaw,
    reminderMinutes = reminderMinutes,
    linkedHealthItemId = linkedHealthItemId,
    linkedHealthItemType = linkedHealthItemType,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun KBTodoItemEntity.toDomain() = KBTodoItem(
    id = id,
    familyId = familyId,
    childId = childId,
    title = title,
    notes = notes,
    dueAtEpochMillis = dueAtEpochMillis,
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

private fun KBRoutineEntity.toDomain() = KBRoutine(
    id = id,
    familyId = familyId,
    childId = childId,
    title = title,
    isActive = isActive,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    isDeleted = isDeleted,
)

private fun KBRoutineCheckEntity.toDomain() = KBRoutineCheck(
    id = id,
    familyId = familyId,
    childId = childId,
    routineId = routineId,
    dayKey = dayKey,
    checkedAtEpochMillis = checkedAtEpochMillis,
    checkedBy = checkedBy,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    isDeleted = isDeleted,
)

private fun KBNoteEntity.toDomain() = KBNote(
    id = id,
    familyId = familyId,
    title = title,
    body = body,
    createdBy = createdBy,
    createdByName = createdByName,
    updatedBy = updatedBy,
    updatedByName = updatedByName,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isDeleted = isDeleted,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun KBExpenseEntity.toDomain() = KBExpense(
    id = id,
    familyId = familyId,
    title = title,
    amount = amount,
    dateEpochMillis = dateEpochMillis,
    categoryId = categoryId,
    notes = notes,
    attachedDocumentId = attachedDocumentId,
    receiptThumbnailData = receiptThumbnailData,
    createdByUid = createdByUid,
    updatedBy = updatedBy,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isDeleted = isDeleted,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun KBGroceryItemEntity.toDomain() = KBGroceryItem(
    id = id,
    familyId = familyId,
    name = name,
    category = category,
    notes = notes,
    isPurchased = isPurchased,
    purchasedAtEpochMillis = purchasedAtEpochMillis,
    purchasedBy = purchasedBy,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun KBChatMessageEntity.toDomain() = KBChatMessage(
    id = id,
    familyId = familyId,
    senderId = senderId,
    senderName = senderName,
    typeRaw = typeRaw,
    text = text,
    latitude = latitude,
    longitude = longitude,
    mediaStoragePath = mediaStoragePath,
    mediaURL = mediaURL,
    mediaDurationSeconds = mediaDurationSeconds,
    mediaThumbnailURL = mediaThumbnailURL,
    replyToId = replyToId,
    mediaLocalPath = mediaLocalPath,
    mediaFileSize = mediaFileSize,
    mediaGroupURLsJSON = mediaGroupURLsJSON,
    mediaGroupTypesJSON = mediaGroupTypesJSON,
    contactPayloadJSON = contactPayloadJSON,
    reactionsJSON = reactionsJSON,
    readByJSON = readByJSON,
    deletedForJSON = deletedForJSON,
    transcriptText = transcriptText,
    transcriptStatusRaw = transcriptStatusRaw,
    transcriptSourceRaw = transcriptSourceRaw,
    transcriptLocaleIdentifier = transcriptLocaleIdentifier,
    transcriptIsFinal = transcriptIsFinal,
    transcriptUpdatedAtEpochMillis = transcriptUpdatedAtEpochMillis,
    transcriptErrorMessage = transcriptErrorMessage,
    createdAtEpochMillis = createdAtEpochMillis,
    editedAtEpochMillis = editedAtEpochMillis,
    isDeleted = isDeleted,
    isDeletedForEveryone = isDeletedForEveryone,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun KBDocumentEntity.toDomain() = KBDocument(
    id = id,
    familyId = familyId,
    childId = childId,
    categoryId = categoryId,
    localPath = localPath,
    title = title,
    fileName = fileName,
    mimeType = mimeType,
    fileSize = fileSize,
    storagePath = storagePath,
    downloadURL = downloadURL,
    notes = notes,
    extractedText = extractedText,
    extractedTextUpdatedAtEpochMillis = extractedTextUpdatedAtEpochMillis,
    extractionStatusRaw = extractionStatusRaw,
    extractionError = extractionError,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    isDeleted = isDeleted,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun KBChildEntity.toDomain() = KBChild(
    id = id,
    familyId = familyId,
    name = name,
    birthDateEpochMillis = birthDateEpochMillis,
    weightKg = weightKg,
    heightCm = heightCm,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedBy = updatedBy,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun KBPediatricProfileEntity.toDomain() = KBPediatricProfile(
    id = id,
    familyId = familyId,
    childId = childId,
    emergencyContactsJson = emergencyContactsJson,
    bloodGroup = bloodGroup,
    allergies = allergies,
    medicalNotes = medicalNotes,
    doctorName = doctorName,
    doctorPhone = doctorPhone,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

private fun emptyPlanningContext() = PlanningContextInput(
    familyName = "",
    memberNames = emptyList(),
    calendarEvents = emptyList(),
    openTodos = emptyList(),
    activeRoutines = emptyList(),
    todayChecks = emptyList(),
    childNames = emptyList(),
    activeTreatments = emptyList(),
    visitsWithNextDate = emptyList(),
    visitsWithPendingExams = emptyList(),
    upcomingVaccines = emptyList(),
    recentNotes = emptyList(),
    recentExpenses = emptyList(),
    expenseCategoryNames = emptyList(),
    pendingGroceryItems = emptyList(),
    recentChatMessages = emptyList(),
    recentDocuments = emptyList(),
    recentWalletTickets = emptyList(),
    children = emptyList(),
    pediatricProfiles = emptyList(),
    allVisits = emptyList(),
    allExams = emptyList(),
    allVaccines = emptyList(),
)
