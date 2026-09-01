package it.vittorioscocca.kidbox.ui.screens.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCopilotActionExecutor
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCopilotChange
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCopilotPrompt
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanGenerator
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanRemoteStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanStore
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.notifications.FitnessPlanReminderScheduler
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FitnessCopilotMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class FitnessCopilotUiState(
    val subjectName: String = "",
    val messages: List<FitnessCopilotMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
    val sessionCount: Int = 0,
    val safetyNoteCount: Int = 0,
    val hasTodaySession: Boolean = false,
    val actionSummary: String? = null,
    val message: String? = null,
) {
    val canSend: Boolean get() = !isLoading && inputText.isNotBlank()
}

/**
 * "Fitness Copilot": chat libera dentro il modulo, con il piano e lo stato di
 * salute allegati in modo invisibile a ogni domanda.
 *
 * La conversazione vive in memoria per la durata della schermata: il suo valore
 * sta nel contesto del piano corrente, che cambia in continuazione.
 */
@HiltViewModel
class FitnessCopilotViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val planStore: FitnessPlanStore,
    private val remoteStore: FitnessPlanRemoteStore,
    private val reminderScheduler: FitnessPlanReminderScheduler,
    private val childDao: KBChildDao,
    private val treatmentDao: KBTreatmentDao,
    private val visitDao: KBMedicalVisitDao,
    private val examDao: KBMedicalExamDao,
    private val profileRepository: PediatricProfileRepository,
    private val healthLinkStore: HealthLinkStore,
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FitnessCopilotUiState())
    val uiState: StateFlow<FitnessCopilotUiState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""
    private var systemPrompt = ""
    private var plan: FitnessPlanDocument? = null

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        viewModelScope.launch { buildContext() }
    }

    fun setInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun consumeActionSummary() {
        _uiState.value = _uiState.value.copy(actionSummary = null)
    }

    fun send(text: String = _uiState.value.inputText) {
        val question = text.trim()
        if (question.isBlank() || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            inputText = "",
            isLoading = true,
            messages = _uiState.value.messages + FitnessCopilotMessage(text = question, isUser = true),
        )
        viewModelScope.launch {
            if (systemPrompt.isBlank()) buildContext()
            val current = plan
            if (current == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = context.getString(R.string.fitness_error_generic),
                )
                return@launch
            }

            // La cronologia viaggia intera: il copilota ragiona su una manciata
            // di scambi, e troncarli renderebbe incoerenti le modifiche al piano.
            val history = _uiState.value.messages.map { message ->
                KBAIMessage(
                    id = message.id,
                    conversationId = "",
                    roleRaw = if (message.isUser) "user" else "assistant",
                    content = message.text,
                    createdAtEpochMillis = message.createdAtEpochMillis,
                )
            }

            aiRepository.askAI(
                familyId = familyId,
                systemPrompt = systemPrompt,
                messages = history,
                purpose = FitnessPlanGenerator.PURPOSE_COPILOT,
            ).onSuccess { reply ->
                val processed = FitnessCopilotActionExecutor.process(reply.reply, current)
                if (processed.changes.isNotEmpty()) {
                    plan = processed.plan
                    withContext(Dispatchers.IO) {
                        planStore.save(childId, processed.plan)
                        remoteStore.upsert(childId, processed.plan)
                        reminderScheduler.reschedule(childId, familyId, processed.plan)
                    }
                    buildContext()
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    usageToday = reply.usageToday,
                    dailyLimit = reply.dailyLimit,
                    actionSummary = summary(processed.changes),
                    messages = _uiState.value.messages + FitnessCopilotMessage(
                        text = processed.displayText,
                        isUser = false,
                    ),
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = error.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.fitness_error_generic),
                )
            }
        }
    }

    private fun summary(changes: List<FitnessCopilotChange>): String? {
        if (changes.isEmpty()) return null
        return changes.joinToString(" · ") { change ->
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(Date(change.dateEpochMillis))
            when (change) {
                is FitnessCopilotChange.Replaced ->
                    context.getString(R.string.fitness_copilot_changed_replaced, date)
                is FitnessCopilotChange.Moved ->
                    context.getString(R.string.fitness_copilot_changed_moved, date)
                is FitnessCopilotChange.StatusUpdated ->
                    context.getString(R.string.fitness_copilot_changed_status, date)
            }
        }
    }

    /**
     * Costruisce il prompt di sistema una volta sola per apertura: piano,
     * avanzamento e dati sanitari viaggiano allegati a ogni domanda.
     */
    private suspend fun buildContext() {
        val current = withContext(Dispatchers.IO) { planStore.load(childId) } ?: return
        plan = current
        val child = withContext(Dispatchers.IO) { childDao.getById(childId) }
        val subjectName = child?.name ?: context.getString(R.string.health_profile)
        val payload = withContext(Dispatchers.IO) {
            val profile = profileRepository.loadOnce(childId)
            val health = healthLinkStore.load(childId)
            val treatments = treatmentDao.observeByFamilyAndChild(familyId, childId).first()
                .filter { it.isActive && !it.isDeleted && it.petId.isBlank() }
            val visits = visitDao.observeByFamilyAndChild(familyId, childId).first()
                .filter { !it.isDeleted }
            val exams = examDao.observeByFamilyAndChild(familyId, childId).first()
                .filter { !it.isDeleted }
            FitnessPlanGenerator.buildPayload(
                subjectName = subjectName,
                birthDateEpochMillis = child?.birthDateEpochMillis ?: health?.birthDateEpochMillis,
                input = current.input,
                snapshot = health,
                profile = profile,
                treatments = treatments,
                visits = visits,
                exams = exams,
                startDateEpochMillis = current.startDateEpochMillis,
            )
        }
        systemPrompt = FitnessCopilotPrompt.systemPrompt(
            subjectName = subjectName,
            plan = current,
            profileSummary = payload.profileSummary,
            healthContext = payload.healthContext,
        )
        _uiState.value = _uiState.value.copy(
            subjectName = subjectName,
            sessionCount = current.allSessions.size,
            safetyNoteCount = current.safetyNotes.size,
            hasTodaySession = current.sessionsOn(System.currentTimeMillis()).isNotEmpty(),
        )
    }
}
