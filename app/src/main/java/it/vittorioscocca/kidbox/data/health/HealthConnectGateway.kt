package it.vittorioscocca.kidbox.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.domain.model.HealthDailyActivity
import it.vittorioscocca.kidbox.domain.model.HealthHeartRateReading
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.HealthWorkoutEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Quelle senza cui l'importazione non ha senso. Restano queste cinque: chi
     * aveva già concesso l'accesso non deve ritrovarsi bloccato perché ne
     * abbiamo aggiunta una nuova.
     */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    /** In più, se l'utente le concede. Senza, si importa tutto il resto. */
    val optionalPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeightRecord::class),
    )

    /** L'insieme da chiedere a Health Connect. */
    val allPermissions: Set<String> = requiredPermissions + optionalPermissions

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private fun clientOrThrow(): HealthConnectClient {
        if (!isAvailable()) {
            throw IllegalStateException(
                "Health Connect non è disponibile. Installa l'app Salute di Google dal Play Store.",
            )
        }
        return HealthConnectClient.getOrCreate(context)
    }

    suspend fun fetchSnapshot(): HealthImportSnapshot {
        val client = clientOrThrow()
        // Health Connect lancia un'eccezione se si legge un tipo non concesso:
        // si chiede prima cosa è permesso e si salta il resto, invece di far
        // fallire tutta l'importazione per un permesso mancante.
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        val canReadHeight = HealthPermission.getReadPermission(HeightRecord::class) in granted
        val canReadWeight = HealthPermission.getReadPermission(WeightRecord::class) in granted
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val lookback = now.minusSeconds(30L * 24 * 3600)

        val heartRates = readRecentHeartRates(client, lookback, now)
        val latestHeart = heartRates.firstOrNull()

        var stepsToday: Int? = null
        runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                ),
            )
            response[StepsRecord.COUNT_TOTAL]?.let { stepsToday = it.toLong().toInt() }
        }

        var activeKcal: Double? = null
        runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                ),
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let {
                activeKcal = it
            }
        }

        // Senza finestra temporale (`before`, non `between`): peso e altezza si
        // inseriscono di rado, e l'ultimo valore vale finché non ne arriva un
        // altro. Con i 30 giorni di `lookback` un peso di due mesi fa non
        // esisteva, e la scheda restava vuota pur avendo il dato.
        var weightKg: Double? = null
        if (canReadWeight) {
        val weightPage = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.before(now),
                ascendingOrder = false,
                pageSize = 1,
            ),
        )
        weightPage.records.firstOrNull()?.let { record ->
            weightKg = record.weight.inKilograms
        }
        }

        // In centimetri: Health Connect la dà in metri, ma nella scheda si legge
        // "172 cm", non "1,72 m".
        var heightCm: Double? = null
        if (canReadHeight) {
        val heightPage = client.readRecords(
            ReadRecordsRequest(
                recordType = HeightRecord::class,
                timeRangeFilter = TimeRangeFilter.before(now),
                ascendingOrder = false,
                pageSize = 1,
            ),
        )
        heightPage.records.firstOrNull()?.let { record ->
            heightCm = record.height.inMeters * 100
        }
        }

        val lookback90 = now.minusSeconds(90L * 24 * 3600)
        val dailyActivity = readDailyActivityLastDays(client, zone, days = 7)
        val daily90 = readDailyActivityLastDays(client, zone, days = 90)
        val workouts = readRecentWorkouts(client, lookback, now, limit = 25)
        val restingAvg = averageHeartRate(client, lookback90, now)
        val stepsAvg90 = averageSteps(daily90)
        val weeklyExercise = averageWeeklyWorkoutMinutes(workouts)

        return HealthImportSnapshot(
            weightKg = weightKg,
            heightCm = heightCm,
            heartRateBpm = latestHeart?.bpm ?: heartRates.firstOrNull()?.bpm,
            heartRateMeasuredAtEpochMillis = latestHeart?.measuredAtEpochMillis,
            restingHeartRateBpm = restingAvg,
            restingHeartRateAvg90d = restingAvg,
            stepsToday = stepsToday,
            activeEnergyKcal = activeKcal,
            recentHeartRates = heartRates,
            recentDailyActivity = dailyActivity,
            recentWorkouts = workouts,
            recentECGs = emptyList(),
            weeklyExerciseMinutesAvg = weeklyExercise,
            stepsDailyAvg90d = stepsAvg90,
            wearablePeriodStartEpochMillis = lookback90.toEpochMilli(),
            wearablePeriodEndEpochMillis = now.toEpochMilli(),
        )
    }

    private suspend fun averageHeartRate(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): Double? {
        val page = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 500,
            ),
        )
        val samples = page.records.flatMap { it.samples.map { s -> s.beatsPerMinute.toDouble() } }
        if (samples.isEmpty()) return null
        return samples.average()
    }

    private fun averageSteps(daily: List<HealthDailyActivity>): Double? {
        val vals = daily.mapNotNull { it.steps }.filter { it > 0 }
        if (vals.isEmpty()) return null
        return vals.sum().toDouble() / vals.size
    }

    private fun averageWeeklyWorkoutMinutes(workouts: List<HealthWorkoutEntry>): Double? {
        if (workouts.isEmpty()) return null
        val cal = java.util.Calendar.getInstance()
        val weekTotals = mutableMapOf<Int, Int>()
        workouts.forEach { w ->
            cal.timeInMillis = w.startedAtEpochMillis
            val key = cal.get(java.util.Calendar.YEAR) * 100 + cal.get(java.util.Calendar.WEEK_OF_YEAR)
            weekTotals[key] = (weekTotals[key] ?: 0) + (w.durationMinutes ?: 0)
        }
        val totals = weekTotals.values
        if (totals.isEmpty()) return null
        return totals.sum().toDouble() / totals.size
    }

    private suspend fun readRecentHeartRates(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        limit: Int = 20,
    ): List<HealthHeartRateReading> {
        val page = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 30,
            ),
        )
        return page.records
            .flatMap { record ->
                record.samples.map { sample ->
                    HealthHeartRateReading(
                        id = "${record.metadata.id}_${sample.time.toEpochMilli()}",
                        bpm = sample.beatsPerMinute.toDouble(),
                        measuredAtEpochMillis = sample.time.toEpochMilli(),
                    )
                }
            }
            .sortedByDescending { it.measuredAtEpochMillis }
            .take(limit)
    }

    private suspend fun readDailyActivityLastDays(
        client: HealthConnectClient,
        zone: ZoneId,
        days: Int,
    ): List<HealthDailyActivity> {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val entries = mutableListOf<HealthDailyActivity>()

        for (offset in 0 until days) {
            val day = LocalDate.now(zone).minusDays(offset.toLong())
            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()

            var steps: Int? = null
            runCatching {
                val response = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                    ),
                )
                response[StepsRecord.COUNT_TOTAL]?.let { steps = it.toLong().toInt() }
            }

            var kcal: Double? = null
            runCatching {
                val response = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                    ),
                )
                response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let {
                    kcal = it
                }
            }

            if ((steps ?: 0) > 0 || (kcal ?: 0.0) > 0.0) {
                entries += HealthDailyActivity(
                    id = formatter.format(day),
                    dayEpochMillis = dayStart.toEpochMilli(),
                    steps = steps,
                    activeEnergyKcal = kcal,
                )
            }
        }
        return entries
    }

    private suspend fun readRecentWorkouts(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        limit: Int = 10,
    ): List<HealthWorkoutEntry> {
        val page = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = limit,
            ),
        )
        return page.records.map { session ->
            val durationMin = session.endTime.epochSecond
                .minus(session.startTime.epochSecond)
                .toDouble()
                .div(60.0)
                .roundToInt()
                .coerceAtLeast(1)
            HealthWorkoutEntry(
                id = session.metadata.id,
                title = exerciseTitle(session.exerciseType),
                startedAtEpochMillis = session.startTime.toEpochMilli(),
                durationMinutes = durationMin,
                activeEnergyKcal = null,
            )
        }
    }

    private fun exerciseTitle(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Corsa"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Camminata"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Nuoto"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Escursionismo"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Forza"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Danza"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Ellittica"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Scale"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Remo"
        else -> "Allenamento"
    }
}
