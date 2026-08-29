package it.vittorioscocca.kidbox.domain.model

import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting

data class HealthHeartRateReading(
    val id: String,
    val bpm: Double,
    val measuredAtEpochMillis: Long,
)

data class HealthDailyActivity(
    val id: String,
    val dayEpochMillis: Long,
    val steps: Int? = null,
    val activeEnergyKcal: Double? = null,
)

data class HealthWorkoutEntry(
    val id: String,
    val title: String,
    val startedAtEpochMillis: Long,
    val durationMinutes: Int? = null,
    val activeEnergyKcal: Double? = null,
)

data class HealthECGEntry(
    val id: String,
    val recordedAtEpochMillis: Long,
    val classificationLabel: String,
    val averageHeartRateBpm: Double? = null,
)

/** Dati letti da Health Connect per la scheda medica. */
data class HealthImportSnapshot(
    val birthDateEpochMillis: Long? = null,
    val weightKg: Double? = null,
    /** Altezza in centimetri, da Health Connect. */
    val heightCm: Double? = null,
    val bloodGroup: String? = null,
    val heartRateBpm: Double? = null,
    val heartRateMeasuredAtEpochMillis: Long? = null,
    val stepsToday: Int? = null,
    val activeEnergyKcal: Double? = null,
    val recentHeartRates: List<HealthHeartRateReading> = emptyList(),
    val recentDailyActivity: List<HealthDailyActivity> = emptyList(),
    val recentWorkouts: List<HealthWorkoutEntry> = emptyList(),
    val recentECGs: List<HealthECGEntry> = emptyList(),
    val syncedAtEpochMillis: Long = System.currentTimeMillis(),
    val restingHeartRateBpm: Double? = null,
    val restingHeartRateAvg90d: Double? = null,
    val vo2Max: Double? = null,
    val vo2MaxRecent: Double? = null,
    val weeklyExerciseMinutesAvg: Double? = null,
    val spo2NightlyAvgPercent: Double? = null,
    val stepsDailyAvg90d: Double? = null,
    val hrvSdnnMsAvg90d: Double? = null,
    val wearablePeriodStartEpochMillis: Long? = null,
    val wearablePeriodEndEpochMillis: Long? = null,
) {
    val hasWearableExtendedMetrics: Boolean
        get() = restingHeartRateAvg90d != null
            || vo2MaxRecent != null
            || vo2Max != null
            || weeklyExerciseMinutesAvg != null
            || spo2NightlyAvgPercent != null
            || stepsDailyAvg90d != null
            || hrvSdnnMsAvg90d != null

    val hasCardiacOrActivity: Boolean
        get() = heartRateBpm != null
            || restingHeartRateBpm != null
            || recentHeartRates.isNotEmpty()
            || (stepsToday ?: 0) > 0
            || activeEnergyKcal != null
            || recentDailyActivity.any { (it.steps ?: 0) > 0 || (it.activeEnergyKcal ?: 0.0) > 0.0 }
            || recentWorkouts.isNotEmpty()
            || recentECGs.isNotEmpty()
            || hasWearableExtendedMetrics

    val ageDescription: String?
        get() = birthDateEpochMillis?.let(HealthAgeFormatting::ageDescriptionFromBirth)
}

