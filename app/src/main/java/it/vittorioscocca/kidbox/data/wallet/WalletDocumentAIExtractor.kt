package it.vittorioscocca.kidbox.data.wallet

import android.graphics.Bitmap
import android.util.Base64
import it.vittorioscocca.kidbox.data.remote.ai.AIMessagePayload
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.PatenteCategory
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Lettura assistita AI dei documenti Wallet: manda le immagini scansionate al
 * modello vision (via la Cloud Function `askAI` esistente, stesso `purpose`
 * e stesso prompt JSON di iOS `WalletDocumentAIExtractor.swift`) e ne ricava
 * i campi strutturati. Riservata al piano Max — il chiamante deve verificare
 * [SubscriptionRepository.getPlan] prima di invocare [extract].
 *
 * Costo in "unità messaggio" del contatore famiglia: il server conta ogni
 * immagine come 1 unità (50.000 caratteri-equivalenti) + il prompt →
 * `nImmagini + 1`, vedi [estimatedMessageUnits].
 */
@Singleton
class WalletDocumentAIExtractor @Inject constructor(
    private val aiService: AIService,
) {
    suspend fun extract(
        bitmaps: List<Bitmap>,
        kind: DocumentKind,
        familyId: String,
    ): Result<WalletDocumentExtraction> {
        if (bitmaps.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nessuna immagine da analizzare."))
        }
        val blocks = mutableListOf<Map<String, Any>>()
        for (bitmap in bitmaps) {
            val b64 = downscaledJpegBase64(bitmap) ?: continue
            blocks += mapOf(
                "type" to "image",
                "source" to mapOf(
                    "type" to "base64",
                    "media_type" to "image/jpeg",
                    "data" to b64,
                ),
            )
        }
        if (blocks.isEmpty()) {
            return Result.failure(IllegalStateException("Impossibile preparare le immagini."))
        }
        blocks += mapOf("type" to "text", "text" to userPrompt(kind))

        val payload = listOf(AIMessagePayload(role = "user", content = blocks))
        val result = aiService.sendMessages(
            messages = payload,
            systemPrompt = SYSTEM_PROMPT,
            familyId = familyId,
            purpose = PURPOSE,
        )
        return result.mapCatching { response ->
            val reply = response.reply.trim()
            if (reply.isEmpty()) error("L'AI non ha restituito dati.")
            parse(reply, kind) ?: error("Risposta AI non interpretabile.")
        }
    }

    private fun downscaledJpegBase64(bitmap: Bitmap): String? = runCatching {
        val longSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longSide > MAX_IMAGE_SIDE) {
            val scale = MAX_IMAGE_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun userPrompt(kind: DocumentKind): String =
        "Tipo documento indicato dall'utente: ${kind.displayName}. Estrai i dati in JSON."

    private fun parse(reply: String, kind: DocumentKind): WalletDocumentExtraction? {
        var text = reply.trim()
        if (text.startsWith("```")) {
            val firstNl = text.indexOf('\n')
            if (firstNl >= 0) text = text.substring(firstNl + 1)
            val fenceIdx = text.lastIndexOf("```")
            if (fenceIdx >= 0) text = text.substring(0, fenceIdx)
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || start >= end) return null
        val json = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null

        fun str(key: String): String? =
            json.optString(key, "").trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

        fun date(key: String): LocalDate? = str(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val categories = mutableListOf<PatenteCategory>()
        json.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val code = obj.optString("code", "").trim().uppercase()
                if (code.isEmpty()) continue
                val issue = obj.optString("issueDate", "").trim()
                    .takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val expiry = obj.optString("expiryDate", "").trim()
                    .takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                categories += PatenteCategory(code = code, issueDate = issue, expiryDate = expiry)
            }
        }

        val isPatente = kind == DocumentKind.PATENTE
        return WalletDocumentExtraction(
            codiceFiscale = if (isPatente) null else str("codiceFiscale"),
            holderName = str("holderName"),
            birthInfo = str("birthInfo"),
            documentNumber = str("documentNumber"),
            issueDate = if (isPatente) null else date("issueDate"),
            expiryDate = if (isPatente) null else date("expiryDate"),
            patenteCategories = if (isPatente) categories else emptyList(),
            rawText = reply,
        )
    }

    companion object {
        private const val PURPOSE = "wallet_doc"
        private const val MAX_IMAGE_SIDE = 1600
        private const val JPEG_QUALITY = 60

        /** Stessa formula del server (`askAIMessageUnitsForPayload`): 1 unità per immagine + prompt. */
        fun estimatedMessageUnits(imageCount: Int): Int = maxOf(1, imageCount + 1)

        private val SYSTEM_PROMPT = """
            Sei un estrattore di dati da documenti d'identità italiani (tessera sanitaria,
            carta d'identità, CIE, patente di guida, passaporto, codice fiscale).
            Ti vengono fornite una o più immagini (fronte/retro). Estrai i dati leggibili.

            Rispondi ESCLUSIVAMENTE con un oggetto JSON valido, senza testo prima o dopo,
            senza markdown, senza ```. Schema:
            {
              "holderName": "Nome Cognome del titolare, o null",
              "birthInfo": "data e luogo di nascita come testo, o null",
              "documentNumber": "numero del documento, o null",
              "codiceFiscale": "codice fiscale a 16 caratteri se presente, o null",
              "issueDate": "AAAA-MM-GG o null",
              "expiryDate": "AAAA-MM-GG o null",
              "categories": [ {"code":"B","issueDate":"AAAA-MM-GG o null","expiryDate":"AAAA-MM-GG o null"} ]
            }

            REGOLE:
            - Non inventare dati: se un campo non è leggibile, usa null.
            - Le date SEMPRE in formato AAAA-MM-GG. Anno a 2 cifre: 00-49 -> 2000+, 50-99 -> 1900+.
            - PATENTE: il numero è al campo 5 del fronte. Le categorie con rilascio e
            scadenza sono nella tabella sul retro (colonna 9 = categoria, colonna 10 =
            rilascio, colonna 11 = scadenza). Includi in "categories" SOLO le categorie
            che hanno almeno una data. NON usare le date dei campi 4a/4b/4c del fronte.
            La patente NON ha codice fiscale: metti null.
            - TESSERA SANITARIA: "codiceFiscale" è quello a 16 caratteri; "categories" resta [].
            - Per i documenti diversi dalla patente lascia "categories": [].
        """.trimIndent()
    }
}
