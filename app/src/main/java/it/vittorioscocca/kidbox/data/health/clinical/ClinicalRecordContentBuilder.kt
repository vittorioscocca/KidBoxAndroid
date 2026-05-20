package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ClinicalRecordContentBuilder {

    private const val REFERTO_MAX_CHARS = 2_000
    private const val EXTRACTION_COMPLETED = 3

    fun buildLines(
        subjectName: String,
        childBirthDateEpochMillis: Long?,
        profile: KBPediatricProfile?,
        healthSnapshot: HealthImportSnapshot?,
        healthSourceLabel: String,
        treatments: List<KBTreatmentEntity>,
        vaccines: List<KBVaccineEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        documentsByExamId: Map<String, List<KBDocumentEntity>>,
        documentsByVisitId: Map<String, List<KBDocumentEntity>>,
        documentsByTreatmentId: Map<String, List<KBDocumentEntity>>,
    ): List<String> {
        val lines = mutableListOf<String>()
        val locale = Locale.getDefault()
        val dateFmt = DateFormat.getDateInstance(DateFormat.LONG, locale)
        val dateTimeFmt = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale)

        fun formatDate(epoch: Long?): String =
            epoch?.let { dateFmt.format(Date(it)) } ?: ""

        fun formatDateTime(epoch: Long): String = dateTimeFmt.format(Date(epoch))

        lines += "CARTELLA CLINICA"
        lines += "Paziente: $subjectName"
        val birth = childBirthDateEpochMillis ?: healthSnapshot?.birthDateEpochMillis
        if (birth != null) {
            lines += "Data di nascita: ${formatDate(birth)}"
            HealthAgeFormatting.ageDescriptionFromBirth(birth)?.let { lines += "Età: $it" }
        }
        lines += "Generata il: ${formatDateTime(System.currentTimeMillis())}"
        lines += "Documento prodotto da KidBox — solo per uso personale/familiare."

        appendMedicalRecord(profile, lines)
        appendHealthApp(healthSnapshot, healthSourceLabel, lines, ::formatDate, ::formatDateTime)

        val activeTreatments = treatments.filter { it.isActive && !it.isDeleted && it.petId.isBlank() }
        if (activeTreatments.isNotEmpty()) {
            lines += "\n--- CURE ATTIVE (${activeTreatments.size}) ---"
            activeTreatments.forEach { t ->
                var line = "• ${t.drugName} — ${t.dosageValue} ${t.dosageUnit}"
                t.notes?.takeIf { it.isNotBlank() }?.let { line += " — $it" }
                lines += line
                appendDocuments(documentsByTreatmentId[t.id].orEmpty(), lines, "  ")
            }
        }

        if (vaccines.isNotEmpty()) {
            lines += "\n--- VACCINI (${vaccines.size}) ---"
            vaccines.sortedByDescending {
                it.administeredDateEpochMillis ?: it.scheduledDateEpochMillis ?: it.createdAtEpochMillis
            }.forEach { v ->
                val name = v.commercialName?.takeIf { it.isNotBlank() } ?: v.name
                lines += "• $name [${v.statusRaw}]"
            }
        }

        if (visits.isNotEmpty()) {
            lines += "\n--- VISITE MEDICHE (${visits.size}) ---"
            visits.sortedByDescending { it.dateEpochMillis }.forEachIndexed { index, visit ->
                lines += ""
                lines += "VISITA ${index + 1} — ${formatDate(visit.dateEpochMillis)}"
                if (visit.reason.isNotBlank()) lines += "Motivo: ${visit.reason}"
                visit.doctorName?.takeIf { it.isNotBlank() }?.let { lines += "Medico: $it" }
                visit.diagnosis?.takeIf { it.isNotBlank() }?.let { lines += "Diagnosi: $it" }
                visit.recommendations?.takeIf { it.isNotBlank() }?.let { lines += "Raccomandazioni: $it" }
                visit.notes?.takeIf { it.isNotBlank() }?.let { lines += "Note: $it" }
                appendDocuments(documentsByVisitId[visit.id].orEmpty(), lines, "  ")
            }
        }

        if (exams.isNotEmpty()) {
            lines += "\n--- ANALISI ED ESAMI (${exams.size}) ---"
            exams.sortedBy { it.deadlineEpochMillis ?: Long.MAX_VALUE }.forEach { exam ->
                var line = "• ${exam.name} [${exam.statusRaw}]"
                if (exam.isUrgent) line += " [URGENTE]"
                exam.deadlineEpochMillis?.let { line += " — scadenza: ${formatDate(it)}" }
                lines += line
                exam.resultText?.takeIf { it.isNotBlank() }?.let { result ->
                    lines += "  Risultato: ${truncate(result)}"
                }
                appendDocuments(documentsByExamId[exam.id].orEmpty(), lines, "  ")
            }
        }

        lines += "\n--- FINE CARTELLA CLINICA ---"
        return lines
    }

    private fun appendMedicalRecord(profile: KBPediatricProfile?, lines: MutableList<String>) {
        lines += "\n--- SCHEDA MEDICA ---"
        if (profile == null) {
            lines += "Nessuna scheda medica compilata."
            return
        }
        profile.bloodGroup?.takeIf { it.isNotBlank() }?.let { lines += "Gruppo sanguigno: $it" }
        profile.allergies?.takeIf { it.isNotBlank() }?.let { lines += "Allergie: $it" }
        profile.medicalNotes?.takeIf { it.isNotBlank() }?.let { lines += "Note mediche: $it" }
        profile.doctorName?.takeIf { it.isNotBlank() }?.let { lines += "Pediatra / medico: $it" }
        profile.doctorPhone?.takeIf { it.isNotBlank() }?.let { lines += "Tel. medico: $it" }
    }

    private fun appendHealthApp(
        snapshot: HealthImportSnapshot?,
        sourceLabel: String,
        lines: MutableList<String>,
        formatDate: (Long?) -> String,
        formatDateTime: (Long) -> String,
    ) {
        lines += "\n--- ${sourceLabel.uppercase(Locale.getDefault())} ---"
        if (snapshot == null || (!snapshot.hasCardiacOrActivity && snapshot.birthDateEpochMillis == null)) {
            lines += "Nessun dato importato dall'app Salute."
            return
        }
        lines += "Ultimo aggiornamento dati: ${formatDateTime(snapshot.syncedAtEpochMillis)}"
        snapshot.ageDescription?.let { lines += "Età (da app Salute): $it" }
        snapshot.weightKg?.let { lines += "Peso: ${"%.2f".format(Locale.US, it)} kg" }
        snapshot.heartRateBpm?.let { lines += "Frequenza cardiaca: ${it.toInt()} bpm" }
        snapshot.stepsToday?.takeIf { it > 0 }?.let { lines += "Passi oggi: $it" }
        if (snapshot.recentWorkouts.isNotEmpty()) {
            lines += "Allenamenti recenti:"
            snapshot.recentWorkouts.take(15).forEach { w ->
                lines += "  • ${w.title} — ${formatDate(w.startedAtEpochMillis)}"
            }
        }
    }

    private fun appendDocuments(
        docs: List<KBDocumentEntity>,
        lines: MutableList<String>,
        indent: String,
    ) {
        docs.filter { it.extractionStatusRaw == EXTRACTION_COMPLETED && !it.extractedText.isNullOrBlank() }
            .forEach { doc ->
                val clean = truncate(doc.extractedText.orEmpty())
                if (clean.isBlank()) return@forEach
                lines += "${indent}Referto (${doc.title}):"
                clean.lineSequence().forEach { lines += "$indent  $it" }
            }
    }

    private fun truncate(text: String): String =
        if (text.length <= REFERTO_MAX_CHARS) text else text.take(REFERTO_MAX_CHARS) + "…"
}
