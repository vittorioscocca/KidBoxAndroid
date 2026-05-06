package it.vittorioscocca.kidbox.ui.screens.ai.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.repository.PlanningAIChatRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class PlanningAIChatState(
    val isLoadingContext: Boolean = true,
    val isLoading: Boolean = false,
    val messages: List<KBAIMessage> = emptyList(),
    val inputText: String = "",
    val errorMessage: String? = null,
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
    val familyName: String = "",
) {
    val canSend: Boolean get() = !isLoading && !isLoadingContext && inputText.isNotBlank()
    val isNearLimit: Boolean get() = dailyLimit > 0 && usageToday >= (dailyLimit * 0.8).toInt()
}

@HiltViewModel
class PlanningAIChatViewModel @Inject constructor(
    private val chatRepository: PlanningAIChatRepository,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val eventDao: KBEventDao,
    private val groceryDao: KBGroceryItemDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanningAIChatState())
    val uiState: StateFlow<PlanningAIChatState> = _uiState.asStateFlow()

    private var familyId = ""
    private var systemPrompt = ""
    private var conversation: KBAIConversation? = null
    private var boundFamilyId = ""

    fun bind(familyId: String, familyName: String) {
        if (boundFamilyId == familyId) return
        boundFamilyId = familyId
        this.familyId = familyId
        _uiState.value = _uiState.value.copy(familyName = familyName, isLoadingContext = true)

        viewModelScope.launch {
            buildContextAndInit(familyId, familyName)
        }
    }

    private suspend fun buildContextAndInit(familyId: String, familyName: String) {
        val members = memberDao.observeActiveByFamilyId(familyId).first()
        val children = childDao.getChildrenByFamilyId(familyId)
        val events = eventDao.observeByFamilyId(familyId).first()
        val groceryItems = groceryDao.observeByFamilyId(familyId).first()

        val now = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("d MMM", Locale.ITALIAN)
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date())

        val upcomingEvents = events
            .filter { !it.isDeleted && it.startAtEpochMillis >= now }
            .sortedBy { it.startAtEpochMillis }
            .take(10)
            .map { "${dateFmt.format(Date(it.startAtEpochMillis))} · ${it.title}" }

        val pendingGrocery = groceryItems
            .filter { !it.isPurchased }
            .take(15)
            .map { it.name }

        systemPrompt = buildString {
            appendLine("Sei l'assistente AI di KidBox, l'app di gestione per famiglie con figli.")
            appendLine("Oggi è $today.")
            appendLine("Famiglia: $familyName")
            if (members.isNotEmpty()) {
                appendLine("Membri: ${members.mapNotNull { it.displayName?.takeIf(String::isNotBlank) }.joinToString(", ")}")
            }
            if (children.isNotEmpty()) {
                appendLine("Figli: ${children.map { it.name }.joinToString(", ")}")
            }
            if (upcomingEvents.isNotEmpty()) {
                appendLine("\nPROSSIMI EVENTI:")
                upcomingEvents.forEach { appendLine("- $it") }
            }
            if (pendingGrocery.isNotEmpty()) {
                appendLine("\nLISTA SPESA (da acquistare):")
                pendingGrocery.forEach { appendLine("- $it") }
            }
            appendLine("\nRispondi in italiano. Sii conciso e utile.")
            appendLine("Non inventare dati non presenti nel contesto.")
            appendLine("Non sostituire il parere di medici o professionisti.")
        }

        val scopeId = "planning_$familyId"
        val conv = chatRepository.getOrCreateConversation(familyId, scopeId)
        conversation = conv

        chatRepository.observeMessages(conv.id)
            .onEach { msgs -> _uiState.value = _uiState.value.copy(messages = msgs) }
            .launchIn(viewModelScope)

        _uiState.value = _uiState.value.copy(isLoadingContext = false)
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        val conv = conversation ?: return
        _uiState.value = _uiState.value.copy(inputText = "", isLoading = true, errorMessage = null)

        viewModelScope.launch {
            chatRepository.sendMessage(conv, text, systemPrompt)
                .onSuccess { (_, reply) ->
                    conversation = chatRepository.getOrCreateConversation(familyId, conv.scopeId)
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

    fun sendSuggestion(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        send()
    }

    fun setInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun clearConversation() {
        val conv = conversation ?: return
        viewModelScope.launch {
            chatRepository.clearConversation(conv)
            _uiState.value = _uiState.value.copy(messages = emptyList())
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
