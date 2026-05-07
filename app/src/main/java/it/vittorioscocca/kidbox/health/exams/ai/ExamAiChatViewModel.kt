package it.vittorioscocca.kidbox.health.exams.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.HealthAIChatRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.health.visits.ai.AiMessage
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ExamAiChatUiState(
    val messages: List<AiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isListMode: Boolean = false,
    val listExamCount: Int = 0,
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
)

@HiltViewModel
class ExamAiChatViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val examRepository: MedicalExamRepository,
    private val chatRepository: HealthAIChatRepository,
    private val healthAttachmentService: HealthAttachmentService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
    private val familyId: String = savedStateHandle["familyId"] ?: ""
    private val childId: String = savedStateHandle["childId"] ?: ""
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
    private var conversation: KBAIConversation? = null

    init {
        if (familyId.isNotBlank()) {
            healthAttachmentService.enqueueBackfillHealthExtraction(familyId)
        }
        bindConversation()
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return
        val conv = conversation ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            runCatching {
                val contextBlock = if (isListMode) loadExamListContextBlock() else loadExamRefertiBlock()
                val basePrompt = buildSystemPrompt(contextBlock)
                chatRepository.sendMessage(conv, trimmed, basePrompt).getOrThrow()
            }.onSuccess {
                val usage = it.second
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isListMode = isListMode,
                    usageToday = usage.usageToday,
                    dailyLimit = usage.dailyLimit,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.run {
                    copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Errore AI",
                        isListMode = isListMode,
                    )
                }
            }
        }
    }

    private fun bindConversation() {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            val scopeId = if (isListMode) {
                // Stable history for "lista esami": same chat for the same profile,
                // independent from current filters/visible IDs.
                "health_exam_list_v3:$familyId:${childId.ifBlank { subjectName }}"
            } else {
                "health_exam_single:$familyId:$examId"
            }
            val conv = chatRepository.getOrCreateConversation(
                familyId = familyId,
                childId = childId.ifBlank { "health_exam" },
                scopeId = scopeId,
            )
            conversation = conv
            chatRepository.observeMessages(conv.id)
                .onEach { msgs ->
                    _uiState.value = _uiState.value.copy(
                        messages = msgs.map { it.toUiMessage() },
                        isListMode = isListMode,
                    )
                }
                .launchIn(viewModelScope)
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
        healthAttachmentService.ensureExamAttachmentsExtraction(familyId, examId)
        documentRepository.startRealtime(familyId)
        val docs = documentRepository.observeAllDocuments(familyId)
            .map { list ->
                list.filter {
                    ExamAttachmentTag.matches(it.notes, examId) &&
                        (
                            !it.extractedText.isNullOrBlank() ||
                                it.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue
                            )
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

    private suspend fun loadExamListContextBlock(): String {
        if (!isListMode || familyId.isBlank() || childId.isBlank()) return ""
        // List-level AI must reason over the full exam history of the profile,
        // not only currently visible/filtered rows.
        val allExamsForChild = examRepository.listByFamilyAndChild(familyId, childId)
            .filter { !it.isDeleted }
            .sortedByDescending { it.updatedAtEpochMillis }
        if (allExamsForChild.isEmpty()) return ""

        // Ensure OCR for attachments before composing AI context.
        allExamsForChild.take(30).forEach { exam ->
            healthAttachmentService.ensureExamAttachmentsExtraction(familyId, exam.id)
        }
        val exams = allExamsForChild

        documentRepository.startRealtime(familyId)
        val docs = documentRepository.observeAllDocuments(familyId).first()
            .filter { !it.isDeleted }

        val sb = StringBuilder()
        sb.appendLine("--- CONTESTO DETTAGLIATO STORIA ESAMI ---")
        sb.appendLine("Totale esami profilo: ${exams.size}")

        exams.take(30).forEachIndexed { index, exam ->
            val examDocs = docs.filter { ExamAttachmentTag.matches(it.notes, exam.id) }
            sb.appendLine()
            sb.appendLine("${index + 1}) ${exam.name}")
            sb.appendLine("   - id: ${exam.id}")
            sb.appendLine("   - stato: ${exam.statusRaw}")
            sb.appendLine("   - scadenza: ${exam.deadlineEpochMillis?.let { dateFmt.format(Date(it)) } ?: "—"}")
            sb.appendLine("   - preparazione: ${exam.preparation?.ifBlank { "—" } ?: "—"}")
            sb.appendLine("   - risultato: ${HealthAiDocumentText.prepareExtractedTextForAi(exam.resultText, 2500).ifBlank { "—" }}")
            sb.appendLine("   - note: ${exam.notes?.ifBlank { "—" } ?: "—"}")
            sb.appendLine("   - allegati: ${examDocs.size}")

            examDocs.forEach { doc ->
                val prepared = HealthAiDocumentText.prepareExtractedTextForAi(doc.extractedText, 4500)
                if (prepared.isBlank()) return@forEach
                sb.appendLine("     • Referto: ${doc.title} (${doc.mimeType})")
                sb.appendLine("       Testo estratto:")
                sb.appendLine(prepared.prependIndent("       "))
            }
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
        return buildString {
            appendLine(
                """
            Sei un assistente medico informativo. Hai accesso alla storia completa degli esami del profilo e ai testi OCR dei referti allegati.
            Devi rispondere usando i dettagli dei referti disponibili nel contesto (valori, range, note, conclusioni) senza dire che serva aprire il singolo esame.
            Se nel contesto ci sono referti o dati esame, NON dire "non ho accesso" o "non posso leggere i referti": usa le informazioni disponibili.
            Non sostituisci il medico. Rispondi in italiano.
            Modalità elenco esami: ci sono $n esami/analisi visibili nell'elenco attuale per il profilo "$subjectName".
            ID visibili in elenco (riferimento interno): $preview$tail
            Usa anche il contesto storico completo degli esami e referti allegati già fornito sotto.
                """.trimIndent(),
            )
            if (refertiBlock.isNotBlank()) {
                appendLine()
                append(refertiBlock)
            }
        }.trimEnd()
    }

    private fun KBAIMessage.toUiMessage(): AiMessage = AiMessage(
        id = id,
        role = roleRaw,
        content = content,
        createdAt = createdAtEpochMillis,
    )
}
