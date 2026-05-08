package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.domain.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PediatricAdvancedInput(
    val familyId: String,
    val child: KBChild,
    val profile: KBPediatricProfile?,
    val subjectId: String,
    val allVisits: List<KBMedicalVisit>,
    val allExams: List<KBMedicalExam>,
    val allTreatments: List<KBTreatment>,
    val allVaccines: List<KBVaccine>,
    val historicDays: Int = 365,
)

object PediatricAdvancedContextBuilder {
    private val locale = Locale("it", "IT")

    fun build(input: PediatricAdvancedInput): String {
        val now = System.currentTimeMillis()
        val birth = input.child.birthDateEpochMillis
        val ageMonths = if (birth != null) ((now - birth) / (30L * 24 * 60 * 60 * 1000)).toInt() else 0
        val ageYears = ageMonths / 12
        val remMonths = ageMonths % 12
        val visits = input.allVisits.filter { it.childId == input.subjectId }.sortedByDescending { it.dateEpochMillis }.take(20)
        return buildString {
            appendLine("### Profilo ${input.child.name} (età: $ageYears anni $remMonths mesi | n/d)")
            appendLine("Data nascita: ${birth?.let(::formatDate) ?: "—"}")
            input.profile?.let {
                appendLine("  Gruppo sanguigno: ${it.bloodGroup ?: "Non indicato"}")
                appendLine("  Allergie: ${it.allergies ?: "Nessuna nota"}")
                appendLine("  Note mediche: ${it.medicalNotes ?: "—"}")
                appendLine("  Pediatra: ${it.doctorName ?: "Non indicato"}")
            }
            appendLine("Visite (ultimo anno):")
            if (visits.isEmpty()) appendLine("• Nessuna")
            visits.forEach { v ->
                appendLine("• ${formatDate(v.dateEpochMillis)} ${v.doctorSpecializationRaw ?: "Specialista"} — ${v.reason}")
                appendLine("  Diagnosi: ${v.diagnosis ?: "—"}")
                appendLine("  Raccomandazioni: ${v.recommendations ?: "—"}")
            }
        }.trim()
    }

    fun estimateWeightPercentile(ageMonths: Int, weightKg: Double, isMale: Boolean): String {
        val median = interpolate(ageMonths, if (isMale) listOf(0 to 3.3, 6 to 7.9, 12 to 9.6, 24 to 12.2, 36 to 14.3, 48 to 16.3, 60 to 18.3) else listOf(0 to 3.2, 6 to 7.3, 12 to 8.9, 24 to 11.5, 36 to 13.9, 48 to 16.0, 60 to 18.2))
        return band(weightKg / median)
    }

    fun estimateHeightPercentile(ageMonths: Int, heightCm: Double, isMale: Boolean): String {
        val median = interpolate(ageMonths, if (isMale) listOf(0 to 49.9, 6 to 67.6, 12 to 75.7, 24 to 87.8, 36 to 96.1, 48 to 103.3, 60 to 110.0) else listOf(0 to 49.1, 6 to 65.7, 12 to 74.0, 24 to 86.4, 36 to 95.1, 48 to 102.7, 60 to 109.4))
        return band(heightCm / median)
    }

    private fun band(ratio: Double): String = when {
        ratio < 0.9 -> "< 10°"
        ratio < 0.97 -> "25°"
        ratio <= 1.03 -> "50°"
        ratio <= 1.10 -> "75°"
        else -> "> 90°"
    }

    private fun interpolate(ageMonths: Int, points: List<Pair<Int, Double>>): Double {
        if (ageMonths <= points.first().first) return points.first().second
        if (ageMonths >= points.last().first) return points.last().second
        val low = points.last { it.first <= ageMonths }
        val high = points.first { it.first >= ageMonths }
        if (low.first == high.first) return low.second
        val t = (ageMonths - low.first).toDouble() / (high.first - low.first)
        return low.second + ((high.second - low.second) * t)
    }

    private fun formatDate(epochMillis: Long): String = SimpleDateFormat("d MMM yyyy", locale).format(Date(epochMillis))
}
