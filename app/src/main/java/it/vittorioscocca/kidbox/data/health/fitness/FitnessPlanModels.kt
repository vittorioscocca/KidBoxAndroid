package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import java.util.Calendar

/**
 * Modelli del Piano Fitness AI: input dell'onboarding, piano mensile
 * strutturato (4 settimane) e stato di completamento delle sedute.
 *
 * A differenza del Piano Alimentare — che è un testo generato una volta e
 * letto — il piano fitness è un calendario vivo: le sedute cambiano stato
 * (fatta, saltata, spostata) e vengono riconciliate con gli allenamenti letti
 * da Health Connect. Per questo l'AI restituisce JSON, non prosa.
 */

/** Obiettivo principale scelto nel wizard. */
enum class FitnessGoal(val labelRes: Int, val promptLabel: String) {
    WEIGHT_LOSS(
        R.string.fitness_goal_weight_loss,
        "perdere peso preservando la massa muscolare",
    ),
    TONING(
        R.string.fitness_goal_toning,
        "tonificazione generale e salute, senza obiettivi di peso",
    ),
    RACE(
        R.string.fitness_goal_race,
        "preparazione a una gara o a un evento sportivo",
    ),
}

/**
 * Disciplina sportiva: serve sia come attività da mettere nel piano (per
 * qualsiasi obiettivo, anche solo tonicità e salute) sia come gara di
 * riferimento quando l'obiettivo è [FitnessGoal.RACE].
 */
enum class FitnessSport(
    val labelRes: Int,
    /** Come attività da inserire nelle sedute. */
    val promptLabel: String,
    /** Come gara o evento di riferimento. */
    val racePromptLabel: String,
) {
    RUNNING(R.string.fitness_sport_running, "corsa", "gara di corsa su 5-10 km"),
    WALKING(R.string.fitness_sport_walking, "camminata veloce o trekking", "marcia o trekking di lunga distanza"),
    MARATHON(R.string.fitness_sport_marathon, "corsa di lunga distanza", "maratona o mezza maratona"),
    TRAIL(R.string.fitness_sport_trail, "trail o corsa in montagna", "trail o corsa in montagna"),
    CYCLING(R.string.fitness_sport_cycling, "bicicletta", "granfondo o gara di ciclismo"),
    SWIMMING(R.string.fitness_sport_swimming, "nuoto", "gara di nuoto"),
    TRIATHLON(R.string.fitness_sport_triathlon, "triathlon (nuoto, bici, corsa)", "triathlon"),
    GYM(R.string.fitness_sport_gym, "palestra con pesi e macchine", "gara di sollevamento pesi"),
    BODYWEIGHT(R.string.fitness_sport_bodyweight, "allenamento a corpo libero", "gara di calisthenics"),
    FUNCTIONAL(R.string.fitness_sport_functional, "allenamento funzionale o HIIT", "gara di cross training"),
    YOGA(R.string.fitness_sport_yoga, "yoga o pilates", "evento o stage intensivo"),
    TENNIS(R.string.fitness_sport_tennis, "tennis o padel", "torneo di tennis o padel"),
    FOOTBALL(R.string.fitness_sport_football, "calcio", "campionato o torneo di calcio"),
    VOLLEYBALL(R.string.fitness_sport_volleyball, "pallavolo", "campionato o torneo di volley"),
    BASKETBALL(R.string.fitness_sport_basketball, "pallacanestro", "campionato o torneo di basket"),
    MARTIAL_ARTS(R.string.fitness_sport_martial_arts, "arti marziali o boxe", "incontro di arti marziali o boxe"),
    DANCE(R.string.fitness_sport_dance, "danza o ballo", "gara o saggio di danza"),
    CLIMBING(R.string.fitness_sport_climbing, "arrampicata", "gara di arrampicata"),
    ROWING(R.string.fitness_sport_rowing, "canottaggio o vogatore", "gara di canottaggio"),
    SKIING(R.string.fitness_sport_skiing, "sci o sport invernali", "gara di sci o sport invernali"),
    OTHER(R.string.fitness_sport_other, "altra attività indicata dall'utente", "evento sportivo");

    companion object {
        /**
         * Discipline proponibili come gara: yoga e camminata restano fra gli
         * sport praticabili, ma non hanno senso come evento di riferimento.
         */
        val raceOptions: List<FitnessSport>
            get() = entries.filter { it != YOGA && it != WALKING }
    }
}

/** Esperienza dichiarata: determina volume e progressione del piano. */
enum class FitnessExperience(val labelRes: Int, val promptLabel: String) {
    BEGINNER(
        R.string.fitness_experience_beginner,
        "principiante (riparte da zero o si allena da meno di 3 mesi)",
    ),
    INTERMEDIATE(
        R.string.fitness_experience_intermediate,
        "intermedio (si allena con continuità da almeno 6 mesi)",
    ),
    ADVANCED(
        R.string.fitness_experience_advanced,
        "avanzato (allenamento strutturato da anni)",
    ),
}

/** Dove ci si allena: cambia completamente gli esercizi proposti. */
enum class FitnessPlace(val labelRes: Int, val promptLabel: String) {
    HOME(
        R.string.fitness_place_home,
        "a casa, senza attrezzatura o con manubri leggeri ed elastici",
    ),
    GYM(
        R.string.fitness_place_gym,
        "in palestra, con accesso a bilancieri, macchine e cardio",
    ),
    OUTDOOR(
        R.string.fitness_place_outdoor,
        "all'aperto, corsa e corpo libero",
    ),
}

/**
 * Parametri raccolti nell'onboarding, prima di invocare l'AI.
 *
 * Età, peso e altezza sono chiesti solo quando Health Connect non li fornisce,
 * come nel Piano Alimentare.
 */
data class FitnessPlanInput(
    val goal: FitnessGoal = FitnessGoal.TONING,
    /**
     * Sport che la persona vuole praticare, per QUALSIASI obiettivo: sono le
     * attività attorno a cui l'AI costruisce le sedute, non solo un dettaglio
     * della preparazione a una gara.
     */
    val preferredSports: Set<FitnessSport> = emptySet(),
    val raceType: FitnessSport? = null,
    /**
     * Descrizione libera della gara: usata quando [raceType] è
     * [FitnessSport.OTHER], oppure per precisare distanza e livello.
     */
    val raceDetail: String = "",
    val raceDateEpochMillis: Long? = null,
    /** Giorni disponibili, in convenzione [Calendar] (1 = domenica … 7 = sabato). */
    val trainingWeekdays: Set<Int> = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
    /** Orario del promemoria, come minuti dalla mezzanotte. */
    val reminderMinutesFromMidnight: Int = 18 * 60,
    val reminderEnabled: Boolean = true,
    val sessionMinutes: Int = 45,
    val experience: FitnessExperience = FitnessExperience.BEGINNER,
    val place: FitnessPlace = FitnessPlace.HOME,
    val notes: String = "",
    val manualAgeYears: String = "",
    val manualWeightKg: String = "",
    val manualHeightCm: String = "",
) {
    val manualAgeValue: Int? get() = number(manualAgeYears)?.toInt()?.takeIf { it in 1..119 }
    val manualWeightValue: Double? get() = number(manualWeightKg)?.takeIf { it in 2.0..400.0 }
    val manualHeightValue: Double? get() = number(manualHeightCm)?.takeIf { it in 40.0..260.0 }

    val reminderHour: Int get() = reminderMinutesFromMidnight / 60
    val reminderMinute: Int get() = reminderMinutesFromMidnight % 60

    /**
     * Sport preferiti nell'ordine dell'enum: il prompt deve essere stabile fra
     * due generazioni identiche, e `Set` non lo è.
     */
    val sortedSports: List<FitnessSport>
        get() = FitnessSport.entries.filter { it in preferredSports }

    /** Giorni ordinati a partire dal primo giorno della settimana locale. */
    val sortedWeekdays: List<Int>
        get() {
            val first = Calendar.getInstance().firstDayOfWeek
            return trainingWeekdays.sortedBy { (it - first + 7) % 7 }
        }

    /** Il wizard è completo? Serve almeno un giorno, e la gara va qualificata. */
    val isComplete: Boolean
        get() {
            if (trainingWeekdays.isEmpty()) return false
            if (goal == FitnessGoal.RACE) {
                val type = raceType ?: return false
                if (type == FitnessSport.OTHER && raceDetail.isBlank()) return false
            }
            return true
        }

    /** Numero inserito a mano, accettando sia la virgola sia il punto decimale. */
    private fun number(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
}

enum class FitnessSessionStatus(val labelRes: Int) {
    PLANNED(R.string.fitness_status_planned),
    DONE(R.string.fitness_status_done),
    SKIPPED(R.string.fitness_status_skipped),
    MOVED(R.string.fitness_status_moved),
}

/**
 * Come una seduta è stata segnata completata: serve al report settimanale per
 * distinguere l'autodichiarazione dal dato letto dall'orologio.
 */
enum class FitnessCompletionSource {
    MANUAL,
    NOTIFICATION,
    HEALTH_CONNECT,
}

data class FitnessExercise(
    val name: String,
    /** "3 serie × 12 ripetizioni", "20 minuti a ritmo facile", … */
    val detail: String = "",
    val notes: String? = null,
)

/** Una singola giornata di allenamento del piano. */
data class FitnessSession(
    val id: String,
    /** Data prevista (mezzanotte locale). Cambia quando la seduta viene spostata. */
    val dateEpochMillis: Long,
    /** Data originale, conservata quando la seduta viene spostata. */
    val originalDateEpochMillis: Long? = null,
    val weekIndex: Int,
    val title: String,
    /**
     * Categoria libera prodotta dall'AI ("corsa", "forza", "mobilità", …):
     * usata solo per scegliere l'icona e per il match con Health Connect.
     */
    val activityType: String,
    val durationMinutes: Int,
    val intensity: String = "",
    val exercises: List<FitnessExercise> = emptyList(),
    /** Obiettivi misurabili della giornata (minuti, kcal, serie/ripetizioni). */
    val targets: List<String> = emptyList(),
    val targetKcal: Int? = null,
    val notes: String? = null,
    val status: FitnessSessionStatus = FitnessSessionStatus.PLANNED,
    val completedAtEpochMillis: Long? = null,
    val completionSource: FitnessCompletionSource? = null,
    /** Id dell'allenamento Health Connect che ha chiuso la seduta (anti doppio conteggio). */
    val matchedWorkoutId: String? = null,
    val actualMinutes: Int? = null,
    val actualKcal: Int? = null,
) {
    val isRest: Boolean
        get() = activityType.lowercase().let { type ->
            type.contains("ripos") || type.contains("rest") || type.contains("recupero")
        }
}

/** Una settimana del piano mensile. */
data class FitnessWeek(
    val index: Int,
    val focus: String,
    val sessions: List<FitnessSession>,
)

/** Piano mensile generato dall'AI. */
data class FitnessPlanDocument(
    val subjectName: String,
    val input: FitnessPlanInput,
    /** Primo giorno del piano (mezzanotte locale). */
    val startDateEpochMillis: Long,
    val summary: String,
    /** Adattamenti dovuti a referti, patologie o terapie: mostrati in evidenza. */
    val safetyNotes: List<String>,
    val weeks: List<FitnessWeek>,
    val generatedAtEpochMillis: Long,
    val messageUnitsConsumed: Int,
) {
    val allSessions: List<FitnessSession>
        get() = weeks.flatMap { it.sessions }.sortedBy { it.dateEpochMillis }

    fun session(id: String): FitnessSession? =
        weeks.firstNotNullOfOrNull { week -> week.sessions.firstOrNull { it.id == id } }

    /** Restituisce una copia del piano con una seduta trasformata. */
    fun updateSession(id: String, transform: (FitnessSession) -> FitnessSession): FitnessPlanDocument =
        copy(
            weeks = weeks.map { week ->
                if (week.sessions.none { it.id == id }) {
                    week
                } else {
                    week.copy(
                        sessions = week.sessions
                            .map { if (it.id == id) transform(it) else it }
                            .sortedBy { session -> session.dateEpochMillis },
                    )
                }
            },
        )

    /** Sedute di una giornata specifica. */
    fun sessionsOn(dayEpochMillis: Long): List<FitnessSession> {
        val day = FitnessPlanDates.startOfDay(dayEpochMillis)
        return allSessions.filter { FitnessPlanDates.startOfDay(it.dateEpochMillis) == day }
    }

    /** Settimana che contiene la data indicata (1-based), `null` se fuori piano. */
    fun weekIndexFor(dayEpochMillis: Long): Int? {
        val days = FitnessPlanDates.daysBetween(startDateEpochMillis, dayEpochMillis)
        if (days < 0) return null
        val index = days / 7 + 1
        return if (weeks.any { it.index == index }) index else null
    }
}

/** Sintesi di fine settimana mostrata all'utente. */
data class FitnessWeeklyReport(
    val weekIndex: Int,
    val weekStartEpochMillis: Long,
    val plannedSessions: Int,
    val completedSessions: Int,
    val skippedSessions: Int,
    val totalMinutes: Int,
    val totalKcal: Int,
    /** Giorni della settimana (convenzione [Calendar]) sistematicamente saltati. */
    val chronicallySkippedWeekdays: List<Int>,
) {
    val completionRate: Float
        get() = if (plannedSessions <= 0) 0f else completedSessions.toFloat() / plannedSessions

    val completionPercent: Int get() = Math.round(completionRate * 100)

    /** Risorsa del messaggio motivazionale: la scelta è locale, non costa AI. */
    val headlineRes: Int
        get() = when (completionPercent) {
            100 -> R.string.fitness_report_headline_perfect
            in 70..99 -> R.string.fitness_report_headline_good
            in 40..69 -> R.string.fitness_report_headline_mid
            else -> R.string.fitness_report_headline_low
        }
}

/** Proposta di adeguamento generata dall'AI a fine settimana. */
data class FitnessAdjustmentProposal(
    val rationale: String,
    val changes: List<String>,
    /** Sedute riscritte da applicare (stessi id di quelle esistenti). */
    val updatedSessions: List<FitnessSession>,
    val weekIndex: Int,
)

/** Contatore messaggi AI dopo una generazione (allineato a askAI / AIAskAIPayload). */
data class FitnessPlanAIUsageInfo(
    val messageUnitsConsumed: Int,
    val usageToday: Int,
    val dailyLimit: Int,
    val totalPayloadChars: Int,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
)

/** Errori del piano fitness mappati su stringhe localizzate dalla UI. */
sealed class FitnessPlanError : Exception() {
    data object PlanNotIncluded : FitnessPlanError()
    data class QuotaWouldExceed(val needed: Int, val remaining: Int, val dailyLimit: Int) : FitnessPlanError()
    data class PayloadTooLarge(val chars: Int, val maxChars: Int) : FitnessPlanError()
    data object MissingHealthData : FitnessPlanError()
    data object IncompleteSetup : FitnessPlanError()
    data object InvalidPlanFormat : FitnessPlanError()
}

/** Aritmetica sui giorni condivisa da piano, calendario e riconciliazione. */
object FitnessPlanDates {

    const val DAY_MILLIS = 86_400_000L

    fun startOfDay(epochMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun today(): Long = startOfDay(System.currentTimeMillis())

    /** Giorni di calendario fra due istanti, ignorando l'ora legale. */
    fun daysBetween(fromEpochMillis: Long, toEpochMillis: Long): Int {
        val from = java.time.Instant.ofEpochMilli(startOfDay(fromEpochMillis))
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val to = java.time.Instant.ofEpochMilli(startOfDay(toEpochMillis))
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt()
    }

    fun plusDays(epochMillis: Long, days: Int): Long {
        val date = java.time.Instant.ofEpochMilli(startOfDay(epochMillis))
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(days.toLong())
        return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun weekdayOf(epochMillis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = epochMillis
    }.get(Calendar.DAY_OF_WEEK)
}
