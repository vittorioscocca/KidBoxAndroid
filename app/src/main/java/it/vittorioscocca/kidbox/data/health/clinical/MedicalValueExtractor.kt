package it.vittorioscocca.kidbox.data.health.clinical

import java.util.Locale
import java.util.regex.Pattern

object MedicalValueExtractor {

    private const val BP_PATTERN =
        "(?i)(?:PA|pressione(?:\\s+arteriosa)?|misurata)[^\\d]{0,30}(\\d{2,3})\\s*[/\\\\]\\s*(\\d{2,3})"

    private const val LESION_PATTERN =
        "(?i)(cisti|angioma|agioma|nodulo|formazione|lesione)\\s+(?:di\\s+)?(\\d+(?:[.,]\\d+)?)\\s*(mm|cm)"

    private const val BPM_PATTERN =
        "(?i)(?:FC|frequenza\\s+cardiaca)[^\\d]{0,20}(\\d{2,3})\\s*bpm"

    private const val WEIGHT_PATTERN =
        "(?i)peso\\s*[:=]?\\s*(\\d+(?:[.,]\\d+)?)\\s*kg"

    private const val WATTS_PATTERN =
        "(?i)(\\d+(?:[.,]\\d+)?)\\s*W\\b"

    private const val METS_PATTERN =
        "(?i)(\\d+(?:[.,]\\d+)?)\\s*METS"

    private val LAB_KEYS: Map<String, String> = mapOf(
        "glicemia" to "Glicemia",
        "ldl" to "LDL",
        "hdl" to "HDL",
        "colesterolo totale" to "Colesterolo totale",
        "colesterolo" to "Colesterolo totale",
        "trigliceridi" to "Trigliceridi",
        "creatinina" to "Creatinina",
        "psa" to "PSA",
        "got" to "GOT",
        "gpt" to "GPT",
        "emoglobina" to "Emoglobina"
    )

    fun extract(
        from: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        if (from.isBlank()) return emptyList()
        val normalized = from.replace("\r\n", "\n").replace("\u2014", "-")
        val results = ArrayList<ExtractedMedicalValue>()
        results.addAll(extractBloodPressure(normalized, sourceId, sourceLabel, dateEpochMillis))
        results.addAll(extractLesions(normalized, sourceId, sourceLabel, dateEpochMillis))
        results.addAll(extractLab(normalized, sourceId, sourceLabel, dateEpochMillis))
        results.addAll(extractHeartRate(normalized, sourceId, sourceLabel, dateEpochMillis))
        results.addAll(extractWeight(normalized, sourceId, sourceLabel, dateEpochMillis))
        results.addAll(extractStress(normalized, sourceId, sourceLabel, dateEpochMillis))
        return results.distinctBy { v ->
            listOf(v.sourceId, v.parameterName, v.textValue, v.dateEpochMillis).joinToString("|")
        }
    }

    private fun extractBloodPressure(
        text: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        val rx = Pattern.compile(BP_PATTERN)
        val results = ArrayList<ExtractedMedicalValue>()
        val matcher = rx.matcher(text)
        while (matcher.find()) {
            val sys = matcher.group(1)?.toIntOrNull() ?: continue
            val dia = matcher.group(2)?.toIntOrNull() ?: continue
            results.add(
                makeValue(
                    kind = ExtractedValueKind.BLOOD_PRESSURE,
                    name = "Pressione arteriosa",
                    numeric = sys.toDouble(),
                    display = sys.toString() + "/" + dia.toString() + " mmHg",
                    unit = "mmHg",
                    systolic = sys,
                    diastolic = dia,
                    dateEpochMillis = dateEpochMillis,
                    sourceId = sourceId,
                    sourceLabel = sourceLabel
                )
            )
        }
        return results
    }

    private fun extractLesions(
        text: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        val rx = Pattern.compile(LESION_PATTERN)
        val results = ArrayList<ExtractedMedicalValue>()
        val matcher = rx.matcher(text)
        while (matcher.find()) {
            val tipo = matcher.group(1)?.trim()?.replaceFirstChar { it.uppercase() } ?: continue
            var mm = matcher.group(2)?.replace(',', '.')?.toDoubleOrNull() ?: continue
            val unit = matcher.group(3) ?: "mm"
            if (unit.equals("cm", ignoreCase = true)) mm *= 10.0
            results.add(
                makeValue(
                    kind = ExtractedValueKind.LESION,
                    name = tipo + " " + mm.toInt() + " mm",
                    numeric = mm,
                    display = mm.toInt().toString() + " mm",
                    unit = "mm",
                    lesionType = tipo,
                    dimensionMm = mm,
                    dateEpochMillis = dateEpochMillis,
                    sourceId = sourceId,
                    sourceLabel = sourceLabel
                )
            )
        }
        return results
    }

    private fun extractLab(
        text: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        val results = ArrayList<ExtractedMedicalValue>()
        for (key in LAB_KEYS.keys) {
            val label = LAB_KEYS[key] ?: continue
            val rx = Pattern.compile(labPatternFor(key))
            val matcher = rx.matcher(text)
            while (matcher.find()) {
                val value = matcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: continue
                val unitRaw = matcher.group(2) ?: ""
                val unit = if (unitRaw.isEmpty()) defaultLabUnit(label) else unitRaw
                val display = if (unitRaw.isEmpty()) formatNum(value) else formatNum(value) + " " + unit
                results.add(
                    makeValue(
                        kind = ExtractedValueKind.LAB,
                        name = label,
                        numeric = value,
                        display = display,
                        unit = unit,
                        dateEpochMillis = dateEpochMillis,
                        sourceId = sourceId,
                        sourceLabel = sourceLabel
                    )
                )
            }
        }
        return results
    }

    private fun extractHeartRate(
        text: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        val rx = Pattern.compile(BPM_PATTERN)
        val results = ArrayList<ExtractedMedicalValue>()
        val matcher = rx.matcher(text)
        while (matcher.find()) {
            val value = matcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: continue
            results.add(
                makeValue(
                    kind = ExtractedValueKind.HEART_RATE,
                    name = "Frequenza cardiaca",
                    numeric = value,
                    display = value.toInt().toString() + " bpm",
                    unit = "bpm",
                    dateEpochMillis = dateEpochMillis,
                    sourceId = sourceId,
                    sourceLabel = sourceLabel
                )
            )
        }
        return results
    }

    private fun extractWeight(
        text: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        val rx = Pattern.compile(WEIGHT_PATTERN)
        val results = ArrayList<ExtractedMedicalValue>()
        val matcher = rx.matcher(text)
        while (matcher.find()) {
            val value = matcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: continue
            results.add(
                makeValue(
                    kind = ExtractedValueKind.WEIGHT,
                    name = "Peso",
                    numeric = value,
                    display = formatNum(value) + " kg",
                    unit = "kg",
                    dateEpochMillis = dateEpochMillis,
                    sourceId = sourceId,
                    sourceLabel = sourceLabel
                )
            )
        }
        return results
    }

    private fun extractStress(
        text: String,
        sourceId: String,
        sourceLabel: String,
        dateEpochMillis: Long
    ): List<ExtractedMedicalValue> {
        val results = ArrayList<ExtractedMedicalValue>()
        val watts = Pattern.compile(WATTS_PATTERN).matcher(text)
        if (watts.find()) {
            val value = watts.group(1)?.replace(',', '.')?.toDoubleOrNull()
            if (value != null) {
                results.add(
                    makeValue(
                        kind = ExtractedValueKind.STRESS_TEST,
                        name = "Carico prova da sforzo",
                        numeric = value,
                        display = value.toInt().toString() + " W",
                        unit = "W",
                        dateEpochMillis = dateEpochMillis,
                        sourceId = sourceId,
                        sourceLabel = sourceLabel
                    )
                )
            }
        }
        val mets = Pattern.compile(METS_PATTERN).matcher(text)
        if (mets.find()) {
            val value = mets.group(1)?.replace(',', '.')?.toDoubleOrNull()
            if (value != null) {
                results.add(
                    makeValue(
                        kind = ExtractedValueKind.STRESS_TEST,
                        name = "METS",
                        numeric = value,
                        display = formatNum(value) + " METS",
                        unit = "METS",
                        dateEpochMillis = dateEpochMillis,
                        sourceId = sourceId,
                        sourceLabel = sourceLabel
                    )
                )
            }
        }
        return results
    }

    private fun makeValue(
        kind: ExtractedValueKind,
        name: String,
        numeric: Double,
        display: String,
        unit: String,
        systolic: Int? = null,
        diastolic: Int? = null,
        lesionType: String? = null,
        dimensionMm: Double? = null,
        dateEpochMillis: Long,
        sourceId: String,
        sourceLabel: String
    ): ExtractedMedicalValue {
        return ExtractedMedicalValue(
            kind = kind,
            parameterName = name,
            numericValue = numeric,
            textValue = display,
            unit = unit,
            systolic = systolic,
            diastolic = diastolic,
            lesionType = lesionType,
            dimensionMm = dimensionMm,
            dateEpochMillis = dateEpochMillis,
            sourceId = sourceId,
            sourceLabel = sourceLabel
        )
    }

    private fun labPatternFor(key: String): String {
        return "(?i)\\b" + Pattern.quote(key) + "\\b\\s*[:=\\-]?\\s*(\\d+(?:[.,]\\d+)?)"
    }

    private fun defaultLabUnit(label: String): String {
        if (label == "PSA") return "ng/mL"
        if (label == "TSH") return "mUI/L"
        return "mg/dL"
    }

    private fun formatNum(value: Double): String {
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return String.format(Locale.US, "%.1f", value)
    }
}
