package it.vittorioscocca.kidbox.health.visits.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.VisitAttachmentTag
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.health.ai.HealthAiSummaryPolicy
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class AiMessage(
    val id: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val createdAt: Long,
)

data class VisitAiChatUiState(
    val messages: List<AiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isListMode: Boolean = false,
    val listVisitCount: Int = 0,
)

@HiltViewModel
class VisitAiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val documentRepository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var summarizedMessageCount = 0
    private var rollingSummary: String? = null

    private val familyId: String = savedStateHandle["familyId"] ?: ""
    private val visitId: String = savedStateHandle["visitId"] ?: ""
    private val subjectName: String = savedStateHandle["subjectName"] ?: ""
    private val visitTitle: String = savedStateHandle["visitTitle"] ?: savedStateHandle["titolo"] ?: ""
    private val visitDate: String = savedStateHandle["visitDate"] ?: savedStateHandle["data"] ?: ""
    private val diagnosis: String = savedStateHandle["diagnosis"] ?: savedStateHandle["diagnosi"] ?: ""
    private val notes: String = savedStateHandle["notes"] ?: savedStateHandle["note"] ?: ""

    private val isListModeFlag: Boolean = savedStateHandle.get<Boolean>("isListMode") == true

    private val visitIdsFromJson: List<String> = runCatching {
        val raw = savedStateHandle.get<String>("visitIdsJson").orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        }
    }.getOrElse { emptyList() }

    private val isListMode: Boolean = isListModeFlag && visitIdsFromJson.isNotEmpty()

    private val _uiState = MutableStateFlow(
        VisitAiChatUiState(
            isListMode = isListMode,
            listVisitCount = visitIdsFromJson.size,
        ),
    )
    val uiState: StateFlow<VisitAiChatUiState> = _uiState.asStateFlow()

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        val userMessage = AiMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = trimmed,
            createdAt = System.currentTimeMillis(),
        )
        _uiState.value = _uiState.value.run {
            copy(
                messages = messages + userMessage,
                isLoading = true,
                errorMessage = null,
                isListMode = isListMode,
                listVisitCount = listVisitCount,
            )
        }

        viewModelScope.launch {
            runCatching {
                val msgs = _uiState.value.messages
                val (newCount, newSummary) = HealthAiSummaryPolicy.summarizeInMemoryIfNeeded(
                    familyId = familyId,
                    messages = msgs,
                    summarizedMessageCount = summarizedMessageCount,
                    currentSummary = rollingSummary,
                    aiRepository = aiRepository,
                    summarySystemPrompt = HealthAiSummaryPolicy.summarizeSystemPromptVisits,
                )
                summarizedMessageCount = newCount
                rollingSummary = newSummary

                val refertiBlock = loadVisitRefertiBlock()
                val basePrompt = buildSystemPrompt(refertiBlock)
                val finalPrompt = HealthAiSummaryPolicy.appendSummaryToSystemPrompt(basePrompt, rollingSummary)
                val payload = msgs.drop(summarizedMessageCount).map { it.toKBAIMessage(visitId) }
                val aiReply = aiRepository.askAI(familyId, finalPrompt, payload).getOrThrow()
                aiReply.reply
            }.onSuccess { replyText ->
                val assistant = AiMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = replyText,
                    createdAt = System.currentTimeMillis(),
                )
                _uiState.value = _uiState.value.run {
                    copy(
                        messages = messages + assistant,
                        isLoading = false,
                        isListMode = isListMode,
                        listVisitCount = listVisitCount,
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.run {
                    copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Errore AI",
                        isListMode = isListMode,
                        listVisitCount = listVisitCount,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.run {
            copy(
                errorMessage = null,
                isListMode = isListMode,
                listVisitCount = listVisitCount,
            )
        }
    }

    private suspend fun loadVisitRefertiBlock(): String {
        if (isListMode || familyId.isBlank() || visitId.isBlank()) return ""
        documentRepository.startRealtime(familyId)
        val docs = documentRepository.observeAllDocuments(familyId)
            .map { list ->
                list.filter {
                    VisitAttachmentTag.matches(it.notes, visitId) &&
                        it.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue
                }
            }
            .first()
        if (docs.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("--- DOCUMENTI / REFERTI ALLEGATI ---")
        for (doc in docs) {
            val raw = doc.extractedText ?: continue
            val prepared = HealthAiDocumentText.prepareExtractedTextForAi(raw)
            if (prepared.isBlank()) continue
            sb.appendLine()
            sb.appendLine("Documento: ${doc.title}")
            sb.appendLine("Tipo: ${doc.mimeType}")
            sb.appendLine("Testo estratto:")
            sb.appendLine(prepared)
        }
        return sb.toString().trimEnd()
    }

    private fun buildSystemPrompt(refertiBlock: String): String {
        val header = """
            Sei un assistente medico informativo per l'app KidBox.
            Rispondi in italiano. Non sostituisci il medico.
        """.trimIndent()
        return if (isListMode) {
            val n = visitIdsFromJson.size
            val preview = visitIdsFromJson.take(40).joinToString(", ")
            val tail = if (n > 40) " … (+${n - 40} altre)" else ""
            """
            $header
            Modalità elenco visite: ci sono $n visite (quelle attualmente visibili in lista, filtri di periodo/ricerca applicati) per il profilo "$subjectName".
            ID visite (solo riferimento interno app, non contengono dati clinici): $preview$tail
            Aiuta con domande trasversali (es. andamento, cosa chiedere al pediatra). Per dettagli su una singola visita suggerisci di aprirla nell'app.
            """.trimIndent()
        } else {
            buildString {
                appendLine(header)
                appendLine("Dati della visita:")
                appendLine("- visitId: $visitId")
                appendLine("- subjectName: $subjectName")
                appendLine("- titolo: $visitTitle")
                appendLine("- data: $visitDate")
                appendLine("- diagnosi: $diagnosis")
                appendLine("- note: $notes")
                if (refertiBlock.isNotBlank()) {
                    appendLine()
                    append(refertiBlock)
                }
            }.trimEnd()
        }
    }

    private fun AiMessage.toKBAIMessage(scope: String): KBAIMessage = KBAIMessage(
        id = id,
        conversationId = scope.ifBlank { "visit_ai" },
        roleRaw = role,
        content = content,
        createdAtEpochMillis = createdAt,
    )
}
