package it.vittorioscocca.kidbox.data.health.ai

/**
 * Normalizza il testo estratto da PDF/RTF/immagini prima di includerlo nel system prompt AI.
 */
object HealthAiDocumentText {

    /** Limite referto nel contesto standard; massima accuratezza usa testo intero. */
    const val STANDARD_REFERTO_MAX_CHARS: Int = 4_000

    fun sanitizeExtractedText(text: String): String =
        text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    fun prepareExtractedTextForAi(raw: String?, maxChars: Int? = null): String {
        if (raw.isNullOrBlank()) return ""
        val sanitized = sanitizeExtractedText(raw)
        val limit = maxChars ?: return sanitized
        if (sanitized.length <= limit) return sanitized
        return sanitized.take(limit).trimEnd() +
            "\n[… referto troncato nel contesto standard; usa “Massima accuratezza” per il testo completo]"
    }
}
