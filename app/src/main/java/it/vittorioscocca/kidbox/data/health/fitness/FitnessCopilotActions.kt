package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.util.KBLog
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import org.json.JSONArray

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
}

data class FitnessCopilotProcessedReply(
    val displayText: String,
    val plan: FitnessPlanDocument,
    val changes: List<FitnessCopilotChange>,
    /**
     * Sedute che l'AI vuole eliminare: **non** ancora rimosse dal piano.
     *
     * L'eliminazione è l'unica azione che non si applica da sola. Le altre sono
     * rimediabili — una seduta spostata si rimette a posto, una segnata per
     * errore si riapre — mentre una seduta cancellata non torna indietro:
     * l'ultima parola resta all'utente.
     */
    val pendingDeletions: List<FitnessSession> = emptyList(),
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
        val start = reply.indexOf(FitnessCopilotActionMarkers.START)
        val end = if (start < 0) -1 else reply.indexOf(FitnessCopilotActionMarkers.END, start + 1)
        if (start < 0 || end < 0) {
            return FitnessCopilotProcessedReply(reply.trim(), plan, emptyList())
        }

        val json = reply.substring(start + FitnessCopilotActionMarkers.START.length, end).trim()
        val display = (
            reply.substring(0, start) +
                reply.substring(end + FitnessCopilotActionMarkers.END.length)
            ).trim()

        val actions = runCatching { JSONArray(json) }.getOrNull()
        if (actions == null || actions.length() == 0) {
            KBLog.ai.error("blocco azioni non decodificabile", TAG)
            // Il blocco c'era: la risposta parla di una modifica che non è
            // avvenuta, e va segnalato.
            return FitnessCopilotProcessedReply(display, plan, emptyList(), failedActions = 1)
        }

        var updated = plan
        val changes = mutableListOf<FitnessCopilotChange>()
        val pendingDeletions = mutableListOf<FitnessSession>()

        for (index in 0 until actions.length()) {
            val action = actions.optJSONObject(index) ?: continue

            // L'aggiunta è l'unica azione che non parte da una seduta esistente:
            // va gestita prima del controllo sul sessionId.
            if (action.optString("type") == "add_session") {
                val date = parseDate(action.optString("date")) ?: continue
                // Fuori dall'orizzonte del piano non c'è settimana in cui
                // metterla: meglio non applicarla che inventarne una.
                val weekIndex = updated.weekIndexFor(date) ?: continue
                val title = action.optString("title").trim().takeIf { it.isNotBlank() } ?: continue
                val activityType = action.optString("activityType").trim()
                    .takeIf { it.isNotBlank() } ?: continue

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

            val sessionId = action.optString("sessionId").takeIf { it.isNotBlank() } ?: continue
            val existing = updated.session(sessionId) ?: continue

            // L'eliminazione non si applica qui: si mette in attesa di conferma.
            if (action.optString("type") == "delete_session") {
                pendingDeletions += existing
                continue
            }

            when (action.optString("type")) {
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
            pendingDeletions = pendingDeletions,
            // Un'eliminazione in attesa non è un fallimento: è stata capita, e
            // aspetta solo l'ultima parola dell'utente.
            failedActions = (actions.length() - changes.size - pendingDeletions.size)
                .coerceAtLeast(0),
        )
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
