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
        /** Allenamenti svolti che non corrispondono a nessuna seduta prevista. */
        val loggedWorkouts: List<FitnessLoggedWorkout> = emptyList(),
        /** Sedute riaperte perché chiuse da un'attività di un'altra disciplina. */
        val repairedSessions: Int = 0,
        /** Attività già registrate a cui è stata aggiunta la distanza letta ora. */
        val enrichedSessions: Int = 0,
    ) {
        val didChange: Boolean
            get() = matchedSessions.isNotEmpty() || loggedWorkouts.isNotEmpty() ||
                repairedSessions > 0 || enrichedSessions > 0
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

        // Si rilegge dall'inizio del piano, non dalla prima seduta aperta: serve
        // anche a ricontrollare le sedute già chiuse (vedi la riparazione qui
        // sotto) e a registrare le attività dei giorni senza nulla in programma.
        val workouts = healthConnect.workoutsSince(
            FitnessPlanDates.startOfDay(plan.startDateEpochMillis),
        )
        if (workouts.isEmpty()) return Result(plan, emptyList())

        var updated = plan
        var repaired = 0

        // Riparazione dei dati lasciati dalla vecchia euristica, che in mancanza
        // di corrispondenza chiudeva una seduta con l'allenamento più lungo del
        // giorno: una corsa poteva risultare "bici svolta". Quelle sedute vanno
        // riaperte, altrimenti il calendario continua a dichiarare un
        // allenamento mai fatto e l'attività vera resta invisibile.
        //
        // Si guardano SOLO le sedute senza `actualActivityTitle`: è la firma
        // della vecchia versione, che quel campo non lo scriveva. Tutto ciò che
        // ha un nome di attività è stato deciso dopo — dal matcher per
        // disciplina, o dalla persona che ha attribuito a mano un allenamento a
        // una seduta di un'altra disciplina ("conta la corsa di oggi come la
        // seduta di bici"). Senza questo filtro la riparazione scambiava quella
        // scelta esplicita per un errore da annullare e la disfaceva al primo
        // giro di sincronizzazione.
        plan.allSessions
            .filter {
                it.status == FitnessSessionStatus.DONE &&
                    it.completionSource == FitnessCompletionSource.HEALTH_CONNECT &&
                    it.actualActivityTitle == null
            }
            .forEach { session ->
                val workout = workouts.firstOrNull { it.id == session.matchedWorkoutId }
                    ?: return@forEach
                if (matchesDiscipline(workout, session)) {
                    updated = updated.updateSession(session.id) {
                        it.copy(actualActivityTitle = workout.title)
                    }
                } else {
                    updated = updated.updateSession(session.id) {
                        it.copy(
                            status = FitnessSessionStatus.PLANNED,
                            completedAtEpochMillis = null,
                            completionSource = null,
                            matchedWorkoutId = null,
                            actualActivityTitle = null,
                            actualMinutes = null,
                            actualKcal = null,
                            actualHeartRateBpm = null,
                            actualDistanceMeters = null,
                        )
                    }
                    repaired++
                }
            }

        // Residui di abbinamenti su sedute non chiuse: campi rimasti da versioni
        // che riaprivano la seduta senza azzerarli. Falsano i minuti del
        // consuntivo e impediscono di riusare quell'attività.
        updated.allSessions
            .filter { it.status != FitnessSessionStatus.DONE }
            .filter {
                it.matchedWorkoutId != null || it.actualMinutes != null ||
                    it.actualKcal != null || it.actualActivityTitle != null ||
                    it.actualHeartRateBpm != null || it.actualDistanceMeters != null
            }
            .forEach { session ->
                updated = updated.updateSession(session.id) {
                    it.copy(
                        matchedWorkoutId = null,
                        actualMinutes = null,
                        actualKcal = null,
                        actualHeartRateBpm = null,
                        actualDistanceMeters = null,
                        actualActivityTitle = null,
                        completedAtEpochMillis = null,
                        completionSource = null,
                    )
                }
            }

        // Chi usava l'app prima che leggessimo le distanze ha sedute chiuse e
        // attività registrate senza chilometri: l'allenamento è lo stesso, il
        // dato c'era già in Health Connect, mancava solo a noi. Si recupera qui
        // invece di lasciare buchi permanenti nello storico.
        var enriched = 0
        updated.allSessions
            .filter { it.status == FitnessSessionStatus.DONE && it.actualDistanceMeters == null }
            .forEach { session ->
                val meters = workouts
                    .firstOrNull { it.id == session.matchedWorkoutId }
                    ?.distanceMeters
                    ?: return@forEach
                updated = updated.updateSession(session.id) {
                    it.copy(actualDistanceMeters = meters)
                }
                enriched++
            }

        if (updated.loggedWorkouts.isNotEmpty()) {
            val backfilled = updated.loggedWorkouts.map { entry ->
                if (entry.distanceMeters != null) return@map entry
                val meters = workouts.firstOrNull { it.id == entry.id }?.distanceMeters
                    ?: return@map entry
                enriched++
                entry.copy(distanceMeters = meters)
            }
            if (backfilled != updated.loggedWorkouts) {
                updated = updated.copy(loggedWorkouts = backfilled)
            }
        }

        // Le sedute da valutare si leggono dal piano già riparato: una riaperta
        // qui sopra può essere richiusa subito dall'allenamento giusto.
        val pending = updated.allSessions.filter {
            !it.isRest && it.status == FitnessSessionStatus.PLANNED && it.dateEpochMillis <= today
        }

        // Un allenamento chiude al massimo una seduta: senza questo insieme una
        // corsa lunga chiuderebbe tutte le sedute arretrate dello stesso giorno.
        val usedWorkoutIds = updated.allSessions.mapNotNull { it.matchedWorkoutId }.toMutableSet()
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
                    actualActivityTitle = workout.title,
                    actualMinutes = workout.durationMinutes,
                    actualKcal = workout.activeEnergyKcal?.roundToInt(),
                    actualHeartRateBpm = workout.averageHeartRateBpm?.roundToInt(),
                    actualDistanceMeters = workout.distanceMeters,
                )
            }
            updated.session(session.id)?.let { matched += it }
        }

        // Quello che resta è attività svolta che il programma non prevedeva: va
        // mostrata per quella che è, non spacciata per una seduta pianificata.
        val alreadyLogged = updated.loggedWorkouts.map { it.id }.toSet()
        val newlyLogged = workouts
            .filter { it.id !in usedWorkoutIds && it.id !in alreadyLogged }
            .map { workout ->
                FitnessLoggedWorkout(
                    id = workout.id,
                    dateEpochMillis = workout.startedAtEpochMillis,
                    title = workout.title,
                    durationMinutes = workout.durationMinutes,
                    kcal = workout.activeEnergyKcal?.roundToInt(),
                    heartRateBpm = workout.averageHeartRateBpm?.roundToInt(),
                    distanceMeters = workout.distanceMeters,
                )
            }
        if (newlyLogged.isNotEmpty()) {
            updated = updated.copy(loggedWorkouts = updated.loggedWorkouts + newlyLogged)
        }

        KBLog.sync.info(
            "pending=${pending.size} workouts=${workouts.size} matched=${matched.size} " +
                "logged=${newlyLogged.size} repaired=$repaired enriched=$enriched",
            TAG,
        )
        return Result(updated, matched, newlyLogged, repaired, enriched)
    }

    /**
     * Chiude la seduta solo un allenamento abbastanza lungo **e della stessa
     * disciplina**.
     *
     * Prima, senza corrispondenza, si ripiegava sull'allenamento più lungo della
     * giornata: una corsa chiudeva così sia la seduta di bici sia quella di
     * corpo libero previste quel giorno, dichiarando svolto un allenamento mai
     * fatto. Meglio lasciare la seduta aperta e mostrare a parte ciò che è stato
     * fatto: a decidere se una cosa sostituisce l'altra è la persona.
     */
    private fun bestMatch(
        session: FitnessSession,
        workouts: List<HealthWorkoutEntry>,
    ): HealthWorkoutEntry? {
        val required = maxOf(
            minimumMinutes,
            (session.durationMinutes * minimumDurationRatio).roundToInt(),
        )
        return workouts
            .filter { (it.durationMinutes ?: 0) >= required }
            .firstOrNull { matchesDiscipline(it, session) }
    }

    private fun matchesDiscipline(workout: HealthWorkoutEntry, session: FitnessSession): Boolean =
        FitnessDisciplineMatcher.matches(
            activityTitle = workout.title,
            sessionText = "${session.activityType} ${session.title}",
        )

    private companion object {
        const val TAG = "FitnessHealthSync"
    }
}
