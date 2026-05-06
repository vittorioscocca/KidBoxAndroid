package it.vittorioscocca.kidbox.data.health.ai

import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.health.visits.ai.AiMessage

/**
 * Stessa strategia dell'app iOS: oltre 8 messaggi non ancora compresi nel riassunto,
 * compatta tutto tranne gli ultimi 4 in un riassunto nel system prompt; le richieste API usano solo i messaggi recenti.
 */
object HealthAiSummaryPolicy {
    const val SUMMARY_THRESHOLD = 8
    const val RECENT_MESSAGES_TO_KEEP = 4

    /** Panoramica salute / chat aggregata. */
    val summarizeSystemPromptHealthOverview: String = """
Riassumi in modo fedele e compatto la conversazione seguente.
Mantieni:
- richieste principali dell'utente
- dati sanitari discussi (cure, vaccini, visite, esami)
- risultati o referti menzionati
- eventuali dubbi ancora aperti
Non aggiungere nulla di nuovo.
    """.trimIndent()

    val summarizeSystemPromptVisits: String = """
Riassumi in modo fedele e compatto la conversazione seguente.
Mantieni:
- richieste principali del genitore
- diagnosi, raccomandazioni, terapie, cure, esami menzionati
- farmaci prescritti e relative istruzioni
- eventuali dubbi ancora aperti
Non aggiungere nulla di nuovo.
    """.trimIndent()

    val summarizeSystemPromptExams: String = """
Riassumi in modo fedele e compatto la conversazione seguente.
Mantieni:
- richieste principali dell'utente
- esami discussi e loro stato
- risultati, referti o allegati menzionati
- eventuali dubbi ancora aperti
Non aggiungere nulla di nuovo.
    """.trimIndent()

    fun appendSummaryToSystemPrompt(base: String, summary: String?): String {
        val s = summary?.trim()?.takeIf { it.isNotEmpty() } ?: return base
        return "$base\n\nRIASSUNTO CONVERSAZIONE PRECEDENTE\n$s"
    }

    /**
     * @return coppia (nuovo [summarizedMessageCount], nuovo testo riassunto sostitutivo).
     */
    suspend fun summarizeInMemoryIfNeeded(
        familyId: String,
        messages: List<AiMessage>,
        summarizedMessageCount: Int,
        currentSummary: String?,
        aiRepository: AiRepository,
        summarySystemPrompt: String,
    ): Pair<Int, String?> {
        if (familyId.isBlank()) return summarizedMessageCount to currentSummary
        val total = messages.size
        val unsummarized = total - summarizedMessageCount
        if (unsummarized <= SUMMARY_THRESHOLD || total <= RECENT_MESSAGES_TO_KEEP) {
            return summarizedMessageCount to currentSummary
        }
        val toSummarize = messages.take(total - RECENT_MESSAGES_TO_KEEP)
        val transcript = toSummarize.joinToString("\n") { msg ->
            val label = if (msg.role == "user") "Utente" else "Assistente"
            "$label: ${msg.content}"
        }
        val summaryMessages = listOf(
            KBAIMessage(
                id = "summary-input",
                conversationId = "",
                roleRaw = "user",
                content = transcript,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        val result = aiRepository.askAI(familyId, summarySystemPrompt, summaryMessages).getOrNull()
            ?: return summarizedMessageCount to currentSummary
        return toSummarize.size to result.reply
    }
}
