package it.vittorioscocca.kidbox.domain.save

/**
 * Riconosce un indirizzo postale dentro un messaggio, senza rete e senza modello:
 * toponimo (via, piazza, rue, calle, street…) + nome + numero civico, con CAP e
 * città se seguono. Gemello di `KBAddressDetector` su iOS (lì lo fa NSDataDetector).
 * Meglio non riconoscere che sbagliare: serve almeno il toponimo e un nome.
 */
object AddressDetector {

    private const val TYPES =
        "via|viale|v\\.le|piazza|p\\.zza|piazzale|corso|c\\.so|largo|vicolo|strada|str\\.|" +
            "lungomare|lungotevere|borgo|contrada|località|loc\\.|salita|traversa|" +
            "rue|avenue|boulevard|place|chemin|" +
            "calle|avenida|paseo|plaza|camino|" +
            "street|st\\.|road|rd\\.|avenue|ave\\.|lane|drive|square"

    // via Roma 12 · Via dei Mille, 4/B · Piazza Garibaldi 3, 20121 Milano
    private val streetFirst = Regex(
        "\\b(?:$TYPES)\\s+(?:(?:d[eoai]l?l?[ae]?|d')\\s*)?[\\p{L}'.]+(?:\\s+[\\p{L}'.]+){0,3}" +
            "(?:,?\\s*(?:n\\.?\\s*)?\\d{1,4}(?:\\s*/?\\s*[A-Za-z])?)?" +
            "(?:,?\\s*\\d{5}(?:\\s+(?-i:\\p{Lu}[\\p{L}']+(?:\\s\\p{Lu}[\\p{L}']+)?))?)?",
        RegexOption.IGNORE_CASE,
    )

    // 221B Baker Street · 10 Downing Street
    private val numberFirst = Regex(
        "\\b\\d{1,4}[A-Za-z]?\\s+[\\p{L}'.]+(?:\\s+[\\p{L}'.]+){0,2}\\s+(?:street|st\\.|road|rd\\.|avenue|ave\\.|lane|drive|square)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun firstAddress(text: String): String? {
        val m = streetFirst.find(text)
        val raw = m?.value ?: numberFirst.find(text)?.value ?: return null
        val cleaned = raw.trim().trimEnd(',', '.', ';', ':', '!', '?').trim()
        // «via» da sola o seguita da una parola comune non è un indirizzo
        val words = cleaned.split(Regex("\\s+"))
        if (words.size < 2 || cleaned.length < 6) return null
        // senza numero civico serve un nome proprio: «via di lì» non è un indirizzo
        if (m != null && cleaned.none { it.isDigit() } && !words.last().first().isUpperCase()) return null
        return cleaned
    }
}
