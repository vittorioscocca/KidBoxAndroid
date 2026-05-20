package it.vittorioscocca.kidbox.data.health.clinical

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ClinicalRecordMeasurementSummary {

    fun bloodPressureYearSummary(points: List<ParameterTrendPoint>): String? =
        summarizeYearly(points) { it.displayValue }

    fun summarizeYearly(
        points: List<ParameterTrendPoint>,
        valueLabel: (ParameterTrendPoint) -> String,
    ): String? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.dateEpochMillis }
        val cal = java.util.Calendar.getInstance()
        val byYear = sorted.groupBy { cal.apply { timeInMillis = it.dateEpochMillis }.get(java.util.Calendar.YEAR) }
        val phrases = byYear.keys.sorted().mapNotNull { year ->
            val yearPoints = byYear[year].orEmpty()
            if (yearPoints.isEmpty()) return@mapNotNull null
            if (yearPoints.size > 4) {
                val labels = yearPoints.map(valueLabel)
                val trend = if (stableNumericTrend(yearPoints)) "con tendenza alla stabilità" else "con variazioni nel corso dell'anno"
                "Nel $year i valori si sono attestati tra ${labels.first()} e ${labels.last()}, ultima rilevazione ${labels.last()}, $trend"
            } else {
                val series = yearPoints.joinToString(", ") {
                    "${formatShort(it.dateEpochMillis)}: ${valueLabel(it)}"
                }
                "Nel $year: $series"
            }
        }
        return phrases.takeIf { it.isNotEmpty() }?.joinToString(". ")?.let { "$it." }
    }

    private fun stableNumericTrend(points: List<ParameterTrendPoint>): Boolean {
        val nums = points.mapNotNull { it.numericValue }
        if (nums.size < 2) return true
        val delta = kotlin.math.abs(nums.last() - nums.first())
        return delta <= maxOf(5.0, kotlin.math.abs(nums.first()) * 0.08)
    }

    private fun formatShort(epoch: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epoch))
}
