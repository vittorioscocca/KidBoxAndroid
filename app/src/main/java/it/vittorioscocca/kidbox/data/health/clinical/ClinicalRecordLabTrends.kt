package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import it.vittorioscocca.kidbox.util.KBLocale

data class LabMeasurementPoint(
    val dateEpochMillis: Long,
    val year: Int,
    val examName: String,
    val metricLabel: String,
    val value: String,
    val unit: String?,
    val context: String?,
)

enum class LabMetricFamily(val displayTitle: String) {
    LIPIDS("Colesterolo e lipidi"),
    CARDIAC("Cuore e prova da sforzo"),
    LIVER_KIDNEY("Fegato, milza e reni"),
    BLOOD_COUNT("Emocromo e funzionalità"),
    GLYCEMIC("Glicemia e metabolismo"),
    OTHER("Altri parametri"),
}

object ClinicalRecordLabTrends {

    private data class MetricPattern(val family: LabMetricFamily, val label: String, val regex: Pattern?)

    private val metricPatterns = listOf(
        MetricPattern(LabMetricFamily.LIPIDS, "Colesterolo totale", rx("(?i)colesterolo\\s*totale[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.LIPIDS, "LDL", rx("(?i)\\bLDL[:\\s-]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.LIPIDS, "HDL", rx("(?i)\\bHDL[:\\s-]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.LIPIDS, "Trigliceridi", rx("(?i)trigliceridi[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.GLYCEMIC, "Glicemia", rx("(?i)glicemia[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.BLOOD_COUNT, "GOT", rx("(?i)\\bGOT[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.BLOOD_COUNT, "GPT", rx("(?i)\\bGPT[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.BLOOD_COUNT, "Creatinina", rx("(?i)creatinina[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.BLOOD_COUNT, "PSA", rx("(?i)\\bPSA[:\\s]*(\\d+[,.]?\\d*)")),
        MetricPattern(LabMetricFamily.BLOOD_COUNT, "Emoglobina", rx("(?i)emoglobina[:\\s]*(\\d+[,.]?\\d*)")),
    )

    fun extract(exams: List<KBMedicalExamEntity>): Map<LabMetricFamily, List<LabMeasurementPoint>> {
        val result = mutableMapOf<LabMetricFamily, MutableList<LabMeasurementPoint>>()
        for (exam in exams) {
            val text = listOf(exam.name, exam.resultText.orEmpty()).joinToString("\n")
            if (text.isBlank()) continue
            val date = exam.resultDateEpochMillis ?: exam.deadlineEpochMillis ?: exam.updatedAtEpochMillis
            val year = SimpleDateFormat("yyyy", KBLocale.current()).format(Date(date)).toIntOrNull() ?: 0

            for ((family, label, regex) in metricPatterns) {
                val value = regex?.let { firstMatch(it, text) } ?: continue
                result.getOrPut(family) { mutableListOf() }.add(
                    LabMeasurementPoint(
                        dateEpochMillis = date,
                        year = year,
                        examName = exam.name,
                        metricLabel = label,
                        value = value.replace(',', '.'),
                        unit = unitHint(label, text),
                        context = clipContext(text),
                    ),
                )
            }
        }
        result.values.forEach { list -> list.sortBy { it.dateEpochMillis } }
        return result
    }

    fun narrative(family: LabMetricFamily, points: List<LabMeasurementPoint>): String? {
        if (points.isEmpty()) return null
        val lines = mutableListOf<String>()
        lines += "Andamento ${family.displayTitle.lowercase(Locale.getDefault())}:"
        points.groupBy { it.metricLabel }.toSortedMap().forEach { (metric, items) ->
            val series = items.joinToString(" → ") { p ->
                val u = p.unit?.let { " $it" }.orEmpty()
                "${p.year}: ${p.value}$u (${formatShort(p.dateEpochMillis)})"
            }
            lines += "• $metric: $series"
            if (items.size >= 2) {
                val v1 = items.first().value.toDoubleOrNull()
                val v2 = items.last().value.toDoubleOrNull()
                if (v1 != null && v2 != null) {
                    val delta = v2 - v1
                    val dir = when {
                        delta > 5 -> "in aumento"
                        delta < -5 -> "in diminuzione"
                        else -> "sostanzialmente stabile"
                    }
                    lines += "  Sintesi: valore $dir nel periodo considerato."
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun formatAllTrends(trends: Map<LabMetricFamily, List<LabMeasurementPoint>>): List<String> {
        val out = mutableListOf<String>()
        for (family in LabMetricFamily.entries) {
            val points = trends[family] ?: continue
            if (points.isEmpty()) continue
            narrative(family, points)?.let { out += it }
        }
        return out
    }

    private fun classifyExamName(
        exam: KBMedicalExamEntity,
        date: Long,
        year: Int,
        result: MutableMap<LabMetricFamily, MutableList<LabMeasurementPoint>>,
    ) {
        val n = exam.name.lowercase(Locale.getDefault())
        val snippet = clipContext(exam.resultText.orEmpty())
        if (n.contains("sforzo") || n.contains("ergometria") || n.contains("ecocardio") || n.contains("coronarografia")) {
            result.getOrPut(LabMetricFamily.CARDIAC) { mutableListOf() }.add(
                LabMeasurementPoint(date, year, exam.name, "Esame cardiaco", "—", null, snippet),
            )
        }
        if (n.contains("milza") || n.contains("addome") || n.contains("epat") || n.contains("ren") || n.contains("prostata")) {
            result.getOrPut(LabMetricFamily.LIVER_KIDNEY) { mutableListOf() }.add(
                LabMeasurementPoint(date, year, exam.name, "Imaging", "—", null, snippet),
            )
        }
        if (n.contains("emocromo") || n.contains("sangue")) {
            result.getOrPut(LabMetricFamily.BLOOD_COUNT) { mutableListOf() }.add(
                LabMeasurementPoint(date, year, exam.name, "Esami sangue", "—", null, snippet),
            )
        }
    }

    private fun rx(pattern: String): Pattern? = runCatching { Pattern.compile(pattern) }.getOrNull()

    private fun firstMatch(regex: Pattern, text: String): String? {
        val m = regex.matcher(text)
        return if (m.find() && m.groupCount() >= 1) m.group(1) else null
    }

    private fun unitHint(label: String, text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        if (lower.contains("mg/dl")) return "mg/dL"
        if (lower.contains("ng/ml")) return "ng/mL"
        if (lower.contains("u/l") || lower.contains("ui/l")) return "U/L"
        if (label.contains("Colesterolo") || label == "LDL" || label == "HDL" || label == "Trigliceridi") return "mg/dL"
        return null
    }

    private fun clipContext(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return if (t.length <= 220) t else t.take(219) + "…"
    }

    private fun formatShort(epoch: Long): String =
        SimpleDateFormat("MMM yyyy", KBLocale.current()).format(Date(epoch))
}
