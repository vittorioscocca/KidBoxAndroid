package it.vittorioscocca.kidbox.data.health.fitness

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializzazione del piano fitness.
 *
 * **Il formato è condiviso con iOS**: lo stesso documento Firestore
 * (`users/{uid}/fitnessPlans/{childId}`) viene letto e scritto da entrambi i
 * client, quindi il formato sul filo è uno solo ed è quello che iOS produce con
 * `JSONEncoder`:
 *
 *   - le date sono stringhe **ISO 8601 senza frazioni di secondo** (`date`,
 *     `startDate`, `generatedAt`, …), non millisecondi;
 *   - i valori degli enum sono in **camelCase** (`weightLoss`, `martialArts`),
 *     come i `rawValue` Swift, non in SCREAMING_SNAKE come i nomi Kotlin.
 *
 * Due vincoli che sembrano dettagli e non lo sono:
 *
 *   1. il decoder Swift con strategia `.iso8601` **rifiuta i millisecondi**:
 *      le date vanno troncate ai secondi;
 *   2. il decoder sintetizzato di Swift **non applica i valori di default** per
 *      le chiavi mancanti: ogni campo non opzionale lato iOS (`id`, `title`,
 *      `activityType`, `status`, …) va scritto sempre, anche quando qui
 *      avrebbe un default, altrimenti iOS non decodifica l'intero piano.
 *
 * La lettura accetta anche il vecchio formato solo-Android (`*EpochMillis` e
 * nomi enum in maiuscolo), per non perdere i piani generati prima di questo
 * allineamento.
 */
object FitnessPlanJson {

    fun encode(document: FitnessPlanDocument): String = JSONObject().apply {
        put("subjectName", document.subjectName)
        put("startDate", isoDate(document.startDateEpochMillis))
        put("summary", document.summary)
        put("safetyNotes", JSONArray(document.safetyNotes))
        put("generatedAt", isoDate(document.generatedAtEpochMillis))
        put("messageUnitsConsumed", document.messageUnitsConsumed)
        put("input", encodeInput(document.input))
        put(
            "weeks",
            JSONArray().apply {
                document.weeks.forEach { week ->
                    put(
                        JSONObject().apply {
                            put("index", week.index)
                            put("focus", week.focus)
                            put(
                                "sessions",
                                JSONArray().apply {
                                    week.sessions.forEach { put(encodeSession(it)) }
                                },
                            )
                        },
                    )
                }
            },
        )
    }.toString()

    /**
     * `null` quando il documento non è utilizzabile: senza data d'inizio o
     * senza sedute il piano non si può disegnare, e restituirlo mezzo vuoto
     * significherebbe sovrascrivere con esso un piano locale valido.
     */
    fun decode(raw: String): FitnessPlanDocument? = runCatching {
        val json = JSONObject(raw)
        val start = json.dateOrNull("startDate", "startDateEpochMillis") ?: return null
        val rawWeeks = json.optJSONArray("weeks") ?: JSONArray()
        val weeks = (0 until rawWeeks.length()).mapNotNull { index ->
            val week = rawWeeks.optJSONObject(index) ?: return@mapNotNull null
            val sessions = week.optJSONArray("sessions") ?: JSONArray()
            FitnessWeek(
                index = week.optInt("index", index + 1),
                focus = week.optString("focus"),
                sessions = (0 until sessions.length()).mapNotNull { position ->
                    sessions.optJSONObject(position)?.let { decodeSession(it) }
                },
            )
        }
        if (weeks.all { it.sessions.isEmpty() }) return null

        FitnessPlanDocument(
            subjectName = json.optString("subjectName"),
            input = decodeInput(json.optJSONObject("input") ?: JSONObject()),
            startDateEpochMillis = start,
            summary = json.optString("summary"),
            safetyNotes = json.optJSONArray("safetyNotes").toStringList(),
            weeks = weeks,
            generatedAtEpochMillis = json.dateOrNull("generatedAt", "generatedAtEpochMillis")
                ?: System.currentTimeMillis(),
            messageUnitsConsumed = json.optInt("messageUnitsConsumed", 0),
        )
    }.getOrNull()

    // ── Input ──────────────────────────────────────────────────────────────

    private fun encodeInput(input: FitnessPlanInput) = JSONObject().apply {
        put("goal", input.goal.wire())
        put("preferredSports", JSONArray(input.preferredSports.map { it.wire() }))
        input.raceType?.let { put("raceType", it.wire()) }
        put("raceDetail", input.raceDetail)
        input.raceDateEpochMillis?.let { put("raceDate", isoDate(it)) }
        put("trainingWeekdays", JSONArray(input.trainingWeekdays.toList()))
        put("reminderMinutesFromMidnight", input.reminderMinutesFromMidnight)
        put("reminderEnabled", input.reminderEnabled)
        put("sessionMinutes", input.sessionMinutes)
        put("experience", input.experience.wire())
        put("place", input.place.wire())
        put("notes", input.notes)
        put("manualAgeYears", input.manualAgeYears)
        put("manualWeightKg", input.manualWeightKg)
        put("manualHeightCm", input.manualHeightCm)
    }

    private fun decodeInput(json: JSONObject): FitnessPlanInput {
        val defaults = FitnessPlanInput()
        val weekdays = json.optJSONArray("trainingWeekdays")
        return FitnessPlanInput(
            goal = enumFromWire(json.optString("goal"), FitnessGoal.entries, FitnessGoal.TONING),
            preferredSports = json.optJSONArray("preferredSports").toStringList()
                .mapNotNull { wire -> FitnessSport.entries.firstOrNull { it.matches(wire) } }
                .toSet(),
            raceType = FitnessSport.entries.firstOrNull { it.matches(json.optString("raceType")) },
            raceDetail = json.optString("raceDetail"),
            raceDateEpochMillis = json.dateOrNull("raceDate", "raceDateEpochMillis"),
            trainingWeekdays = if (weekdays == null || weekdays.length() == 0) {
                defaults.trainingWeekdays
            } else {
                (0 until weekdays.length()).map { weekdays.optInt(it) }.filter { it in 1..7 }.toSet()
            },
            reminderMinutesFromMidnight = json.optInt(
                "reminderMinutesFromMidnight",
                defaults.reminderMinutesFromMidnight,
            ),
            reminderEnabled = json.optBoolean("reminderEnabled", true),
            sessionMinutes = json.optInt("sessionMinutes", defaults.sessionMinutes),
            experience = enumFromWire(
                json.optString("experience"),
                FitnessExperience.entries,
                FitnessExperience.BEGINNER,
            ),
            place = enumFromWire(json.optString("place"), FitnessPlace.entries, FitnessPlace.HOME),
            notes = json.optString("notes"),
            manualAgeYears = json.optString("manualAgeYears"),
            manualWeightKg = json.optString("manualWeightKg"),
            manualHeightCm = json.optString("manualHeightCm"),
        )
    }

    // ── Seduta ─────────────────────────────────────────────────────────────

    private fun encodeSession(session: FitnessSession) = JSONObject().apply {
        put("id", session.id)
        put("date", isoDate(session.dateEpochMillis))
        session.originalDateEpochMillis?.let { put("originalDate", isoDate(it)) }
        put("weekIndex", session.weekIndex)
        put("title", session.title)
        put("activityType", session.activityType)
        put("durationMinutes", session.durationMinutes)
        put("intensity", session.intensity)
        put(
            "exercises",
            JSONArray().apply {
                session.exercises.forEach { exercise ->
                    put(
                        JSONObject().apply {
                            // `id` non è opzionale lato iOS: senza, l'intero
                            // piano non si decodifica su quel client.
                            put("id", UUID.randomUUID().toString())
                            put("name", exercise.name)
                            put("detail", exercise.detail)
                            exercise.notes?.let { put("notes", it) }
                        },
                    )
                }
            },
        )
        put("targets", JSONArray(session.targets))
        session.targetKcal?.let { put("targetKcal", it) }
        session.notes?.let { put("notes", it) }
        put("status", session.status.wire())
        session.completedAtEpochMillis?.let { put("completedAt", isoDate(it)) }
        session.completionSource?.let { put("completionSource", it.wire()) }
        session.matchedWorkoutId?.let { put("matchedWorkoutId", it) }
        session.actualMinutes?.let { put("actualMinutes", it) }
        session.actualKcal?.let { put("actualKcal", it) }
    }

    private fun decodeSession(json: JSONObject): FitnessSession? {
        val date = json.dateOrNull("date", "dateEpochMillis") ?: return null
        val exercises = json.optJSONArray("exercises") ?: JSONArray()
        return FitnessSession(
            id = json.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            dateEpochMillis = date,
            originalDateEpochMillis = json.dateOrNull("originalDate", "originalDateEpochMillis"),
            weekIndex = json.optInt("weekIndex", 1),
            title = json.optString("title"),
            activityType = json.optString("activityType"),
            durationMinutes = json.optInt("durationMinutes", 40),
            intensity = json.optString("intensity"),
            exercises = (0 until exercises.length()).mapNotNull { index ->
                val item = exercises.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name")
                if (name.isBlank()) return@mapNotNull null
                FitnessExercise(
                    name = name,
                    detail = item.optString("detail"),
                    notes = item.optString("notes").takeIf { it.isNotBlank() },
                )
            },
            targets = json.optJSONArray("targets").toStringList(),
            targetKcal = if (json.has("targetKcal")) json.optInt("targetKcal") else null,
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            status = enumFromWire(
                json.optString("status"),
                FitnessSessionStatus.entries,
                FitnessSessionStatus.PLANNED,
            ),
            completedAtEpochMillis = json.dateOrNull("completedAt", "completedAtEpochMillis"),
            completionSource = FitnessCompletionSource.entries
                .firstOrNull { it.matches(json.optString("completionSource")) },
            matchedWorkoutId = json.optString("matchedWorkoutId").takeIf { it.isNotBlank() },
            actualMinutes = if (json.has("actualMinutes")) json.optInt("actualMinutes") else null,
            actualKcal = if (json.has("actualKcal")) json.optInt("actualKcal") else null,
        )
    }

    // ── Date ───────────────────────────────────────────────────────────────

    /** ISO 8601 troncato ai secondi: con i millisecondi iOS rifiuta la data. */
    private fun isoDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).toString()

    /**
     * Legge una data dal nome condiviso (stringa ISO) oppure dal vecchio nome
     * solo-Android (millisecondi). `null` se manca o non è interpretabile.
     */
    private fun JSONObject.dateOrNull(isoKey: String, legacyMillisKey: String): Long? {
        optString(isoKey).takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
        }
        return optLong(legacyMillisKey).takeIf { it > 0L }
    }

    // ── Enum ───────────────────────────────────────────────────────────────

    /**
     * `WEIGHT_LOSS` → `weightLoss`. Unica eccezione `HEALTH_CONNECT`, che sul
     * filo resta `healthKit`: il vocabolario del documento condiviso è quello
     * di iOS, che è arrivato prima.
     */
    private fun Enum<*>.wire(): String {
        if (name == "HEALTH_CONNECT") return "healthKit"
        return name.split("_").mapIndexed { index, part ->
            val lower = part.lowercase()
            if (index == 0) lower else lower.replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }

    /** Accetta il nome sul filo (camelCase) e il vecchio nome Kotlin (maiuscolo). */
    private fun Enum<*>.matches(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return raw == wire() || raw == name || (name == "HEALTH_CONNECT" && raw == "healthConnect")
    }

    private fun <T : Enum<T>> enumFromWire(raw: String?, values: List<T>, default: T): T =
        values.firstOrNull { it.matches(raw) } ?: default

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }
}
