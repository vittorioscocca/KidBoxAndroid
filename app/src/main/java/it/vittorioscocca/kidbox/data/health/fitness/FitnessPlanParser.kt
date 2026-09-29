package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.util.KBLog
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Legge il JSON prodotto dall'AI e lo trasforma in [FitnessPlanDocument].
 *
 * Il modello a volte incornicia il JSON con una frase o con un blocco di codice
 * Markdown, nonostante il prompt lo vieti: qui isoliamo l'oggetto tra la prima
 * graffa aperta e l'ultima chiusa invece di fallire.
 */
object FitnessPlanParser {

    private const val TAG = "FitnessPlanParser"

    fun parsePlan(
        raw: String,
        subjectName: String,
        input: FitnessPlanInput,
        startDateEpochMillis: Long,
        messageUnitsConsumed: Int,
    ): FitnessPlanDocument {
        val json = jsonObject(raw) ?: run {
            KBLog.ai.error("nessun JSON riconoscibile nella risposta", TAG)
            throw FitnessPlanError.InvalidPlanFormat
        }

        val summary = json.optString("summary").trim()
        val safetyNotes = json.optJSONArray("safetyNotes").toStringList()

        val rawWeeks = json.optJSONArray("weeks") ?: JSONArray()
        val weeks = mutableListOf<FitnessWeek>()
        for (position in 0 until rawWeeks.length()) {
            val rawWeek = rawWeeks.optJSONObject(position) ?: continue
            val index = rawWeek.optIntOrNull("index") ?: (position + 1)
            val sessions = (rawWeek.optJSONArray("sessions") ?: JSONArray())
                .objects()
                .mapNotNull { session(it, index, startDateEpochMillis) }
                .sortedBy { it.dateEpochMillis }
            if (sessions.isEmpty()) continue
            weeks += FitnessWeek(
                index = index,
                focus = rawWeek.optString("focus").trim(),
                sessions = sessions,
            )
        }

        val uniqueWeeks = oneSessionPerDay(weeks)
        if (uniqueWeeks.isEmpty()) {
            KBLog.ai.error("JSON senza settimane utilizzabili", TAG)
            throw FitnessPlanError.InvalidPlanFormat
        }

        return FitnessPlanDocument(
            subjectName = subjectName,
            input = input,
            startDateEpochMillis = FitnessPlanDates.startOfDay(startDateEpochMillis),
            summary = summary,
            safetyNotes = safetyNotes,
            weeks = uniqueWeeks.sortedBy { it.index },
            generatedAtEpochMillis = System.currentTimeMillis(),
            messageUnitsConsumed = messageUnitsConsumed,
        )
    }

    /**
     * Una seduta per giorno, anche quando l'AI ne mette due sullo stesso
     * `dayOffset` (è successo nel settembre 2026: lunedì e mercoledì doppi
     * nella settimana 4, mai chiesti). Il doppione non si vede sul calendario
     * ma pesa nel report: resta la seduta più lunga, che è quella principale.
     * Parity con `oneSessionPerDay` su iOS.
     */
    fun oneSessionPerDay(weeks: List<FitnessWeek>): List<FitnessWeek> {
        val keptByDay = mutableMapOf<Long, FitnessSession>()
        weeks.flatMap { it.sessions }.forEach { session ->
            val day = FitnessPlanDates.startOfDay(session.dateEpochMillis)
            val kept = keptByDay[day]
            if (kept == null || session.durationMinutes > kept.durationMinutes) keptByDay[day] = session
        }
        val keep = keptByDay.values.map { it.id }.toSet()
        val dropped = weeks.sumOf { it.sessions.size } - keep.size
        if (dropped > 0) KBLog.ai.error("scartate $dropped sedute doppie sullo stesso giorno", TAG)
        return weeks
            .map { week -> week.copy(sessions = week.sessions.filter { it.id in keep }) }
            .filter { it.sessions.isNotEmpty() }
    }

    /** Aggiornamento parziale: spostamento di una seduta o adeguamento settimanale. */
    data class SessionUpdates(
        val rationale: String,
        val changes: List<String>,
        val sessions: List<FitnessSession>,
    )

    fun parseSessionUpdates(
        raw: String,
        startDateEpochMillis: Long,
        fallbackWeekIndex: Int,
    ): SessionUpdates {
        val json = jsonObject(raw) ?: throw FitnessPlanError.InvalidPlanFormat
        return SessionUpdates(
            rationale = json.optString("rationale").trim(),
            changes = json.optJSONArray("changes").toStringList(),
            sessions = (json.optJSONArray("sessions") ?: JSONArray())
                .objects()
                .mapNotNull { session(it, fallbackWeekIndex, startDateEpochMillis) },
        )
    }

    private fun session(
        raw: JSONObject,
        weekIndex: Int,
        startDateEpochMillis: Long,
    ): FitnessSession? {
        val dayOffset = raw.optIntOrNull("dayOffset") ?: return null
        val date = FitnessPlanDates.plusDays(startDateEpochMillis, dayOffset)

        val title = raw.optString("title").trim()
        val activityType = raw.optString("activityType").trim()
        if (title.isBlank() && activityType.isBlank()) return null

        val exercises = (raw.optJSONArray("exercises") ?: JSONArray())
            .objects()
            .mapNotNull { item ->
                val name = item.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                FitnessExercise(
                    name = name,
                    detail = item.optString("detail").trim(),
                    notes = item.optString("notes").trim().takeIf { it.isNotBlank() },
                )
            }

        // L'id lo decide l'AI solo negli aggiornamenti parziali; alla prima
        // generazione lo assegniamo noi, così resta stabile tra i salvataggi.
        val id = raw.optString("id").trim().takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        return FitnessSession(
            id = id,
            dateEpochMillis = date,
            weekIndex = weekIndex,
            title = title.ifBlank { activityType },
            activityType = activityType.ifBlank { title },
            durationMinutes = raw.optIntOrNull("durationMinutes") ?: 40,
            intensity = raw.optString("intensity").trim(),
            exercises = exercises,
            targets = raw.optJSONArray("targets").toStringList(),
            targetKcal = raw.optIntOrNull("targetKcal"),
            notes = raw.optString("notes").trim().takeIf { it.isNotBlank() },
        )
    }

    /** Isola l'oggetto JSON dalla risposta, tollerando testo o ``` attorno. */
    fun jsonObject(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).trim().takeIf { s -> s.isNotBlank() } }
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    /** `optInt` non distingue "assente" da 0, e un dayOffset 0 è legittimo. */
    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }
}
