package it.vittorioscocca.kidbox.data.health.ai

import android.util.Log
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONException

private val DATE_FMT = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private const val TAG = "HealthContextBuilder"

fun computeScopeId(
    subjectId: String,
    examIds: List<String>,
    visitIds: List<String>,
    treatmentIds: List<String>,
    vaccineIds: List<String>,
): String {
    val hash = (examIds + visitIds + treatmentIds + vaccineIds)
        .sorted()
        .joinToString("-")
        .hashCode()
    return "health-overview-$subjectId-${abs(hash)}"
}

object HealthContextBuilder {

    fun buildSystemPrompt(
        subjectName: String,
        subjectId: String,
        exams: List<KBMedicalExam>,
        visits: List<KBMedicalVisit>,
        treatments: List<KBTreatment>,
        vaccines: List<KBVaccine>,
        documentsByExamId: Map<String, List<KBDocumentEntity>> = emptyMap(),
        documentsByVisitId: Map<String, List<KBDocumentEntity>> = emptyMap(),
    ): String {
        val now = System.currentTimeMillis()
        val sb = StringBuilder()

        sb.appendLine(
            """
Sei un assistente medico informativo integrato nell'app KidBox, pensata per genitori.
Il tuo ruolo è offrire una visione d'insieme chiara e comprensibile della salute della persona.

REGOLE IMPORTANTI:
- Se l'utente chiede una diagnosi o un parere clinico vincolante, ricordagli gentilmente, dopo aver dato il tuo parere, di consultare il proprio medico.
- Usa un linguaggio semplice, adatto a un genitore non esperto.
- Puoi aiutare a capire cure in corso, vaccini, visite recenti, esami in attesa e referti allegati.
- Se nei documenti ci sono testi estratti, usali per contestualizzare meglio.
- Rispondi sempre in italiano.
            """.trimIndent(),
        )

        sb.appendLine()
        sb.appendLine("--- PROFILO: $subjectName ---")

        // ── Treatments ──────────────────────────────────────────────────────────
        val activeTreatments = treatments.filter { t ->
            t.isActive && !t.isDeleted &&
                (t.isLongTerm || t.endDateEpochMillis == null || t.endDateEpochMillis >= now)
        }
        sb.appendLine()
        sb.appendLine("--- CURE ATTIVE (${activeTreatments.size}) ---")
        activeTreatments.forEach { t ->
            val dosageStr = formatDosage(t.dosageValue)
            val endDate = t.endDateEpochMillis?.let { DATE_FMT.format(Date(it)) } ?: "in corso"
            val notesStr = if (!t.notes.isNullOrBlank()) " — ${t.notes}" else ""
            sb.appendLine("- ${t.drugName} — $dosageStr ${t.dosageUnit}, ${t.dailyFrequency}x/giorno, ${t.durationDays} giorni (fine: $endDate)$notesStr")
        }

        // ── Vaccines ─────────────────────────────────────────────────────────────
        val sortedVaccines = vaccines
            .filter { !it.isDeleted }
            .sortedByDescending { it.administeredDateEpochMillis ?: it.scheduledDateEpochMillis ?: it.createdAtEpochMillis }
        sb.appendLine()
        sb.appendLine("--- VACCINI (${sortedVaccines.size}) ---")
        sortedVaccines.forEach { v ->
            val status = v.computedStatus().rawValue
            val typeStr = if (v.vaccineTypeRaw.isNotBlank()) " ${v.vaccineTypeRaw}" else ""
            val dateStr = when {
                v.administeredDateEpochMillis != null -> "somministrato il ${DATE_FMT.format(Date(v.administeredDateEpochMillis))}"
                v.scheduledDateEpochMillis != null -> "programmato il ${DATE_FMT.format(Date(v.scheduledDateEpochMillis))}"
                else -> ""
            }
            sb.appendLine("- ${v.name} [$status]$typeStr${if (dateStr.isNotBlank()) " — $dateStr" else ""}")
        }

        // ── Visits ───────────────────────────────────────────────────────────────
        val sortedVisits = visits
            .filter { !it.isDeleted }
            .sortedByDescending { it.dateEpochMillis }
            .take(10)
        sb.appendLine()
        sb.appendLine("--- VISITE (${sortedVisits.size}) ---")
        sortedVisits.forEach { v ->
            val dateStr = DATE_FMT.format(Date(v.dateEpochMillis))
            val statusStr = v.visitStatusRaw?.takeIf { it.isNotBlank() } ?: "sconosciuto"
            sb.appendLine("- $dateStr — ${v.reason.ifBlank { "Visita medica" }} [$statusStr]")
            val doctorParts = listOfNotNull(v.doctorName?.takeIf { it.isNotBlank() }, v.doctorSpecializationRaw?.takeIf { it.isNotBlank() })
            if (doctorParts.isNotEmpty()) sb.appendLine("  Medico: ${doctorParts.joinToString(" — ")}")
            if (!v.diagnosis.isNullOrBlank()) sb.appendLine("  Diagnosi: ${v.diagnosis}")
            if (!v.recommendations.isNullOrBlank()) sb.appendLine("  Raccomandazioni: ${v.recommendations}")
            val examsStr = parsePrescribedExams(v.prescribedExamsJson)
            if (examsStr.isNotBlank()) sb.appendLine("  Esami prescritti: $examsStr")
            if (!v.notes.isNullOrBlank()) sb.appendLine("  Note: ${v.notes}")
            if (v.nextVisitDateEpochMillis != null) {
                val nextDateStr = DATE_FMT.format(Date(v.nextVisitDateEpochMillis))
                val nextReason = v.nextVisitReason?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                sb.appendLine("  Prossima visita: $nextDateStr$nextReason")
            }
            documentsByVisitId[v.id]?.forEach { doc ->
                val text = doc.extractedText?.takeIf {
                    it.isNotBlank() && doc.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue
                }
                if (text != null) {
                    val prepared = HealthAiDocumentText.prepareExtractedTextForAi(text)
                    if (prepared.isNotBlank()) {
                        sb.appendLine("  Referto allegato (${doc.title}):")
                        prepared.lines().forEach { line -> sb.appendLine("  $line") }
                    }
                }
            }
        }

        // ── Exams ────────────────────────────────────────────────────────────────
        val sortedExams = exams
            .filter { !it.isDeleted }
            .sortedWith(compareBy(nullsLast()) { it.deadlineEpochMillis })
        sb.appendLine()
        sb.appendLine("--- ESAMI (${sortedExams.size}) ---")
        val overdueStatuses = setOf(KBExamStatus.PENDING.rawValue, KBExamStatus.BOOKED.rawValue)
        sortedExams.forEach { e ->
            val deadlineStr = e.deadlineEpochMillis?.let { "scadenza: ${DATE_FMT.format(Date(it))}" } ?: "senza scadenza"
            val isOverdue = e.deadlineEpochMillis != null && e.deadlineEpochMillis < now && e.statusRaw in overdueStatuses
            val urgentStr = if (e.isUrgent) " {URGENTE}" else ""
            val overdueStr = if (isOverdue) " ⚠️ SCADUTA" else ""
            val resultStr = e.resultText?.takeIf { it.isNotBlank() }?.let { raw ->
                val clipped = HealthAiDocumentText.prepareExtractedTextForAi(raw)
                if (clipped.isBlank()) "" else " — Risultato: $clipped"
            } ?: ""
            sb.appendLine("- ${e.name} [${e.statusRaw}]$urgentStr — $deadlineStr$overdueStr$resultStr")
            documentsByExamId[e.id]?.forEach { doc ->
                val text = doc.extractedText?.takeIf {
                    it.isNotBlank() && doc.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue
                }
                if (text != null) {
                    val prepared = HealthAiDocumentText.prepareExtractedTextForAi(text)
                    if (prepared.isNotBlank()) {
                        sb.appendLine("  Referto allegato (${doc.title}):")
                        prepared.lines().forEach { line -> sb.appendLine("  $line") }
                    }
                }
            }
        }

        sb.appendLine()
        sb.appendLine("--- FINE CONTESTO SALUTE ---")
        sb.append("Rispondi alle domande usando le informazioni sopra.")

        val prompt = sb.toString()
        Log.d(TAG, "buildSystemPrompt for $subjectName: ${prompt.take(200)}...")
        return prompt
    }

    private fun formatDosage(value: Double): String =
        if (value == floor(value)) "%.0f".format(value) else "%.1f".format(value)

    private fun parsePrescribedExams(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.getJSONObject(i).optString("name").takeIf { it.isNotBlank() } }.getOrNull()
            }.joinToString(", ")
        } catch (_: JSONException) {
            ""
        }
    }
}
