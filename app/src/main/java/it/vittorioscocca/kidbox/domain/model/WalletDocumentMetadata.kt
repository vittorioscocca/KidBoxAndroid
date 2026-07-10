package it.vittorioscocca.kidbox.domain.model

import android.util.Base64
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Metadati del documento Wallet codificati in `KBDocumentEntity.notes`:
 * `kb_wallet_doc:<kind>|enc=<base64 AES-GCM>`, dove il blob cifrato contiene
 * tutti i campi sensibili (Codice Fiscale, nome, nascita, numero documento,
 * date, categorie patente) cifrati con la chiave di famiglia
 * ([DocumentCryptoManager], la stessa che protegge il PDF). Solo il `kind`
 * resta in chiaro nel prefisso (serve per filtrare/mostrare l'icona senza
 * decifrare). Porting 1:1 di `KBWalletDocumentMetadata` (iOS) — stesso
 * formato su filo, cifrato allo stesso modo, quindi compatibile cross-platform
 * (stesso `DocumentCryptoManager`/`WalletCryptoService`, stessa chiave famiglia).
 */
data class WalletDocumentMetadata(
    val kind: DocumentKind,
    val codiceFiscale: String? = null,
    val holderName: String? = null,
    val birthInfo: String? = null,
    val documentNumber: String? = null,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val patenteCategories: List<PatenteCategory> = emptyList(),
    val notifyBeforeExpiry: Boolean = true,
) {
    /** Scadenza effettiva per il promemoria: per la patente la più imminente tra le categorie. */
    val effectiveExpiryDate: LocalDate?
        get() = if (kind == DocumentKind.PATENTE) {
            patenteCategories.mapNotNull { it.expiryDate }.minOrNull()
        } else {
            expiryDate
        }

    fun encode(cryptoManager: DocumentCryptoManager, familyId: String): String {
        val prefix = NOTES_PREFIX + kind.raw
        val payload = sensitivePayload()
        if (payload.isEmpty()) return prefix
        val encrypted = runCatching {
            cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), familyId)
        }.getOrNull()
        return if (encrypted != null) {
            "$prefix|enc=${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } else {
            // Fallback in chiaro se la chiave famiglia non è ancora disponibile:
            // meglio salvare i dati che perderli.
            "$prefix|$payload"
        }
    }

    private fun sensitivePayload(): String {
        val parts = mutableListOf<String>()
        codiceFiscale?.takeIf { it.isNotBlank() }?.let { parts += "cf=${sanitize(it)}" }
        holderName?.takeIf { it.isNotBlank() }?.let { parts += "holder=${sanitize(it)}" }
        birthInfo?.takeIf { it.isNotBlank() }?.let { parts += "birth=${sanitize(it)}" }
        documentNumber?.takeIf { it.isNotBlank() }?.let { parts += "docnum=${sanitize(it)}" }
        issueDate?.let { parts += "issue=${it.format(DATE_FMT)}" }
        expiryDate?.let { parts += "expiry=${it.format(DATE_FMT)}" }
        if (patenteCategories.isNotEmpty()) {
            val encodedCats = patenteCategories.mapNotNull { cat ->
                val code = cat.code
                    .replace("|", "").replace("=", "").replace(";", "").replace("~", "")
                    .trim().uppercase()
                if (code.isEmpty()) return@mapNotNull null
                val issue = cat.issueDate?.format(DATE_FMT).orEmpty()
                val expiry = cat.expiryDate?.format(DATE_FMT).orEmpty()
                "$code~$issue~$expiry"
            }.joinToString(";")
            if (encodedCats.isNotEmpty()) parts += "cats=$encodedCats"
        }
        parts += "notify=${if (notifyBeforeExpiry) "1" else "0"}"
        return parts.joinToString("|")
    }

    companion object {
        const val NOTES_PREFIX = "kb_wallet_doc:"
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd

        fun parse(notes: String?, cryptoManager: DocumentCryptoManager, familyId: String): WalletDocumentMetadata? {
            if (notes == null || !notes.startsWith(NOTES_PREFIX)) return null
            val segments = notes.split("|")
            val first = segments.firstOrNull() ?: return null
            val rawKind = first.removePrefix(NOTES_PREFIX)
            val kind = DocumentKind.entries.firstOrNull { it.raw == rawKind } ?: return null

            var metadata = WalletDocumentMetadata(kind = kind)
            val rest = segments.drop(1)

            val encSegment = rest.firstOrNull { it.startsWith("enc=") }
            if (encSegment != null) {
                val b64 = encSegment.removePrefix("enc=")
                val decrypted = runCatching {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    String(cryptoManager.decrypt(bytes, familyId), Charsets.UTF_8)
                }.getOrNull() ?: return metadata // chiave non disponibile: solo kind
                metadata = applyFields(decrypted, metadata)
                return metadata
            }

            // Retrocompatibilità: vecchio formato in chiaro dopo il kind.
            metadata = applyFields(rest.joinToString("|"), metadata)
            return metadata
        }

        private fun applyFields(payload: String, base: WalletDocumentMetadata): WalletDocumentMetadata {
            var m = base
            for (segment in payload.split("|")) {
                val idx = segment.indexOf('=')
                if (idx <= 0) continue
                val key = segment.substring(0, idx)
                val value = segment.substring(idx + 1)
                m = when (key) {
                    "cf" -> m.copy(codiceFiscale = value)
                    "holder" -> m.copy(holderName = value)
                    "birth" -> m.copy(birthInfo = value)
                    "docnum" -> m.copy(documentNumber = value)
                    "issue" -> m.copy(issueDate = parseDate(value))
                    "expiry" -> m.copy(expiryDate = parseDate(value))
                    "cats" -> m.copy(patenteCategories = parseCategories(value))
                    "notify" -> m.copy(notifyBeforeExpiry = value == "1")
                    else -> m
                }
            }
            return m
        }

        private fun parseCategories(raw: String): List<PatenteCategory> =
            raw.split(";").mapNotNull { entry ->
                val f = entry.split("~", limit = 3)
                val code = f.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val issue = f.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { parseDate(it) }
                val expiry = f.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { parseDate(it) }
                PatenteCategory(code = code, issueDate = issue, expiryDate = expiry)
            }

        private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw, DATE_FMT) }.getOrNull()

        private fun sanitize(value: String): String =
            value.replace("|", " ").replace("=", " ").trim()
    }
}
