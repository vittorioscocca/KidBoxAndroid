package it.vittorioscocca.kidbox.health.visits.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.TreatmentAttachmentTag
import it.vittorioscocca.kidbox.data.health.VisitAttachmentTag
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.HealthAIChatRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatStreamingDelivery
import it.vittorioscocca.kidbox.ui.screens.ai.planning.FamilyMemoryPromptSection
import it.vittorioscocca.kidbox.ui.screens.ai.planning.FamilyMemoryService
import it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningAIActionBlock
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
import it.vittorioscocca.kidbox.util.KBLocale

data class AiMessage(
    val id: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val createdAt: Long,
)

data class VisitAiChatUiState(
    val messages: List<AiMessage> = emptyList(),
    val streamingMessageId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isListMode: Boolean = false,
    val listVisitCount: Int = 0,
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
    val actionExecutionSummary: String? = null,
)

@HiltViewModel
class VisitAiChatViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val visitRepository: MedicalVisitRepository,
    private val examRepository: MedicalExamRepository,
    private val treatmentRepository: TreatmentRepository,
    private val chatRepository: HealthAIChatRepository,
    private val healthAttachmentService: HealthAttachmentService,
    private val familyMemoryService: FamilyMemoryService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val COMPACTION_THRESHOLD = 0.60
    private var lastCompactionStep: Int = 0
    private var messagesInSession: Int = 0
    private var dailyLimit: Int = 0

    private val dateFmt = SimpleDateFormat("d MMM yyyy", KBLocale.current())
    private val familyId: String = savedStateHandle["familyId"] ?: ""
    private val childId: String = savedStateHandle["childId"] ?: ""
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
                val contextBlock = if (isListMode) loadVisitListContextBlock() else loadVisitRefertiBlock()
                val basePrompt = buildSystemPrompt(contextBlock)
                val promptWithMemory = FamilyMemoryPromptSection.append(basePrompt, familyMemoryService, familyId)
                chatRepository.sendMessage(conv, trimmed, promptWithMemory).getOrThrow()
            }.onSuccess { result ->
                messagesInSession = result.reply.usageToday
                dailyLimit = result.reply.dailyLimit
                maybeCompactIfNeeded(conv)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    streamingMessageId = AIChatStreamingDelivery.beginAssistantReveal(
                        result.assistantMessage.id,
                    ),
                    isListMode = isListMode,
                    usageToday = result.reply.usageToday,
                    dailyLimit = result.reply.dailyLimit,
                    actionExecutionSummary = result.executionSummary,
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

    private fun bindConversation() {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            val scopeId = if (isListMode) {
                // Stable history for "lista visite": same chat for the same profile,
                // independent from current filters/visible IDs.
                "health_visit_list_v2:$familyId:${childId.ifBlank { subjectName }}"
            } else {
                "health_visit_single:$familyId:$visitId"
            }
            val conv = chatRepository.getOrCreateConversation(
                familyId = familyId,
                childId = childId.ifBlank { "health_visit" },
                scopeId = scopeId,
            )
            conversation = conv
            lastCompactionStep = if (conv.summary.isNullOrBlank()) 0 else 3
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

    fun clearActionExecutionSummary() {
        _uiState.value = _uiState.value.copy(actionExecutionSummary = null)
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

    fun finishStreaming(messageId: String) {
        _uiState.value = _uiState.value.copy(
            streamingMessageId = AIChatStreamingDelivery.finishReveal(
                messageId,
                _uiState.value.streamingMessageId,
            ),
            isListMode = isListMode,
        )
    }

    fun clearConversation() {
        val conv = conversation ?: return
        viewModelScope.launch {
            chatRepository.clearConversation(conv)
            lastCompactionStep = 0
            _uiState.value = _uiState.value.copy(messages = emptyList(), streamingMessageId = null)
        }
    }

    private suspend fun loadVisitRefertiBlock(): String {
        if (isListMode || familyId.isBlank() || visitId.isBlank()) return ""
        healthAttachmentService.ensureVisitAttachmentsExtraction(familyId, visitId)
        documentRepository.startRealtime(familyId)
        val initialDocs = documentRepository.observeAllDocuments(familyId)
            .map { list ->
                list.filter {
                    !it.isDeleted
                }
            }
            .first()
        val sb = StringBuilder()
        val visitDocs = initialDocs.filter {
            VisitAttachmentTag.matches(it.notes, visitId) &&
                (
                    !it.extractedText.isNullOrBlank() ||
                        it.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue
                    )
        }
        if (visitDocs.isNotEmpty()) {
            sb.appendLine("--- DOCUMENTI / REFERTI VISITA ---")
        }
        for (doc in visitDocs) {
            val raw = doc.extractedText ?: continue
            val prepared = HealthAiDocumentText.prepareExtractedTextForAi(raw)
            if (prepared.isBlank()) continue
            sb.appendLine()
            sb.appendLine("Documento: ${doc.title}")
            sb.appendLine("Tipo: ${doc.mimeType}")
            sb.appendLine("Testo estratto:")
            sb.appendLine(prepared)
        }

        val visit = visitRepository.loadOnce(visitId)
        val linkedExamIdsFromJson = visit?.linkedExamIdsJson
            ?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { i -> arr.getString(i) }
                }.getOrElse { emptyList() }
            } ?: emptyList()
        val examsForChild = examRepository.listByFamilyAndChild(familyId, childId)
            .filter { !it.isDeleted }
        val examsLinkedById = examsForChild.filter { linkedExamIdsFromJson.contains(it.id) }
        val examsLinkedByVisit = examsForChild.filter { it.prescribingVisitId == visitId }
        val linkedExams = (examsLinkedById + examsLinkedByVisit)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtEpochMillis }
        val linkedTreatmentIdsFromJson = visit?.linkedTreatmentIdsJson
            ?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { i -> arr.getString(i) }
                }.getOrElse { emptyList() }
            } ?: emptyList()
        val treatmentsForChild = treatmentRepository.listByFamilyAndChild(familyId, childId)
            .filter { !it.isDeleted }
        val linkedTreatments = (treatmentsForChild.filter { linkedTreatmentIdsFromJson.contains(it.id) } +
            treatmentsForChild.filter { it.prescribingVisitId == visitId })
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtEpochMillis }

        if (linkedExams.isNotEmpty()) {
            linkedExams.forEach { exam ->
                healthAttachmentService.ensureExamAttachmentsExtraction(familyId, exam.id)
            }
            val allDocs = documentRepository.observeAllDocuments(familyId)
                .map { list -> list.filter { !it.isDeleted } }
                .first()
            sb.appendLine()
            sb.appendLine("--- ESAMI COLLEGATI ALLA VISITA ---")
            linkedExams.forEachIndexed { idx, exam ->
                sb.appendLine("${idx + 1}) ${exam.name}")
                sb.appendLine("   - stato: ${exam.statusRaw}")
                sb.appendLine("   - scadenza: ${exam.deadlineEpochMillis?.let { dateFmt.format(Date(it)) } ?: "—"}")
                sb.appendLine("   - preparazione: ${exam.preparation?.ifBlank { "—" } ?: "—"}")
                sb.appendLine("   - risultato: ${HealthAiDocumentText.prepareExtractedTextForAi(exam.resultText, 2500).ifBlank { "—" }}")
                val examDocs = allDocs.filter { ExamAttachmentTag.matches(it.notes, exam.id) }
                examDocs.forEach { doc ->
                    val prepared = HealthAiDocumentText.prepareExtractedTextForAi(doc.extractedText, 4500)
                    if (prepared.isBlank()) return@forEach
                    sb.appendLine("   • Referto esame: ${doc.title}")
                    sb.appendLine(prepared.prependIndent("     "))
                }
                sb.appendLine()
            }
        }
        if (linkedTreatments.isNotEmpty()) {
            linkedTreatments.forEach { treatment ->
                healthAttachmentService.ensureTreatmentAttachmentsExtraction(familyId, treatment.id)
            }
            val allDocs = documentRepository.observeAllDocuments(familyId)
                .map { list -> list.filter { !it.isDeleted } }
                .first()
            sb.appendLine()
            sb.appendLine("--- CURE/FARMACI COLLEGATI ALLA VISITA ---")
            linkedTreatments.forEachIndexed { idx, treatment ->
                sb.appendLine("${idx + 1}) ${treatment.drugName}")
                sb.appendLine("   - dosaggio: ${treatment.dosageValue} ${treatment.dosageUnit}")
                sb.appendLine("   - frequenza: ${treatment.dailyFrequency}x/die")
                sb.appendLine("   - note: ${treatment.notes?.ifBlank { "—" } ?: "—"}")
                val treatmentDocs = allDocs.filter { TreatmentAttachmentTag.matches(it.notes, treatment.id) }
                treatmentDocs.forEach { doc ->
                    val prepared = HealthAiDocumentText.prepareExtractedTextForAi(doc.extractedText, 4500)
                    if (prepared.isBlank()) return@forEach
                    sb.appendLine("   • Referto cura/farmaco: ${doc.title}")
                    sb.appendLine(prepared.prependIndent("     "))
                }
                sb.appendLine()
            }
        }
        return sb.toString().trimEnd()
    }

    private suspend fun loadVisitListContextBlock(): String {
        if (!isListMode || familyId.isBlank() || childId.isBlank()) return ""
        documentRepository.startRealtime(familyId)
        val visits = visitRepository.observe(familyId, childId).first()
            .filter { !it.isDeleted }
            .sortedByDescending { it.updatedAtEpochMillis }
        if (visits.isEmpty()) return ""

        visits.take(30).forEach { v ->
            healthAttachmentService.ensureVisitAttachmentsExtraction(familyId, v.id)
        }
        val examsForChild = examRepository.listByFamilyAndChild(familyId, childId).filter { !it.isDeleted }
        val treatmentsForChild = treatmentRepository.listByFamilyAndChild(familyId, childId).filter { !it.isDeleted }
        val allLinkedExamIds = mutableSetOf<String>()
        val allLinkedTreatmentIds = mutableSetOf<String>()
        visits.take(30).forEach { visit ->
            val linkedExamIds = runCatching {
                val arr = JSONArray(visit.linkedExamIdsJson)
                List(arr.length()) { i -> arr.getString(i) }
            }.getOrElse { emptyList() }
            val linkedTreatmentIds = runCatching {
                val arr = JSONArray(visit.linkedTreatmentIdsJson)
                List(arr.length()) { i -> arr.getString(i) }
            }.getOrElse { emptyList() }
            allLinkedExamIds += linkedExamIds
            allLinkedExamIds += examsForChild.filter { it.prescribingVisitId == visit.id }.map { it.id }
            allLinkedTreatmentIds += linkedTreatmentIds
            allLinkedTreatmentIds += treatmentsForChild.filter { it.prescribingVisitId == visit.id }.map { it.id }
        }
        allLinkedExamIds.forEach { examId ->
            healthAttachmentService.ensureExamAttachmentsExtraction(familyId, examId)
        }
        allLinkedTreatmentIds.forEach { treatmentId ->
            healthAttachmentService.ensureTreatmentAttachmentsExtraction(familyId, treatmentId)
        }

        // Re-read after OCR ensures so extractedText used in prompt is up-to-date.
        val allDocs = documentRepository.observeAllDocuments(familyId).first()
            .filter { !it.isDeleted }

        val sb = StringBuilder()
        sb.appendLine("--- CONTESTO DETTAGLIATO STORIA VISITE ---")
        sb.appendLine("Totale visite profilo: ${visits.size}")

        visits.take(30).forEachIndexed { index, visit ->
            sb.appendLine()
            sb.appendLine("${index + 1}) ${visit.reason}")
            sb.appendLine("   - id: ${visit.id}")
            sb.appendLine("   - data: ${dateFmt.format(Date(visit.dateEpochMillis))}")
            sb.appendLine("   - medico: ${visit.doctorName?.ifBlank { "—" } ?: "—"}")
            sb.appendLine("   - diagnosi: ${visit.diagnosis?.ifBlank { "—" } ?: "—"}")
            sb.appendLine("   - raccomandazioni: ${visit.recommendations?.ifBlank { "—" } ?: "—"}")
            sb.appendLine("   - note: ${visit.notes?.ifBlank { "—" } ?: "—"}")

            val visitDocs = allDocs.filter { VisitAttachmentTag.matches(it.notes, visit.id) }
            visitDocs.forEach { doc ->
                val prepared = HealthAiDocumentText.prepareExtractedTextForAi(doc.extractedText, 4500)
                if (prepared.isBlank()) return@forEach
                sb.appendLine("   • Referto visita: ${doc.title}")
                sb.appendLine(prepared.prependIndent("     "))
            }

            val linkedExamIds = runCatching {
                val arr = JSONArray(visit.linkedExamIdsJson)
                List(arr.length()) { i -> arr.getString(i) }
            }.getOrElse { emptyList() }
            val linkedExams = (examsForChild.filter { linkedExamIds.contains(it.id) } +
                examsForChild.filter { it.prescribingVisitId == visit.id })
                .distinctBy { it.id }
            val linkedTreatmentIds = runCatching {
                val arr = JSONArray(visit.linkedTreatmentIdsJson)
                List(arr.length()) { i -> arr.getString(i) }
            }.getOrElse { emptyList() }
            val linkedTreatments = (treatmentsForChild.filter { linkedTreatmentIds.contains(it.id) } +
                treatmentsForChild.filter { it.prescribingVisitId == visit.id })
                .distinctBy { it.id }

            linkedExams.forEach { exam ->
                healthAttachmentService.ensureExamAttachmentsExtraction(familyId, exam.id)
                sb.appendLine("   • Esame collegato: ${exam.name} (${exam.statusRaw})")
                val examDocs = allDocs.filter { ExamAttachmentTag.matches(it.notes, exam.id) }
                examDocs.forEach { doc ->
                    val prepared = HealthAiDocumentText.prepareExtractedTextForAi(doc.extractedText, 3000)
                    if (prepared.isBlank()) return@forEach
                    sb.appendLine("     Referto esame: ${doc.title}")
                    sb.appendLine(prepared.prependIndent("       "))
                }
            }
            linkedTreatments.forEach { treatment ->
                healthAttachmentService.ensureTreatmentAttachmentsExtraction(familyId, treatment.id)
                sb.appendLine("   • Cura/Farmaco collegato: ${treatment.drugName}")
                sb.appendLine("     Dosaggio: ${treatment.dosageValue} ${treatment.dosageUnit} · ${treatment.dailyFrequency}x/die")
                val treatmentDocs = allDocs.filter { TreatmentAttachmentTag.matches(it.notes, treatment.id) }
                treatmentDocs.forEach { doc ->
                    val prepared = HealthAiDocumentText.prepareExtractedTextForAi(doc.extractedText, 3000)
                    if (prepared.isBlank()) return@forEach
                    sb.appendLine("     Referto cura/farmaco: ${doc.title}")
                    sb.appendLine(prepared.prependIndent("       "))
                }
            }
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
            buildString {
                appendLine(
                    """
            $header
            Hai accesso alla storia completa delle visite del profilo e ai testi OCR dei referti visita/esami/cure-farmaci collegati.
            Devi rispondere usando i dettagli clinici disponibili nel contesto, senza chiedere di aprire la singola visita.
            Modalità elenco visite: ci sono $n visite visibili nell'elenco attuale per il profilo "$subjectName".
            ID visite visibili (riferimento interno): $preview$tail
            Usa anche il contesto storico completo già fornito sotto.
                    """.trimIndent(),
                )
                if (refertiBlock.isNotBlank()) {
                    appendLine()
                    append(refertiBlock)
                }
                appendLine()
                append(PlanningAIActionBlock.promptSection)
            }.trimEnd()
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
                appendLine()
                append(PlanningAIActionBlock.promptSection)
            }.trimEnd()
        }
    }

    private fun KBAIMessage.toUiMessage(): AiMessage = AiMessage(
        id = id,
        role = roleRaw,
        content = content,
        createdAt = createdAtEpochMillis,
    )
}
