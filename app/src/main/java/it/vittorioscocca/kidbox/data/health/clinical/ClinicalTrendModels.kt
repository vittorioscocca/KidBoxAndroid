package it.vittorioscocca.kidbox.data.health.clinical

enum class ClinicalTrendDirection { STABILE, IN_AUMENTO, IN_DIMINUZIONE }

enum class ClinicalOverallStatus(val badgeLabel: String, val emoji: String) {
    STABILE("Stabile", "🟢"),
    MIGLIORATO("Migliorato", "🔵"),
    PEGGIORATO("Attenzione", "🔴"),
    DA_MONITORARE("Da monitorare", "🟡"),
    ATTENZIONE("Attenzione", "🔴"),
}

enum class ExtractedValueKind { BLOOD_PRESSURE, LESION, LAB, HEART_RATE, WEIGHT, STRESS_TEST, GENERIC }

data class ExtractedMedicalValue(
    val kind: ExtractedValueKind,
    val parameterName: String,
    val numericValue: Double?,
    val textValue: String?,
    val unit: String?,
    val systolic: Int?,
    val diastolic: Int?,
    val lesionType: String?,
    val dimensionMm: Double?,
    val dateEpochMillis: Long,
    val sourceId: String,
    val sourceLabel: String
)

data class ParameterTrendPoint(
    val dateEpochMillis: Long,
    val displayValue: String,
    val numericValue: Double?,
    val source: String,
)

data class ParameterTrend(
    val name: String,
    val points: List<ParameterTrendPoint>,
    val trend: ClinicalTrendDirection,
    val deltaPercent: Double?,
    val clinicalNote: String?,
)

data class SpecialtyTrendSnapshot(
    val specialtyId: String,
    val specialtyTitle: String,
    val parameters: List<ParameterTrend>,
    val narrativeAnalysis: String,
    val overallStatus: ClinicalOverallStatus,
    val lastUpdatedEpochMillis: Long,
)

data class GlobalStatusLine(
    val specialtyTitle: String,
    val status: ClinicalOverallStatus,
    val headline: String,
)

data class ClinicalRecordGlobalSummary(
    val monitoredSpecialtiesCount: Int,
    val attentionCount: Int,
    val lastUpdatedEpochMillis: Long,
    val activeTherapyNames: List<String>,
    val nextAppointmentLine: String?,
    val statusLines: List<GlobalStatusLine>,
)
