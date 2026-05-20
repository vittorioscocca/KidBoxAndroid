package it.vittorioscocca.kidbox.data.health.clinical

import java.util.Calendar
import java.util.Locale

object TrendAnalyzer {

    fun buildSpecialtyTrend(
        specialtyId: String,
        specialtyTitle: String,
        values: List<ExtractedMedicalValue>,
        chronologyLines: List<String>,
    ): SpecialtyTrendSnapshot? {
        if (!ClinicalRecordSectionPolicy.shouldGenerateStandaloneSection(specialtyId)) return null
        val relevant = values.filter { mapsToSpecialty(it, specialtyId) }
        val parameters = buildParameters(relevant)
        if (parameters.isEmpty() && chronologyLines.isEmpty()) return null
        val status = classifyOverall(parameters)
        val narrative = buildNarrative(specialtyId, parameters, chronologyLines)
        val last = (parameters.flatMap { it.points }.map { it.dateEpochMillis } + relevant.map { it.dateEpochMillis }).maxOrNull()
            ?: System.currentTimeMillis()
        return SpecialtyTrendSnapshot(specialtyId, specialtyTitle, parameters, narrative, status, last)
    }

    fun buildGlobalSummary(
        trends: List<SpecialtyTrendSnapshot>,
        therapyNames: List<String>,
        nextAppointment: String?,
    ) = ClinicalRecordGlobalSummary(
        monitoredSpecialtiesCount = trends.size,
        attentionCount = trends.count {
            it.overallStatus == ClinicalOverallStatus.DA_MONITORARE ||
                it.overallStatus == ClinicalOverallStatus.ATTENZIONE ||
                it.overallStatus == ClinicalOverallStatus.PEGGIORATO
        },
        lastUpdatedEpochMillis = trends.maxOfOrNull { it.lastUpdatedEpochMillis } ?: System.currentTimeMillis(),
        activeTherapyNames = therapyNames,
        nextAppointmentLine = nextAppointment,
        statusLines = trends.map { t ->
            GlobalStatusLine(
                t.specialtyTitle,
                t.overallStatus,
                t.parameters.lastOrNull()?.points?.lastOrNull()?.displayValue ?: t.narrativeAnalysis.take(60),
            )
        },
    )

    private fun buildParameters(values: List<ExtractedMedicalValue>): List<ParameterTrend> {
        return values.groupBy { groupKey(it) }.map { (name, items) ->
            val sorted = items.sortedBy { it.dateEpochMillis }
            val points = sorted.map {
                ParameterTrendPoint(it.dateEpochMillis, it.textValue ?: name, it.numericValue ?: it.dimensionMm, it.sourceLabel)
            }
            val (trend, delta) = computeTrend(points, lowerIsBetter(name))
            ParameterTrend(name, points, trend, delta, clinicalNote(name, trend))
        }.sortedBy { it.name }
    }

    private fun computeTrend(points: List<ParameterTrendPoint>, lowerIsBetter: Boolean): Pair<ClinicalTrendDirection, Double?> {
        val nums = points.mapNotNull { it.numericValue }
        if (nums.size < 2 || nums.first() == 0.0) return ClinicalTrendDirection.STABILE to null
        val delta = ((nums.last() - nums.first()) / kotlin.math.abs(nums.first())) * 100
        if (kotlin.math.abs(delta) < 5) return ClinicalTrendDirection.STABILE to delta
        val up = nums.last() > nums.first()
        return when {
            lowerIsBetter && up -> ClinicalTrendDirection.IN_AUMENTO to delta
            lowerIsBetter && !up -> ClinicalTrendDirection.IN_DIMINUZIONE to delta
            !lowerIsBetter && up -> ClinicalTrendDirection.IN_AUMENTO to delta
            else -> ClinicalTrendDirection.IN_DIMINUZIONE to delta
        }
    }

    private fun classifyOverall(parameters: List<ParameterTrend>): ClinicalOverallStatus {
        if (parameters.isEmpty()) return ClinicalOverallStatus.DA_MONITORARE
        if (parameters.any { it.points.size == 1 && (it.name.contains("angiom", true) || it.name.contains("cist", true)) }) {
            return ClinicalOverallStatus.DA_MONITORARE
        }
        if (parameters.all { it.trend == ClinicalTrendDirection.STABILE }) return ClinicalOverallStatus.STABILE
        return ClinicalOverallStatus.DA_MONITORARE
    }

    private fun buildNarrative(specialtyId: String, parameters: List<ParameterTrend>, chronology: List<String>): String {
        if (specialtyId == ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY.raw) {
            val bp = parameters.firstOrNull { it.name.contains("Pressione", ignoreCase = true) }
            if (bp != null) {
                ClinicalRecordMeasurementSummary.bloodPressureYearSummary(bp.points)?.let { return it }
                bp.points.lastOrNull()?.let {
                    return "Ultima pressione documentata: ${it.displayValue}."
                }
            }
        }
        if (parameters.isNotEmpty()) {
            val bits = parameters.take(3).joinToString("; ") { p ->
                val last = p.points.lastOrNull()?.displayValue ?: ""
                "${p.name} $last"
            }
            return "Parametri monitorati per questa area: $bits."
        }
        if (chronology.isNotEmpty()) return "Sono documentati ${chronology.size} eventi clinici in questa specialità nel periodo considerato."
        return "Dati insufficienti per un'analisi di andamento; allega referti con valori numerici."
    }

    private fun groupKey(v: ExtractedMedicalValue) = when (v.kind) {
        ExtractedValueKind.BLOOD_PRESSURE -> "Pressione arteriosa"
        ExtractedValueKind.LESION -> v.lesionType ?: v.parameterName
        else -> v.parameterName
    }

    private fun mapsToSpecialty(v: ExtractedMedicalValue, specialtyId: String): Boolean = when (specialtyId) {
        ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY.raw ->
            v.kind == ExtractedValueKind.BLOOD_PRESSURE || v.kind == ExtractedValueKind.STRESS_TEST ||
                v.kind == ExtractedValueKind.HEART_RATE || (v.kind == ExtractedValueKind.LAB && v.parameterName in listOf("LDL", "HDL", "Colesterolo totale"))
        ClinicalRecordTopicBuilder.TopicId.GASTROENTEROLOGY.raw -> v.kind == ExtractedValueKind.LESION
        ClinicalRecordTopicBuilder.TopicId.UROLOGY.raw -> v.parameterName == "PSA" || v.kind == ExtractedValueKind.LESION
        ClinicalRecordTopicBuilder.TopicId.METABOLISM.raw -> v.kind == ExtractedValueKind.LAB
        else -> false
    }

    private fun lowerIsBetter(name: String): Boolean {
        val n = name.lowercase(Locale.getDefault())
        return n.contains("pressione") || n.contains("ldl") || n.contains("glicemia")
    }

    private fun clinicalNote(name: String, trend: ClinicalTrendDirection): String? =
        if (trend == ClinicalTrendDirection.STABILE) "Valore stabile nel periodo considerato." else "Andamento variabile; confrontare con il medico."

    private fun year(epoch: Long) = Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)
}
