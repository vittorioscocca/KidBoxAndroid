package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.util.KBLog
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Capacità operative del copilota: l'AI non si limita a rispondere, può
 * modificare il piano.
 *
 * Il meccanismo è quello già usato dalle altre chat KidBox: l'assistente allega
 * alla risposta un blocco JSON fra due marcatori, il client lo esegue e lo
 * rimuove dal testo mostrato. I marcatori sono dedicati al fitness, così le
 * altre pipeline di azioni non provano a eseguire ciò che non conoscono.
 */
object FitnessCopilotActionMarkers {
    const val START = "<<<KIDBOX_FITNESS_ACTIONS>>>"
    const val END = "<<<END_KIDBOX_FITNESS_ACTIONS>>>"
}

/**
 * Modifica applicata al piano. Porta la data e non la frase: il riepilogo va
 * localizzato, e qui non c'è un `Context`.
 */
sealed interface FitnessCopilotChange {
    val dateEpochMillis: Long

    data class Replaced(override val dateEpochMillis: Long) : FitnessCopilotChange
    data class Moved(override val dateEpochMillis: Long) : FitnessCopilotChange
    data class StatusUpdated(override val dateEpochMillis: Long) : FitnessCopilotChange
    data class Added(override val dateEpochMillis: Long) : FitnessCopilotChange
    data class Deleted(override val dateEpochMillis: Long) : FitnessCopilotChange
}

data class FitnessCopilotProcessedReply(
    val displayText: String,
    val plan: FitnessPlanDocument,
    val changes: List<FitnessCopilotChange>,
    /**
     * Azioni allegate alla risposta che non è stato possibile eseguire.
     *
     * Serve a non lasciar passare una conferma falsa: il testo discorsivo dice
     * "ho spostato la seduta" anche quando l'id era inventato o il JSON era
     * malformato, e senza questo l'utente se ne accorgerebbe solo tornando sul
     * calendario.
     */
    val failedActions: Int = 0,
)

object FitnessCopilotActionExecutor {

    private const val TAG = "FitnessCopilot"

    /**
     * Estrae le azioni dalla risposta, le applica al piano e restituisce il
     * testo ripulito da mostrare in chat.
     */
    fun process(reply: String, plan: FitnessPlanDocument): FitnessCopilotProcessedReply {
        val extracted = extractActions(reply)
        val display = extracted.displayText
        val actions = extracted.actions
        // Nessun blocco: la risposta è solo testo.
        if (!extracted.blockFound) {
            return FitnessCopilotProcessedReply(display, plan, emptyList())
        }

        var updated = plan
        val changes = mutableListOf<FitnessCopilotChange>()

        for (action in actions) {

            // L'aggiunta è l'unica azione che non parte da una seduta esistente:
            // va gestita prima del controllo sul sessionId.
            if (action.optString("type") == "add_session") {
                val date = parseDate(action.optString("date")) ?: continue
                // Fuori dall'orizzonte del piano non c'è settimana in cui
                // metterla: meglio non applicarla che inventarne una.
                val weekIndex = updated.weekIndexFor(date) ?: continue
                val rawTitle = action.optString("title").trim().takeIf { it.isNotBlank() }
                val activityType = action.optString("activityType").trim()
                    .takeIf { it.isNotBlank() } ?: rawTitle ?: continue
                val title = rawTitle ?: activityType

                val session = FitnessSession(
                    id = UUID.randomUUID().toString(),
                    dateEpochMillis = date,
                    weekIndex = weekIndex,
                    title = title,
                    activityType = activityType,
                    durationMinutes = action.optInt("durationMinutes")
                        .takeIf { it > 0 }?.coerceAtLeast(10) ?: 45,
                    intensity = action.optString("intensity").trim(),
                    exercises = action.optJSONArray("exercises")?.let { exercises(it) } ?: emptyList(),
                    targets = action.optJSONArray("targets")?.let { strings(it) } ?: emptyList(),
                    targetKcal = if (action.has("targetKcal")) action.optInt("targetKcal") else null,
                    notes = action.optString("notes").trim().takeIf { it.isNotBlank() },
                )
                updated = updated.copy(
                    weeks = updated.weeks.map { week ->
                        if (week.index != weekIndex) {
                            week
                        } else {
                            week.copy(
                                sessions = (week.sessions + session)
                                    .sortedBy { it.dateEpochMillis },
                            )
                        }
                    },
                )
                changes += FitnessCopilotChange.Added(date)
                continue
            }

            // Il prompt elenca le sedute come `id=…`: il modello a volte copia
            // anche il prefisso.
            val sessionId = action.optString("sessionId").trim().removePrefix("id=")
                .takeIf { it.isNotBlank() } ?: continue
            val existing = updated.session(sessionId) ?: continue

            when (action.optString("type")) {
                // Si applica subito come le altre: la richiesta in chat è già
                // la conferma dell'utente. Con la finestra intermedia il
                // modello scriveva "ho eliminato" e la seduta restava sul
                // calendario.
                "delete_session" -> {
                    updated = updated.removeSession(sessionId)
                    changes += FitnessCopilotChange.Deleted(existing.dateEpochMillis)
                }

                "replace_session" -> {
                    updated = updated.updateSession(sessionId) { session ->
                        session.copy(
                            title = action.optString("title")
                                .takeIf { it.isNotBlank() } ?: session.title,
                            activityType = action.optString("activityType")
                                .takeIf { it.isNotBlank() } ?: session.activityType,
                            durationMinutes = action.optInt("durationMinutes")
                                .takeIf { it > 0 } ?: session.durationMinutes,
                            intensity = action.optString("intensity")
                                .takeIf { it.isNotBlank() } ?: session.intensity,
                            exercises = action.optJSONArray("exercises")
                                ?.let { exercises(it) } ?: session.exercises,
                            targets = action.optJSONArray("targets")
                                ?.let { strings(it) } ?: session.targets,
                            targetKcal = if (action.has("targetKcal")) {
                                action.optInt("targetKcal")
                            } else {
                                session.targetKcal
                            },
                            notes = action.optString("notes")
                                .takeIf { it.isNotBlank() } ?: session.notes,
                            status = FitnessSessionStatus.PLANNED,
                        )
                    }
                    changes += FitnessCopilotChange.Replaced(existing.dateEpochMillis)
                }

                "move_session" -> {
                    val newDate = parseDate(action.optString("date")) ?: continue
                    updated = updated.updateSession(sessionId) { session ->
                        session.copy(
                            originalDateEpochMillis = session.originalDateEpochMillis
                                ?: session.dateEpochMillis,
                            dateEpochMillis = newDate,
                            status = FitnessSessionStatus.PLANNED,
                        )
                    }
                    changes += FitnessCopilotChange.Moved(newDate)
                }

                "mark_session" -> {
                    val status = FitnessSessionStatus.entries.firstOrNull {
                        it.name.equals(action.optString("status"), ignoreCase = true)
                    } ?: continue
                    updated = updated.updateSession(sessionId) { session ->
                        session.copy(
                            status = status,
                            completedAtEpochMillis = if (status == FitnessSessionStatus.DONE) {
                                System.currentTimeMillis()
                            } else {
                                null
                            },
                            completionSource = if (status == FitnessSessionStatus.DONE) {
                                FitnessCompletionSource.MANUAL
                            } else {
                                null
                            },
                        )
                    }
                    changes += FitnessCopilotChange.StatusUpdated(existing.dateEpochMillis)
                }

                else -> KBLog.ai.info("azione ignota type=${action.optString("type")}", TAG)
            }
        }

        return FitnessCopilotProcessedReply(
            displayText = display,
            plan = updated,
            changes = changes,
            failedActions = (actions.size - changes.size).coerceAtLeast(0) + extracted.undecodable,
        )
    }

    private class ExtractedActions(
        val displayText: String,
        val actions: List<JSONObject>,
        /** Azioni (o blocchi interi) che c'erano ma non si sono potute leggere. */
        val undecodable: Int,
        val blockFound: Boolean,
    )

    /**
     * Estrae **tutti** i blocchi di azioni dalla risposta.
     *
     * Due modi in cui una modifica annunciata andava persa senza avviso:
     * - il modello spezza le azioni in più blocchi, e si leggeva solo il primo;
     * - la risposta viene troncata dal limite di token prima del marcatore di
     *   chiusura, e il blocco non veniva nemmeno riconosciuto (né rimosso dal
     *   testo, né segnalato).
     * Parity con `FitnessCopilotActionExecutor.extractActions` su iOS.
     */
    private fun extractActions(reply: String): ExtractedActions {
        val display = StringBuilder()
        val actions = mutableListOf<JSONObject>()
        var undecodable = 0
        var blockFound = false
        var cursor = 0

        while (true) {
            val start = reply.indexOf(FitnessCopilotActionMarkers.START, cursor)
            if (start < 0) break
            blockFound = true
            display.append(reply, cursor, start)
            val jsonStart = start + FitnessCopilotActionMarkers.START.length
            val end = reply.indexOf(FitnessCopilotActionMarkers.END, jsonStart)
            if (end < 0) {
                // Blocco troncato: quel che resta non è testo da mostrare, e le
                // azioni che contiene non sono affidabili.
                KBLog.ai.error("blocco azioni senza chiusura (risposta troncata?)", TAG)
                undecodable += 1
                cursor = reply.length
                break
            }
            val (decoded, failed) = decodeActions(reply.substring(jsonStart, end))
            actions += decoded
            undecodable += failed
            cursor = end + FitnessCopilotActionMarkers.END.length
        }
        display.append(reply, cursor, reply.length)

        return ExtractedActions(display.toString().trim(), actions, undecodable, blockFound)
    }

    private fun decodeActions(raw: String): Pair<List<JSONObject>, Int> {
        var json = raw.trim()
        // Recinzione Markdown attorno al JSON.
        if (json.startsWith("```")) {
            json = json.substringAfter('\n', "").trim().removeSuffix("```").trim()
        }
        // Array di azioni, singola azione, oppure `{"actions": [...]}`.
        val root = runCatching { JSONTokener(json).nextValue() }.getOrNull()
        val array: JSONArray = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("actions") ?: JSONArray().put(root)
            else -> {
                KBLog.ai.error("blocco azioni non decodificabile", TAG)
                return emptyList<JSONObject>() to 1
            }
        }
        val actions = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        return actions to (array.length() - actions.size)
    }

    private fun exercises(array: JSONArray): List<FitnessExercise> =
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            FitnessExercise(
                name = name,
                detail = item.optString("detail").trim(),
                notes = item.optString("notes").trim().takeIf { it.isNotBlank() },
            )
        }

    private fun strings(array: JSONArray): List<String> =
        (0 until array.length()).mapNotNull {
            array.optString(it).trim().takeIf { value -> value.isNotBlank() }
        }

    /** Data in formato `yyyy-MM-dd`, come richiesto nel system prompt. */
    private fun parseDate(raw: String): Long? {
        if (raw.isBlank()) return null
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return runCatching { formatter.parse(raw)?.time }.getOrNull()
            ?.let { FitnessPlanDates.startOfDay(it) }
    }
}
