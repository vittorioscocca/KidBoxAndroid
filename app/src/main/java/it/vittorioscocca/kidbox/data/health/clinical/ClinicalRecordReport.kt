package it.vittorioscocca.kidbox.data.health.clinical

data class ClinicalRecordReportArea(
    val id: String,
    val title: String,
    val summary: String,
    val narrative: String,
    val trendNarrative: String?,
    val bullets: List<String>,
    val overallStatus: ClinicalOverallStatus? = null,
    val analisiNarrativa: String? = null,
    val parameters: List<ParameterTrend>? = null,
)

data class ClinicalRecordReport(
    val subjectName: String,
    val sourceNative: Boolean,
    val sourceAiEnhanced: Boolean = false,
    val generatedAtEpochMillis: Long = System.currentTimeMillis(),
    val fullDocumentLines: List<String>,
    val areas: List<ClinicalRecordReportArea>,
    val globalSummary: ClinicalRecordGlobalSummary? = null,
    val specialtyTrends: List<SpecialtyTrendSnapshot> = emptyList(),
) {
    val hasContent: Boolean get() = fullDocumentLines.isNotEmpty() || areas.isNotEmpty()
}
