package it.vittorioscocca.kidbox.notifications

import it.vittorioscocca.kidbox.data.crypto.SectionFields

/**
 * Push con il testo cifrato (cifratura delle sezioni, secondo giro §5 di
 * `internal/cifratura-sezioni.md`). Il server non legge i titoli sigillati:
 * manda in `data` il blob `secure` del documento, `secureColl`,
 * `secureFamilyId` e i testi `titleTpl`/`bodyTpl` già tradotti con segnaposto
 * `{campo}`; `title`/`body` sono la versione generica.
 *
 * Parte pura: la decifratura arriva da fuori, così si prova in un test JVM.
 */
object SecurePushText {

    private val placeholder = Regex("""\{([A-Za-z0-9_]+)\}""")

    /**
     * Riempie i segnaposto `{campo}` con [values]. Se anche un solo segnaposto
     * non ha un valore non vuoto restituisce null: chi chiama usa allora la
     * versione generica intera, mai un testo a metà.
     */
    fun fill(template: String, values: Map<String, String>): String? {
        var missing = false
        val out = placeholder.replace(template) { match ->
            val value = values[match.groupValues[1]]?.trim()
            if (value.isNullOrEmpty()) {
                missing = true
                ""
            } else {
                value
            }
        }
        return if (missing) null else out
    }

    /**
     * Titolo e testo decifrati, o null se la push non è «da decifrare» o se
     * qualcosa manca (blob assente perché troppo lungo, chiave mancante, JSON
     * illeggibile, segnaposto senza valore): allora valgono `title`/`body`.
     *
     * [decrypt] riceve blob e familyId e restituisce il JSON in chiaro o null.
     */
    fun resolve(
        data: Map<String, String>,
        decrypt: (blob: String, familyId: String) -> String?,
    ): Pair<String, String>? {
        val blob = data["secure"]?.takeIf { it.isNotBlank() } ?: return null
        val familyId = (data["secureFamilyId"] ?: data["familyId"])?.takeIf { it.isNotBlank() } ?: return null
        val titleTpl = data["titleTpl"]?.takeIf { it.isNotBlank() } ?: return null
        val bodyTpl = data["bodyTpl"]?.takeIf { it.isNotBlank() } ?: return null
        val json = runCatching { decrypt(blob, familyId) }.getOrNull() ?: return null
        val values = SectionFields.decodeJson(json) ?: return null
        val title = fill(titleTpl, values) ?: return null
        val body = fill(bodyTpl, values) ?: return null
        return title to body
    }
}
