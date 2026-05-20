package it.vittorioscocca.kidbox.data.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.domain.model.HealthDailyActivity
import it.vittorioscocca.kidbox.domain.model.HealthECGEntry
import it.vittorioscocca.kidbox.domain.model.HealthHeartRateReading
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.HealthWorkoutEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.healthLinkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "health_link_store",
)

@Singleton
class HealthLinkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(childId: String): HealthImportSnapshot? {
        val key = stringPreferencesKey("link_$childId")
        val json = context.healthLinkDataStore.data.map { it[key] }.first() ?: return null
        return decode(json)
    }

    suspend fun save(childId: String, snapshot: HealthImportSnapshot) {
        val key = stringPreferencesKey("link_$childId")
        context.healthLinkDataStore.edit { prefs ->
            prefs[key] = encode(snapshot)
        }
    }

    private fun encode(s: HealthImportSnapshot): String = JSONObject().apply {
        s.birthDateEpochMillis?.let { put("birthDateEpochMillis", it) }
        s.weightKg?.let { put("weightKg", it) }
        s.bloodGroup?.let { put("bloodGroup", it) }
        s.heartRateBpm?.let { put("heartRateBpm", it) }
        s.heartRateMeasuredAtEpochMillis?.let { put("heartRateMeasuredAtEpochMillis", it) }
        s.stepsToday?.let { put("stepsToday", it) }
        s.activeEnergyKcal?.let { put("activeEnergyKcal", it) }
        put("syncedAtEpochMillis", s.syncedAtEpochMillis)
        s.restingHeartRateBpm?.let { put("restingHeartRateBpm", it) }
        s.restingHeartRateAvg90d?.let { put("restingHeartRateAvg90d", it) }
        s.vo2Max?.let { put("vo2Max", it) }
        s.vo2MaxRecent?.let { put("vo2MaxRecent", it) }
        s.weeklyExerciseMinutesAvg?.let { put("weeklyExerciseMinutesAvg", it) }
        s.spo2NightlyAvgPercent?.let { put("spo2NightlyAvgPercent", it) }
        s.stepsDailyAvg90d?.let { put("stepsDailyAvg90d", it) }
        s.hrvSdnnMsAvg90d?.let { put("hrvSdnnMsAvg90d", it) }
        s.wearablePeriodStartEpochMillis?.let { put("wearablePeriodStartEpochMillis", it) }
        s.wearablePeriodEndEpochMillis?.let { put("wearablePeriodEndEpochMillis", it) }
        put("recentHeartRates", encodeHeartRates(s.recentHeartRates))
        put("recentDailyActivity", encodeDaily(s.recentDailyActivity))
        put("recentWorkouts", encodeWorkouts(s.recentWorkouts))
        put("recentECGs", encodeECGs(s.recentECGs))
    }.toString()

    private fun encodeHeartRates(list: List<HealthHeartRateReading>): JSONArray = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("bpm", r.bpm)
                put("measuredAtEpochMillis", r.measuredAtEpochMillis)
            })
        }
    }

    private fun encodeDaily(list: List<HealthDailyActivity>): JSONArray = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().apply {
                put("id", d.id)
                put("dayEpochMillis", d.dayEpochMillis)
                d.steps?.let { put("steps", it) }
                d.activeEnergyKcal?.let { put("activeEnergyKcal", it) }
            })
        }
    }

    private fun encodeWorkouts(list: List<HealthWorkoutEntry>): JSONArray = JSONArray().apply {
        list.forEach { w ->
            put(JSONObject().apply {
                put("id", w.id)
                put("title", w.title)
                put("startedAtEpochMillis", w.startedAtEpochMillis)
                w.durationMinutes?.let { put("durationMinutes", it) }
                w.activeEnergyKcal?.let { put("activeEnergyKcal", it) }
            })
        }
    }

    private fun encodeECGs(list: List<HealthECGEntry>): JSONArray = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("id", e.id)
                put("recordedAtEpochMillis", e.recordedAtEpochMillis)
                put("classificationLabel", e.classificationLabel)
                e.averageHeartRateBpm?.let { put("averageHeartRateBpm", it) }
            })
        }
    }

    private fun decode(json: String): HealthImportSnapshot? = runCatching {
        val o = JSONObject(json)
        HealthImportSnapshot(
            birthDateEpochMillis = if (o.has("birthDateEpochMillis") && !o.isNull("birthDateEpochMillis")) {
                o.getLong("birthDateEpochMillis")
            } else {
                null
            },
            weightKg = o.optDouble("weightKg").takeIf { o.has("weightKg") && !o.isNull("weightKg") },
            bloodGroup = o.optString("bloodGroup").takeIf { it.isNotBlank() },
            heartRateBpm = o.optDouble("heartRateBpm").takeIf { o.has("heartRateBpm") && !o.isNull("heartRateBpm") },
            heartRateMeasuredAtEpochMillis = o.optLong("heartRateMeasuredAtEpochMillis")
                .takeIf { o.has("heartRateMeasuredAtEpochMillis") && !o.isNull("heartRateMeasuredAtEpochMillis") },
            stepsToday = o.optInt("stepsToday").takeIf { o.has("stepsToday") && !o.isNull("stepsToday") },
            activeEnergyKcal = o.optDouble("activeEnergyKcal")
                .takeIf { o.has("activeEnergyKcal") && !o.isNull("activeEnergyKcal") },
            recentHeartRates = decodeHeartRates(o.optJSONArray("recentHeartRates")),
            recentDailyActivity = decodeDaily(o.optJSONArray("recentDailyActivity")),
            recentWorkouts = decodeWorkouts(o.optJSONArray("recentWorkouts")),
            recentECGs = decodeECGs(o.optJSONArray("recentECGs")),
            syncedAtEpochMillis = o.optLong("syncedAtEpochMillis", System.currentTimeMillis()),
            restingHeartRateBpm = o.optDouble("restingHeartRateBpm")
                .takeIf { o.has("restingHeartRateBpm") && !o.isNull("restingHeartRateBpm") },
            restingHeartRateAvg90d = o.optDouble("restingHeartRateAvg90d")
                .takeIf { o.has("restingHeartRateAvg90d") && !o.isNull("restingHeartRateAvg90d") },
            vo2Max = o.optDouble("vo2Max").takeIf { o.has("vo2Max") && !o.isNull("vo2Max") },
            vo2MaxRecent = o.optDouble("vo2MaxRecent").takeIf { o.has("vo2MaxRecent") && !o.isNull("vo2MaxRecent") },
            weeklyExerciseMinutesAvg = o.optDouble("weeklyExerciseMinutesAvg")
                .takeIf { o.has("weeklyExerciseMinutesAvg") && !o.isNull("weeklyExerciseMinutesAvg") },
            spo2NightlyAvgPercent = o.optDouble("spo2NightlyAvgPercent")
                .takeIf { o.has("spo2NightlyAvgPercent") && !o.isNull("spo2NightlyAvgPercent") },
            stepsDailyAvg90d = o.optDouble("stepsDailyAvg90d")
                .takeIf { o.has("stepsDailyAvg90d") && !o.isNull("stepsDailyAvg90d") },
            hrvSdnnMsAvg90d = o.optDouble("hrvSdnnMsAvg90d")
                .takeIf { o.has("hrvSdnnMsAvg90d") && !o.isNull("hrvSdnnMsAvg90d") },
            wearablePeriodStartEpochMillis = o.optLong("wearablePeriodStartEpochMillis")
                .takeIf { o.has("wearablePeriodStartEpochMillis") && !o.isNull("wearablePeriodStartEpochMillis") },
            wearablePeriodEndEpochMillis = o.optLong("wearablePeriodEndEpochMillis")
                .takeIf { o.has("wearablePeriodEndEpochMillis") && !o.isNull("wearablePeriodEndEpochMillis") },
        )
    }.getOrNull()

    private fun decodeHeartRates(arr: JSONArray?): List<HealthHeartRateReading> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    HealthHeartRateReading(
                        id = item.optString("id", i.toString()),
                        bpm = item.optDouble("bpm"),
                        measuredAtEpochMillis = item.optLong("measuredAtEpochMillis"),
                    ),
                )
            }
        }
    }

    private fun decodeDaily(arr: JSONArray?): List<HealthDailyActivity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    HealthDailyActivity(
                        id = item.optString("id", i.toString()),
                        dayEpochMillis = item.optLong("dayEpochMillis"),
                        steps = item.optInt("steps").takeIf { item.has("steps") && !item.isNull("steps") },
                        activeEnergyKcal = item.optDouble("activeEnergyKcal")
                            .takeIf { item.has("activeEnergyKcal") && !item.isNull("activeEnergyKcal") },
                    ),
                )
            }
        }
    }

    private fun decodeWorkouts(arr: JSONArray?): List<HealthWorkoutEntry> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    HealthWorkoutEntry(
                        id = item.optString("id", i.toString()),
                        title = item.optString("title", "Allenamento"),
                        startedAtEpochMillis = item.optLong("startedAtEpochMillis"),
                        durationMinutes = item.optInt("durationMinutes")
                            .takeIf { item.has("durationMinutes") && !item.isNull("durationMinutes") },
                        activeEnergyKcal = item.optDouble("activeEnergyKcal")
                            .takeIf { item.has("activeEnergyKcal") && !item.isNull("activeEnergyKcal") },
                    ),
                )
            }
        }
    }

    private fun decodeECGs(arr: JSONArray?): List<HealthECGEntry> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    HealthECGEntry(
                        id = item.optString("id", i.toString()),
                        recordedAtEpochMillis = item.optLong("recordedAtEpochMillis"),
                        classificationLabel = item.optString("classificationLabel", ""),
                        averageHeartRateBpm = item.optDouble("averageHeartRateBpm")
                            .takeIf { item.has("averageHeartRateBpm") && !item.isNull("averageHeartRateBpm") },
                    ),
                )
            }
        }
    }
}
