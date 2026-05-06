package it.vittorioscocca.kidbox.data.health.ai

/**
 * Normalizza il testo estratto da PDF/RTF/immagini prima di includerlo nel system prompt AI
 * (stessa logica di sanitizzazione usata su iOS per i referti).
 *
 * Tronca per caratteri così il prompt non esplode su referti lunghi; il testo completo resta in locale/DB.
 */
object HealthAiDocumentText {

    /** Limite per singolo referto nel contesto AI (caratteri Unicode). */
    const val DEFAULT_MAX_CHARS_PER_DOCUMENT: Int = 18_000

    fun sanitizeExtractedText(text: String): String =
        text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    fun clipForAiContext(sanitized: String, maxChars: Int = DEFAULT_MAX_CHARS_PER_DOCUMENT): String {
        if (sanitized.length <= maxChars) return sanitized
        val head = sanitized.take(maxChars)
        return head + "\n\n[… Testo troncato per limite di contesto AI ($maxChars caratteri); il referto completo è disponibile nell'app. …]"
    }

    fun prepareExtractedTextForAi(raw: String?, maxChars: Int = DEFAULT_MAX_CHARS_PER_DOCUMENT): String {
        if (raw.isNullOrBlank()) return ""
        val sanitized = sanitizeExtractedText(raw)
        if (sanitized.isEmpty()) return ""
        return clipForAiContext(sanitized, maxChars)
    }
}
