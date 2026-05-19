package it.vittorioscocca.kidbox.data.health.ai

/**
 * Normalizza il testo estratto da PDF/RTF/immagini prima di includerlo nel system prompt AI
 * (nessun troncamento: il contesto completo viene inviato; il contatore messaggi scala lato server).
 */
object HealthAiDocumentText {

    fun sanitizeExtractedText(text: String): String =
        text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    fun prepareExtractedTextForAi(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return sanitizeExtractedText(raw)
    }
}
