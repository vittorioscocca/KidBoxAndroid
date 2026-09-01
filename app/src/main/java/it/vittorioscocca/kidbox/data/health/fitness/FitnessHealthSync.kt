package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.data.health.HealthConnectGateway
import it.vittorioscocca.kidbox.domain.model.HealthWorkoutEntry
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Riconciliazione tra il piano e gli allenamenti registrati da Health Connect
 * (Wear OS, Garmin, Strava e ogni altra sorgente che ci scrive).
 *
 * Due inneschi, come da specifica: passivo all'apertura della schermata e
 * attivo dal pulsante "Sincronizza ora".
 */
@Singleton
class FitnessHealthSync @Inject constructor(
    private val healthConnect: HealthConnectGateway,
) {

    /**
     * Quanto deve durare un allenamento, in proporzione alla seduta prevista,
     * perché valga come completata. Sotto questa soglia resta "da fare": una
     * camminata di 5 minuti non chiude una seduta di forza da 45.
     */
    private val minimumDurationRatio = 0.5

    /** Minuti minimi comunque richiesti, anche per sedute brevi. */
    private val minimumMinutes = 10

    data class Result(
        val plan: FitnessPlanDocument,
        val matchedSessions: List<FitnessSession>,
    ) {
        val didChange: Boolean get() = matchedSessions.isNotEmpty()
    }

    /**
     * Confronta le attività lette da Health Connect con le sedute pianificate e
     * chiude quelle coperte da un allenamento reale.
     *
     * Si guardano solo le sedute **passate o di oggi** e ancora `PLANNED`: una
     * seduta già chiusa a mano non viene toccata, e una futura non può essere
     * completata in anticipo.
     */
    suspend fun reconcile(plan: FitnessPlanDocument): Result {
        val today = FitnessPlanDates.today()
        val pending = plan.allSessions.filter {
            !it.isRest && it.status == FitnessSessionStatus.PLANNED && it.dateEpochMillis <= today
        }
        if (pending.isEmpty()) return Result(plan, emptyList())

        val windowStart = pending.minOf { it.dateEpochMillis }
        val workouts = healthConnect.workoutsSince(windowStart)
        if (workouts.isEmpty()) return Result(plan, emptyList())

        // Un allenamento chiude al massimo una seduta: senza questo insieme una
        // corsa lunga chiuderebbe tutte le sedute arretrate dello stesso giorno.
        val usedWorkoutIds = plan.allSessions.mapNotNull { it.matchedWorkoutId }.toMutableSet()
        var updated = plan
        val matched = mutableListOf<FitnessSession>()

        pending.sortedBy { it.dateEpochMillis }.forEach { session ->
            val sameDay = workouts.filter {
                it.id !in usedWorkoutIds &&
                    FitnessPlanDates.startOfDay(it.startedAtEpochMillis) ==
                    FitnessPlanDates.startOfDay(session.dateEpochMillis)
            }
            val workout = bestMatch(session, sameDay) ?: return@forEach

            usedWorkoutIds += workout.id
            updated = updated.updateSession(session.id) { target ->
                target.copy(
                    status = FitnessSessionStatus.DONE,
                    completedAtEpochMillis = workout.startedAtEpochMillis,
                    completionSource = FitnessCompletionSource.HEALTH_CONNECT,
                    matchedWorkoutId = workout.id,
                    actualMinutes = workout.durationMinutes,
                    actualKcal = workout.activeEnergyKcal?.roundToInt(),
                )
            }
            updated.session(session.id)?.let { matched += it }
        }

        KBLog.sync.info(
            "pending=${pending.size} workouts=${workouts.size} matched=${matched.size}",
            TAG,
        )
        return Result(updated, matched)
    }

    /**
     * Tra gli allenamenti dello stesso giorno vince quello abbastanza lungo e, a
     * parità, quello con la disciplina più vicina al tipo di seduta.
     */
    private fun bestMatch(
        session: FitnessSession,
        workouts: List<HealthWorkoutEntry>,
    ): HealthWorkoutEntry? {
        val required = maxOf(
            minimumMinutes,
            (session.durationMinutes * minimumDurationRatio).roundToInt(),
        )
        val eligible = workouts.filter { (it.durationMinutes ?: 0) >= required }
        if (eligible.isEmpty()) return null
        return eligible.firstOrNull { matchesDiscipline(it, session) }
            ?: eligible.maxByOrNull { it.durationMinutes ?: 0 }
    }

    /**
     * Corrispondenza grossolana tra il titolo Health Connect ("Corsa") e il tipo
     * di seduta prodotto dall'AI ("corsa", "forza", …).
     */
    private fun matchesDiscipline(workout: HealthWorkoutEntry, session: FitnessSession): Boolean {
        val workoutTitle = workout.title.lowercase()
        val sessionText = "${session.activityType} ${session.title}".lowercase()
        val families = listOf(
            listOf("cors", "run", "jog"),
            listOf("camm", "walk", "escursion"),
            listOf("forza", "pesi", "strength", "funzional", "tonific"),
            listOf("hiit", "intervall", "circuit"),
            listOf("bici", "cicl", "cycl", "spinning"),
            listOf("nuot", "swim"),
            listOf("yoga", "pilates", "stretch", "mobil", "flessib"),
            listOf("remo", "canott", "rowing"),
            listOf("danza", "dance", "ballo"),
        )
        return families.any { keys ->
            keys.any { workoutTitle.contains(it) } && keys.any { sessionText.contains(it) }
        }
    }

    private companion object {
        const val TAG = "FitnessHealthSync"
    }
}
