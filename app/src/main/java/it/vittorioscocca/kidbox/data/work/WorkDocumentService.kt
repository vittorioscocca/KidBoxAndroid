package it.vittorioscocca.kidbox.data.work

import it.vittorioscocca.kidbox.data.docintel.DocIntelBadResponseException
import it.vittorioscocca.kidbox.data.docintel.DocIntelUnreadableException
import it.vittorioscocca.kidbox.data.docintel.DocumentIntelligenceService
import it.vittorioscocca.kidbox.data.remote.ai.AIMessagePayload
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.domain.work.WorkFields
import it.vittorioscocca.kidbox.domain.work.WorkLeavePeriod
import it.vittorioscocca.kidbox.domain.work.WorkRecordKind
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Il documento non è né una busta paga, né un contratto, né un foglio ferie. */
class WorkNotAWorkDocumentException : Exception("not a work document")

data class WorkAnalysis(
    val kind: String,
    val fields: WorkFields,
    val referenceDate: LocalDate,
)

/**
 * Legge una busta paga, un contratto o un foglio ferie (vision, Claude Haiku) e
 * ne estrae i valori. Gemello di `WorkDocumentService.swift`: stesso prompt,
 * purpose `workDoc` (solo Pro e Max, bloccato anche dal server), 1 messaggio
 * AI per pagina letta, al massimo 3 pagine.
 */
@Singleton
class WorkDocumentService @Inject constructor(
    private val aiService: AIService,
    private val docIntel: DocumentIntelligenceService,
) {
    suspend fun analyze(
        file: File,
        mimeType: String,
        familyId: String,
        labels: LeaveLabels,
    ): Result<WorkAnalysis> {
        val images = withContext(Dispatchers.IO) { docIntel.renderImages(file, mimeType) }
        if (images.isEmpty()) return Result.failure(DocIntelUnreadableException())

        val blocks = images.map { b64 ->
            mapOf(
                "type" to "image",
                "source" to mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to b64),
            )
        } + mapOf("type" to "text", "text" to "File: ${file.name}. Leggi il documento e rispondi con il JSON richiesto.")

        return aiService.sendMessages(
            messages = listOf(AIMessagePayload(role = "user", content = blocks)),
            systemPrompt = SYSTEM_PROMPT,
            familyId = familyId,
            purpose = PURPOSE,
        ).mapCatching { parse(it.reply, labels) }
    }

    /** Le tre etichette che il codice conosce, già tradotte (la sigla PAR diventa «Ferie»). */
    data class LeaveLabels(val ferie: String, val permessoRol: String, val malattia: String)

    private fun parse(reply: String, labels: LeaveLabels): WorkAnalysis {
        var text = reply.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n', text)
            text = text.substringBeforeLast("```", text)
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw DocIntelBadResponseException()
        val root = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
            ?: throw DocIntelBadResponseException()
        val kind = root.optString("kind", "")
        if (kind !in listOf(WorkRecordKind.PAYSLIP, WorkRecordKind.CONTRACT, WorkRecordKind.LEAVE_PLAN)) {
            throw WorkNotAWorkDocumentException()
        }
        val fieldsObj = root.optJSONObject("fields") ?: throw WorkNotAWorkDocumentException()
        var fields = WorkFields.fromObject(fieldsObj)
        if (kind == WorkRecordKind.LEAVE_PLAN) {
            // In bozza ogni periodo parte con «Aggiungi al calendario» acceso: lo spegne chi non lo vuole.
            val periods = fields.leavePeriods
                .filter { it.startDay != null }
                .sortedBy { it.startDay }
                .map {
                    it.copy(
                        inCalendar = true,
                        label = WorkLeavePeriod.cleanLabel(it.label, labels.ferie, labels.permessoRol, labels.malattia),
                    )
                }
            if (periods.isEmpty()) throw WorkNotAWorkDocumentException()
            fields = fields.copy(leavePeriods = periods)
        }
        val ref = WorkFields.parseDay(root.optString("referenceDate", ""))
            ?: fields.leavePeriods.firstOrNull()?.startDay
            ?: WorkFields.parseDay(fields.startDate)
            ?: LocalDate.now()
        return WorkAnalysis(kind, fields, ref)
    }

    private companion object {
        const val PURPOSE = "workDoc"

        val SYSTEM_PROMPT = """
Sei l'assistente di KidBox. Leggi un documento di lavoro (immagini delle pagine) e ne estrai i valori. Rispondi ESCLUSIVAMENTE con un oggetto JSON valido (nessun testo prima o dopo, niente markdown, niente ```), con questa forma:
{
  "kind": "payslip|contract|leavePlan|other",
  "referenceDate": "yyyy-MM-dd",
  "fields": {
    "title": "titolo breve, es. Busta paga settembre 2026 / Contratto a tempo indeterminato",
    "employer": "datore di lavoro",
    "period": "mese di competenza della busta paga, es. Settembre 2026",
    "gross": 0, "net": 0, "taxes": 0, "contributions": 0,
    "vacationAccrued": 0, "vacationTaken": 0, "vacationRemaining": 0,
    "permitHoursAccrued": 0, "permitHoursRemaining": 0, "tfr": 0,
    "contractType": "tempo indeterminato|tempo determinato|apprendistato|partita IVA|altro",
    "startDate": "yyyy-MM-dd", "endDate": "yyyy-MM-dd",
    "grossAnnual": 0, "level": "livello e/o CCNL", "noticePeriod": "preavviso",
    "vacationDaysPerYear": 0, "notes": "altro di rilevante",
    "leavePeriods": [{"start": "yyyy-MM-dd", "end": "yyyy-MM-dd", "label": "Ferie|Permesso|ROL|Malattia|altro"}]
  }
}

REGOLE:
- "payslip" = busta paga / cedolino; "contract" = contratto di lavoro o lettera di assunzione/proroga; "leavePlan" = foglio, piano o calendario delle ferie e dei permessi con date precise; altro documento → "other" con "fields": {}.
- NON INVENTARE. Se un valore non c'è nel documento OMETTI il campo: niente zeri di ripiego.
- Importi come numeri con il punto decimale, in euro, senza simbolo. "gross" = totale competenze lorde del mese; "net" = netto in busta; "taxes" = IRPEF e addizionali trattenute nel mese; "contributions" = contributi previdenziali a carico del lavoratore (INPS).
- Ferie in GIORNI (o in ORE se così è scritto: in tal caso convertile solo se il documento dice quante ore valgono un giorno, altrimenti riporta il numero e scrivilo in "notes"). "vacationRemaining" è il saldo residuo, "vacationAccrued" quanto maturato nel periodo o nell'anno, "vacationTaken" il goduto.
- Permessi (ROL, ex festività) in ORE: "permitHoursRemaining" è il saldo residuo.
- "leavePlan": in "leavePeriods" metti SOLO i giorni di assenza segnati nel documento, uno per periodo; raggruppa i giorni consecutivi in un solo periodo (start = primo giorno, end = ultimo), un giorno solo ha start = end. Non aggiungere giorni non segnati, non includere festivi o weekend che non sono indicati. Se manca l'anno, usa quello più probabile dal contesto del documento. "label" è il tipo di assenza come scritto ma in italiano esteso: una sigla va sciolta (PAR → «Ferie», ROL → «Permesso (ROL)», FER → «Ferie», MAL → «Malattia»); mai una sigla da sola. "referenceDate" è l'inizio del primo periodo; "title" es. «Ferie 2026».
- "referenceDate": per la busta paga il primo giorno del mese di competenza; per il contratto la data di inizio. Date in formato yyyy-MM-dd.
- Non dare giudizi né consigli: estrai solo ciò che è scritto.
""".trimIndent()
    }
}
