package it.vittorioscocca.kidbox.data.docintel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.remote.ai.AIMessagePayload
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.util.KBLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Né immagine né PDF: niente da far leggere. */
class DocIntelUnreadableException : Exception("unreadable")

/** La risposta non era il JSON atteso. */
class DocIntelBadResponseException : Exception("bad response")

/**
 * Legge un documento (vision, Claude Haiku) e propone le azioni nelle sezioni
 * giuste. Gemello di `DocumentIntelligenceService.swift`: stesso prompt, stesso
 * purpose, stesse 3 pagine al massimo (ogni pagina = 1 messaggio AI).
 */
@Singleton
class DocumentIntelligenceService @Inject constructor(
    private val aiService: AIService,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val vehicleDao: VehicleDao,
) {

    /** Persone di Salute (figli, poi adulti) e veicoli, per l'AI e per la scheda. */
    suspend fun familyRefs(familyId: String): Pair<List<DocIntelPerson>, List<DocIntelVehicle>> =
        withContext(Dispatchers.IO) {
            val kids = childDao.getChildrenByFamilyId(familyId).map { DocIntelPerson(it.id, it.name, isAdult = false) }
            val adults = memberDao.getAllByFamilyId(familyId)
                .filter { !it.isDeleted }
                .mapNotNull { m ->
                    val name = m.displayName?.trim().orEmpty()
                    if (name.isEmpty()) null else DocIntelPerson(m.userId, name, isAdult = true)
                }
                .distinctBy { it.id }
            val vehicles = vehicleDao.listActiveByFamily(familyId).map { v ->
                val label = listOfNotNull(v.name, v.brand, v.model).filter { it.isNotBlank() }.joinToString(" ")
                DocIntelVehicle(v.id, label.ifBlank { v.name })
            }
            (kids + adults) to vehicles
        }

    suspend fun analyze(
        file: File,
        mimeType: String,
        familyId: String,
        persons: List<DocIntelPerson>,
        vehicles: List<DocIntelVehicle>,
    ): Result<DocIntelResult> {
        val images = withContext(Dispatchers.IO) { renderImages(file, mimeType) }
        if (images.isEmpty()) return Result.failure(DocIntelUnreadableException())

        val blocks = images.map { b64 ->
            mapOf(
                "type" to "image",
                "source" to mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to b64),
            )
        } + mapOf("type" to "text", "text" to userPrompt(file.name, persons, vehicles))

        return aiService.sendMessages(
            messages = listOf(AIMessagePayload(role = "user", content = blocks)),
            systemPrompt = SYSTEM_PROMPT,
            familyId = familyId,
            purpose = PURPOSE,
        ).mapCatching { response ->
            parse(response.reply) ?: throw DocIntelBadResponseException()
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    /** Anche per la scheda Lavoro (`WorkDocumentService`): stesse pagine, stesso costo. */
    internal fun renderImages(file: File, mimeType: String): List<String> {
        if (mimeType.startsWith("image/")) {
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return emptyList()
            return listOfNotNull(jpegBase64(bmp))
        }
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until minOf(renderer.pageCount, MAX_PAGES)).mapNotNull { i ->
                        renderer.openPage(i).use { page ->
                            val scale = minOf(MAX_IMAGE_SIDE.toFloat() / maxOf(page.width, page.height), 2f)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            jpegBase64(bmp)
                        }
                    }
                }
            }
        }.onFailure { KBLog.ai.warning("DocIntel: PDF non leggibile ${it.message}", TAG) }.getOrDefault(emptyList())
    }

    private fun jpegBase64(bitmap: Bitmap): String? = runCatching {
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

    // ── Prompt ───────────────────────────────────────────────────────────

    private fun userPrompt(fileName: String, persons: List<DocIntelPerson>, vehicles: List<DocIntelVehicle>): String {
        val kids = persons.filter { !it.isAdult }
        val adults = persons.filter { it.isAdult }
        return buildString {
            append("Data odierna: ${Instant.now()}. Nome file originale: \"$fileName\".\n")
            if (kids.isEmpty()) {
                append("Figli in famiglia: nessuno.\n")
            } else {
                append("Figli in famiglia (id → nome): ${kids.joinToString("; ") { "${it.id} → ${it.name}" }}.\n")
            }
            if (adults.isNotEmpty()) {
                append("Adulti in famiglia (id → nome): ${adults.joinToString("; ") { "${it.id} → ${it.name}" }}.\n")
            }
            if (vehicles.isEmpty()) {
                append("Veicoli: nessuno.\n")
            } else {
                append("Veicoli (id → descrizione): ${vehicles.joinToString("; ") { "${it.id} → ${it.label}" }}.\n")
            }
            append("Analizza le pagine, classifica il documento e proponi le azioni in JSON.")
        }
    }

    // ── Parse ────────────────────────────────────────────────────────────

    private fun parse(reply: String): DocIntelResult? {
        val text = reply.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null
        val arr = obj.optJSONArray("actions") ?: JSONArray()
        val actions = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toAction() }
        return DocIntelResult(
            documentType = obj.str("documentType"),
            category = obj.str("category"),
            suggestedTitle = obj.str("suggestedTitle"),
            actions = actions,
        )
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).trim().takeIf { it.isNotEmpty() } else null

    private fun JSONObject.dbl(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.int(key: String): Int? = dbl(key)?.toInt()

    private fun JSONObject.bool(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.toAction(): DocIntelAction? {
        val type = str("type") ?: return null
        return DocIntelAction(
            type = type,
            summary = str("summary"),
            title = str("title"),
            body = str("body"),
            notes = str("notes"),
            amount = dbl("amount"),
            date = str("date"),
            endDate = str("endDate"),
            isAllDay = bool("isAllDay"),
            childId = str("childId"),
            vehicleId = str("vehicleId"),
            vehicleEventType = str("vehicleEventType"),
            doctorName = str("doctorName"),
            vaccineType = str("vaccineType"),
            renameTo = str("renameTo"),
            diagnosis = str("diagnosis"),
            recommendations = str("recommendations"),
            resultText = str("resultText"),
            location = str("location"),
            activeIngredient = str("activeIngredient"),
            dosageValue = dbl("dosageValue"),
            dosageUnit = str("dosageUnit"),
            dailyFrequency = int("dailyFrequency"),
            durationDays = int("durationDays"),
            isLongTerm = bool("isLongTerm"),
            paymentType = str("paymentType"),
            subtype = str("subtype"),
            provider = str("provider"),
        )
    }

    private companion object {
        const val TAG = "DocumentIntelligence"
        const val PURPOSE = "doc_intelligence"
        const val MAX_PAGES = 3
        const val MAX_IMAGE_SIDE = 1600
        const val JPEG_QUALITY = 60

        // Stesso testo di `DocumentIntelligenceService.swift`: se cambia lì, cambia qui.
        val SYSTEM_PROMPT = """
Sei l'assistente di KidBox, un'app di gestione familiare. Analizzi un documento (immagini delle pagine), capisci CHE COS'È e lo smisti nella sezione giusta dell'app, compilando la scheda di quella sezione con i dati letti.

Rispondi ESCLUSIVAMENTE con un oggetto JSON valido (nessun testo prima o dopo, niente markdown, niente ```), con questa forma:
{
  "documentType": "tipo di documento in italiano (es. bolletta della luce, referto di visita, analisi del sangue, ricetta)",
  "category": "bill|medical_report|lab_results|prescription|receipt|vehicle|school|insurance|other",
  "suggestedTitle": "titolo breve e leggibile per il documento, senza estensione",
  "actions": [ ...azioni... ]
}

PRIMA classifica, POI scegli l'azione principale:
- bill (bolletta luce/gas/acqua/internet/telefono/condominio, tassa IMU/TARI, rata mutuo/affitto) → house_payment. NON aggiungere expense_add: la spesa nasce da sola dal pagamento di Casa.
- medical_report (referto o relazione di una visita) → medical_visit con medico, data, diagnosis (cosa ha detto il medico) e recommendations (cosa fare). Se prescrive farmaci aggiungi anche treatment_add; se prescrive esami aggiungi exam_add senza resultText; se fissa un controllo aggiungi health_reminder.
- lab_results (analisi del sangue, urine, radiografia, ecografia…) → exam_add con resultText che riporta TUTTI i valori letti (nome, valore, unità, intervallo di riferimento; segna con * quelli fuori range).
- prescription (ricetta, piano terapeutico, foglio di terapia) → un treatment_add per ogni farmaco.
- receipt (scontrino, fattura di acquisto) → expense_add.
- vehicle (bollo, assicurazione, revisione, tagliando) → vehicle_event (se il veicolo combacia) altrimenti event_add.
- altro → event_add / todo_add / note_add solo se servono davvero.

Ogni azione è un oggetto con "type", una "summary" (frase breve in italiano che descrive cosa farà l'azione) e i campi pertinenti. Date sempre in ISO8601 UTC. Tipi disponibili:
- house_payment: pagamento/scadenza di Casa. {"type":"house_payment","paymentType":"bolletta|tassa|affitto|mutuo|altro","subtype":"luce|gas|acqua|internet|telefono|condominio|IMU|TARI|...","title":"nome breve es. Bolletta luce Enel ottobre","provider":"fornitore","amount":84.20,"date":"<scadenza del pagamento>","notes":"periodo, numero cliente, consumi","summary":"..."}
- medical_visit: visita medica. {"type":"medical_visit","childId":"<id persona>","doctorName":"...","title":"motivo della visita","date":"...","diagnosis":"cosa ha detto il medico","recommendations":"indicazioni e terapia","notes":"...","summary":"..."}
- exam_add: esame o analisi. {"type":"exam_add","childId":"<id persona>","title":"nome dell'esame","date":"<data del prelievo/esecuzione>","resultText":"valori letti, uno per riga","location":"laboratorio o struttura","notes":"...","summary":"..."}
- treatment_add: cura con un farmaco. {"type":"treatment_add","childId":"<id persona>","title":"nome del farmaco","activeIngredient":"...","dosageValue":5,"dosageUnit":"ml|mg|compresse|gocce|bustine|supposte|puff|UI","dailyFrequency":2,"durationDays":7,"isLongTerm":false,"date":"<inizio>","notes":"istruzioni (es. dopo i pasti)","summary":"..."}
- expense_add: spesa da scontrino/fattura. {"type":"expense_add","title":"...","amount":12.50,"date":"...","summary":"..."}
- event_add: evento/appuntamento. {"type":"event_add","title":"...","date":"<startAt>","endDate":"...","isAllDay":false,"notes":"...","childId":"...","summary":"..."}
- todo_add: cosa da fare. {"type":"todo_add","title":"...","date":"<dueAt>","childId":"...","summary":"..."}
- health_reminder: promemoria sanitario (es. richiamo, controllo). {"type":"health_reminder","title":"...","date":"<dueAt>","childId":"...","summary":"..."}
- note_add: nota/sintesi del documento. {"type":"note_add","title":"...","body":"...","summary":"..."}
- vehicle_event: scadenza/intervento veicolo. {"type":"vehicle_event","vehicleId":"<id veicolo se combacia>","vehicleEventType":"insurance|tax|revision|service|repair|tire|altro","title":"...","date":"...","amount":0,"summary":"..."}
- vaccine_add: vaccino somministrato. {"type":"vaccine_add","childId":"<id figlio>","vaccineType":"esavalente|pneumococco|meningococcoB|mpr|varicella|meningococcoACWY|hpv|influenza|altro","date":"<administeredDate>","title":"nome commerciale","summary":"..."}
- rename_document: rinomina il documento col titolo corretto. {"type":"rename_document","renameTo":"...","summary":"..."}

REGOLE:
- Proponi solo azioni realmente supportate dal contenuto del documento. Non inventare dati.
- "childId" è l'id della PERSONA (figlio o adulto) fra quelle fornite: scegli quella il cui nome compare sul documento come paziente. Se nessuna combacia chiaramente, ometti il campo (lo sceglierà l'utente).
- Usa vehicleId SOLO se uno tra quelli forniti combacia chiaramente; altrimenti ometti il campo.
- Includi quasi sempre "rename_document" con un titolo pulito. Non aggiungere note_add se l'azione principale contiene già il contenuto del documento.
- Se il documento non contiene nulla di utile, ritorna "actions": [].
- Gli importi sono numeri (punto decimale), senza simbolo di valuta.
        """.trimIndent()
    }
}
