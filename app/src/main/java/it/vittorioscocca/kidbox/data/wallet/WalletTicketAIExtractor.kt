package it.vittorioscocca.kidbox.data.wallet

import android.graphics.Bitmap
import android.util.Base64
import it.vittorioscocca.kidbox.data.remote.ai.AIMessagePayload
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** Campi biglietto letti dall'AI: partenza/arrivo separati, titolare, codice. */
data class WalletTicketExtraction(
    val holderName: String? = null,
    val bookingCode: String? = null,
    val emitter: String? = null,
    val kind: WalletTicketKind? = null,
    val departureLocation: String? = null,
    val departureDateTimeEpochMillis: Long? = null,
    val arrivalLocation: String? = null,
    val arrivalDateTimeEpochMillis: Long? = null,
    val rawText: String = "",
)

/**
 * Lettura assistita AI dei biglietti Wallet (stessa Cloud Function `askAI`
 * dei documenti d'identità, nuovo `purpose = "wallet_ticket"`; nessuna
 * modifica backend: il `purpose` è gestito interamente lato client — vedi
 * `functions/index.js`, che non fa branching su nessun valore tranne
 * `"clinicalRecord"`).
 *
 * A differenza dei documenti d'identità (foto scattate con lo scanner), un
 * biglietto PDF ha quasi sempre un layer di testo reale già estratto da
 * `WalletPdfParser` (PDFBox): mandare quel testo all'AI è più preciso (nessun
 * errore OCR/vision) e più economico (1 unità messaggio invece di 1 per
 * immagine). Se il testo è troppo corto (biglietto scansionato come
 * immagine), ripiego su un'immagine della prima pagina, stesso schema di
 * [WalletDocumentAIExtractor].
 */
@Singleton
class WalletTicketAIExtractor @Inject constructor(
    private val aiService: AIService,
) {
    suspend fun extract(
        text: String?,
        fallbackBitmap: Bitmap?,
        familyId: String,
    ): Result<WalletTicketExtraction> {
        val trimmed = text?.trim().orEmpty()
        val blocks: List<Map<String, Any>> = when {
            trimmed.length >= MIN_TEXT_LENGTH -> {
                listOf(mapOf("type" to "text", "text" to userPromptForText(trimmed.take(MAX_TEXT_CHARS))))
            }
            fallbackBitmap != null -> {
                val b64 = downscaledJpegBase64(fallbackBitmap)
                    ?: return Result.failure(IllegalStateException("Impossibile preparare l'immagine."))
                listOf(
                    mapOf(
                        "type" to "image",
                        "source" to mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to b64),
                    ),
                    mapOf("type" to "text", "text" to userPromptForImage()),
                )
            }
            else -> return Result.failure(IllegalArgumentException("Nessun testo o immagine da analizzare."))
        }

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
            parse(reply) ?: error("Risposta AI non interpretabile.")
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

    private fun userPromptForText(text: String): String =
        "Testo estratto dal PDF del biglietto:\n\n$text\n\nEstrai i dati in JSON."

    private fun userPromptForImage(): String =
        "L'immagine mostra un biglietto/documento di viaggio. Estrai i dati in JSON."

    private fun parse(reply: String): WalletTicketExtraction? {
        var jsonText = reply.trim()
        if (jsonText.startsWith("```")) {
            val firstNl = jsonText.indexOf('\n')
            if (firstNl >= 0) jsonText = jsonText.substring(firstNl + 1)
            val fenceIdx = jsonText.lastIndexOf("```")
            if (fenceIdx >= 0) jsonText = jsonText.substring(0, fenceIdx)
        }
        val start = jsonText.indexOf('{')
        val end = jsonText.lastIndexOf('}')
        if (start < 0 || end < 0 || start >= end) return null
        val json = runCatching { JSONObject(jsonText.substring(start, end + 1)) }.getOrNull() ?: return null

        fun str(key: String): String? =
            json.optString(key, "").trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

        fun dateTimeMillis(key: String): Long? = str(key)?.let {
            runCatching {
                LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }

        val kind = str("kind")?.let { raw -> WalletTicketKind.entries.firstOrNull { it.raw == raw } }

        return WalletTicketExtraction(
            holderName = str("holderName"),
            bookingCode = str("bookingCode"),
            emitter = str("emitter"),
            kind = kind,
            departureLocation = str("departureLocation"),
            departureDateTimeEpochMillis = dateTimeMillis("departureDateTime"),
            arrivalLocation = str("arrivalLocation"),
            arrivalDateTimeEpochMillis = dateTimeMillis("arrivalDateTime"),
            rawText = reply,
        )
    }

    companion object {
        private const val PURPOSE = "wallet_ticket"
        private const val MIN_TEXT_LENGTH = 40
        private const val MAX_TEXT_CHARS = 6000
        private const val MAX_IMAGE_SIDE = 1600
        private const val JPEG_QUALITY = 60

        /** Stessa formula del server: 1 unità per il testo (sotto 50k caratteri-equivalenti), +1 se serve un'immagine di fallback. */
        fun estimatedMessageUnits(usedImageFallback: Boolean): Int = if (usedImageFallback) 2 else 1

        private val SYSTEM_PROMPT = """
            Sei un estrattore di dati da biglietti/titoli di viaggio italiani ed
            europei (treno, aereo, traghetto, autobus, ma anche cinema/concerti/
            parcheggi/musei). Ti viene fornito il testo estratto dal PDF del
            biglietto (o, in mancanza di testo, un'immagine del biglietto).

            Rispondi ESCLUSIVAMENTE con un oggetto JSON valido, senza testo prima o
            dopo, senza markdown, senza ```. Schema:
            {
              "holderName": "nome e cognome del titolare/passeggero, o null",
              "bookingCode": "codice di prenotazione/PNR/biglietto, o null",
              "emitter": "nome del vettore/emittente (es. Trenitalia, Ryanair), o null",
              "kind": "uno tra flight|train|ferry|bus|concert|cinema|parking|museum|other, o null",
              "departureLocation": "luogo/stazione/aeroporto di partenza, o null",
              "departureDateTime": "AAAA-MM-GGTHH:MM (partenza) o null",
              "arrivalLocation": "luogo/stazione/aeroporto di arrivo, o null",
              "arrivalDateTime": "AAAA-MM-GGTHH:MM (arrivo) o null"
            }

            REGOLE:
            - Non inventare dati: se un campo non è presente/leggibile, usa null.
            - Le date/ore SEMPRE in formato AAAA-MM-GGTHH:MM (24h), anno a 4 cifre.
            - Se è indicato solo un orario senza data esplicita, deducila dal contesto
              (es. altre date presenti nel testo); se non è possibile, usa null.
            - Per biglietti che non sono viaggi (cinema, concerto, parcheggio, museo)
              "departureLocation"/"departureDateTime" rappresentano semplicemente
              luogo e orario dell'evento; "arrivalLocation"/"arrivalDateTime" restano null.
        """.trimIndent()
    }
}
