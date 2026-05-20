package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Contesto sanitario per askAI cartella clinica (senza pianificazione/veicoli). */
object ClinicalRecordHealthContextBuilder {

    fun buildClinicalPrompt(
        subjectName: String,
        treatments: List<KBTreatmentEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        health: HealthImportSnapshot?,
        healthLabel: String,
        birthDateEpochMillis: Long?,
        refertoMaxChars: Int = 2500,
    ): String = buildString {
        appendLine("CONTESTO DATI CLINICI KidBox (solo salute: visite, esami, cure, referti).")
        appendLine("NON usare né menzionare: veicoli, garage, casa, animali, lista spesa, to-do, calendario.")
        appendLine()
        appendLine("--- PROFILO ---")
        appendLine("Nome: $subjectName")
        appendLine("Cure attive: ${treatments.count { it.isActive }}")
        appendLine("Visite: ${visits.size}")
        appendLine("Esami: ${exams.size}")
        visits.sortedByDescending { it.dateEpochMillis }.take(8).forEach { v ->
            appendLine("• Visita ${formatDate(v.dateEpochMillis)}: ${v.reason}")
            v.diagnosis?.takeIf { it.isNotBlank() }?.let { appendLine("  Diagnosi: ${clip(it, refertoMaxChars)}") }
        }
        exams.take(12).forEach { e ->
            appendLine("• Esame ${e.name} [${e.statusRaw}]")
            e.resultText?.takeIf { it.isNotBlank() }?.let {
                appendLine("  Risultato: ${clip(it, refertoMaxChars)}")
            }
        }
        health?.let {
            ClinicalRecordAppleHealthNarrative.analyze(it, birthDateEpochMillis, visits)?.let { analysis ->
                appendLine()
                appendLine("--- $healthLabel / WEARABLE ---")
                appendLine(analysis.narrative)
            }
        }
        appendLine()
        appendLine("--- FINE DATI CLINICI ---")
    }

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max) + "…"

    private fun formatDate(epoch: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epoch))
}
