package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.domain.model.KBAIMessage

object ClinicalRecordAISynthesizer {

    private const val SERVER_RULES_OVERHEAD = 700

    private val systemPrompt: String = """
        Sei un medico di famiglia che redige una cartella clinica sintetica e professionale.
        Scrivi esclusivamente in prosa narrativa fluente in italiano.
        ZERO bullet point, ZERO elenchi. Ogni sezione è un paragrafo continuo.
        Non inventare valori assenti nei dati.
        ${ClinicalRecordPromptRules.supplementalRules}
        STRUTTURA: CARTELLA CLINICA — NOME; anagrafica; --- HEALTH CONNECT / WEARABLE (se dati);
        --- CURE; aree cliniche (CARDIOLOGIA include pressione); --- ESAMI IN ATTESA; --- RIEPILOGO.
        VIETATA sezione standalone PRESSIONE ARTERIOSA.
    """.trimIndent()

    data class EnhanceResult(
        val report: ClinicalRecordReport,
        val usage: ClinicalRecordAIUsageInfo?,
    )

    suspend fun enhance(
        aiRepository: AiRepository,
        familyId: String,
        nativeReport: ClinicalRecordReport,
        healthContext: String,
    ): EnhanceResult {
        val userContent = buildUserContent(nativeReport, healthContext)
        val estimate = estimatePayload(nativeReport, healthContext)
        if (estimate.totalChars > AIAskAIPayload.ABSOLUTE_MAX_CHARS) {
            throw IllegalStateException("Contesto troppo grande per l'AI.")
        }

        val reply = aiRepository.askAI(
            familyId = familyId,
            systemPrompt = systemPrompt,
            messages = listOf(aiUserMessage(userContent)),
            purpose = "clinicalRecord",
        ).getOrElse { throw it }

        var cleaned = ClinicalRecordTextSanitizer.sanitize(reply.reply)
        cleaned = collapseStrayListLines(cleaned)
        val parsed = ClinicalRecordTextSanitizer.sanitizeReport(
            ClinicalRecordReportParser.parse(cleaned, nativeReport.subjectName, sourceAiEnhanced = true),
        )
        val usage = ClinicalRecordAIUsageInfo(
            messageUnitsConsumed = reply.messageUnitsConsumed,
            usageToday = reply.usageToday,
            dailyLimit = reply.dailyLimit,
            isLargeContext = reply.isLargeContext,
            totalPayloadChars = estimate.totalChars,
        )
        return EnhanceResult(
            report = parsed.copy(
                areas = nativeReport.areas,
                globalSummary = nativeReport.globalSummary,
                specialtyTrends = nativeReport.specialtyTrends,
            ),
            usage = usage,
        )
    }

    fun estimatePayload(nativeReport: ClinicalRecordReport, healthContext: String): PayloadEstimate {
        val user = buildUserContent(nativeReport, healthContext)
        val total = AIAskAIPayload.totalChars(systemPrompt, listOf(aiUserMessage(user))) +
            SERVER_RULES_OVERHEAD
        // Cartella clinica: minimo fisso (Sonnet ~3× Haiku), parity server.
        val units = AIAskAIPayload.clinicalRecordMessageUnits(total)
        return PayloadEstimate(total, units, AIAskAIPayload.isLargeContext(total))
    }

    data class PayloadEstimate(val totalChars: Int, val messageUnits: Int, val isLargeContext: Boolean)

    private fun buildUserContent(native: ClinicalRecordReport, healthContext: String): String = """
        Redigi la cartella clinica narrativa per ${native.subjectName}.
        BOZZA NATIVA:
        ${native.fullDocumentLines.joinToString("\n")}
        DATI GREZZI APP:
        $healthContext
    """.trimIndent()

    private fun aiUserMessage(content: String) = KBAIMessage(
        id = "",
        conversationId = "",
        roleRaw = "user",
        content = content,
        createdAtEpochMillis = System.currentTimeMillis(),
    )

    private fun collapseStrayListLines(text: String): String {
        val blocks = mutableListOf<String>()
        var current = mutableListOf<String>()
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    blocks += current.joinToString(" ")
                    current = mutableListOf()
                }
                return@forEach
            }
            if (line == "---" || (line == line.uppercase() && line.length > 6)) {
                if (current.isNotEmpty()) {
                    blocks += current.joinToString(" ")
                    current = mutableListOf()
                }
                blocks += line
                return@forEach
            }
            current += line
        }
        if (current.isNotEmpty()) blocks += current.joinToString(" ")
        return blocks.joinToString("\n\n")
    }
}
