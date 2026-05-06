package it.vittorioscocca.kidbox.health.exams.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.health.ai.HealthAiSummaryPolicy
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.health.visits.ai.AiMessage
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ExamAiChatUiState(
    val messages: List<AiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isListMode: Boolean = false,
    val listExamCount: Int = 0,
)

@HiltViewModel
class ExamAiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val documentRepository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var summarizedMessageCount = 0
    private var rollingSummary: String? = null

    private val familyId: String = savedStateHandle["familyId"] ?: ""
    private val examId: String = savedStateHandle["examId"] ?: ""
    private val examName: String = savedStateHandle["examName"] ?: savedStateHandle["name"] ?: ""
    private val subjectName: String = savedStateHandle["subjectName"] ?: ""
    private val examStatus: String = savedStateHandle["examStatus"] ?: savedStateHandle["stato"] ?: ""
    private val deadline: String = savedStateHandle["deadline"] ?: savedStateHandle["scadenza"] ?: ""
    private val preparation: String = savedStateHandle["preparation"] ?: savedStateHandle["preparazione"] ?: ""
    private val resultText: String = savedStateHandle["resultText"] ?: savedStateHandle["risultato"] ?: ""
    private val notes: String = savedStateHandle["notes"] ?: savedStateHandle["note"] ?: ""
    private val attachmentsSummary: String =
        savedStateHandle["attachmentsSummary"] ?: savedStateHandle["allegati"] ?: ""

    private val isListModeFlag: Boolean = savedStateHandle.get<Boolean>("isListMode") == true

    private val examIdsFromJson: List<String> = runCatching {
        val raw = savedStateHandle.get<String>("examIdsJson").orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        }
    }.getOrElse { emptyList() }

    private val isListMode: Boolean = isListModeFlag && examIdsFromJson.isNotEmpty()

    private val _uiState = MutableStateFlow(
        ExamAiChatUiState(
            isListMode = isListMode,
            listExamCount = examIdsFromJson.size,
        ),
    )
    val uiState: StateFlow<ExamAiChatUiState> = _uiState.asStateFlow()

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
                listExamCount = listExamCount,
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
                    summarySystemPrompt = HealthAiSummaryPolicy.summarizeSystemPromptExams,
                )
                summarizedMessageCount = newCount
                rollingSummary = newSummary

                val refertiBlock = loadExamRefertiBlock()
                val basePrompt = buildSystemPrompt(refertiBlock)
                val finalPrompt = HealthAiSummaryPolicy.appendSummaryToSystemPrompt(basePrompt, rollingSummary)
                val payload = msgs.drop(summarizedMessageCount).map { it.toKBAIMessage(examId) }
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
                        listExamCount = listExamCount,
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.run {
                    copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Errore AI",
                        isListMode = isListMode,
                        listExamCount = listExamCount,
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
                listExamCount = listExamCount,
            )
        }
    }

    private suspend fun loadExamRefertiBlock(): String {
        if (isListMode || familyId.isBlank() || examId.isBlank()) return ""
        documentRepository.startRealtime(familyId)
        val docs = documentRepository.observeAllDocuments(familyId)
            .map { list ->
                list.filter {
                    ExamAttachmentTag.matches(it.notes, examId) &&
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
        val resultForAi = HealthAiDocumentText.prepareExtractedTextForAi(resultText)
        val singleExam = """
            Sei un assistente medico informativo. Spiega l'esame, la preparazione, il risultato e gli allegati.
            Non sostituisci il medico. Rispondi in italiano.
            Dati esame:
            - examId: $examId
            - nome: $examName
            - subjectName (profilo): $subjectName
            - stato: $examStatus
            - scadenza: $deadline
            - preparazione: $preparation
            - risultato: $resultForAi
            - note: $notes
            - allegati: $attachmentsSummary
        """.trimIndent()
        if (!isListMode) {
            return if (refertiBlock.isBlank()) {
                singleExam
            } else {
                singleExam + "\n\n" + refertiBlock
            }
        }
        val n = examIdsFromJson.size
        val preview = examIdsFromJson.take(40).joinToString(", ")
        val tail = if (n > 40) " … (+${n - 40} altri)" else ""
        return """
            Sei un assistente medico informativo. Spiega in modo generale esami, preparazioni, risultati e allegati quando rilevante.
            Non sostituisci il medico. Rispondi in italiano.
            Modalità elenco esami: ci sono $n esami/analisi visibili nell'elenco attuale (filtro periodo applicato) per il profilo "$subjectName".
            ID esami (solo riferimento interno app): $preview$tail
            Per dettagli su un singolo esame suggerisci di aprirlo nell'app.
        """.trimIndent()
    }

    private fun AiMessage.toKBAIMessage(scope: String): KBAIMessage = KBAIMessage(
        id = id,
        conversationId = scope.ifBlank { "exam_ai" },
        roleRaw = role,
        content = content,
        createdAtEpochMillis = createdAt,
    )
}
