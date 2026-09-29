package it.vittorioscocca.kidbox.data.health.fitness

/**
 * Report di fine settimana: il conteggio è locale e non costa messaggi AI.
 * L'AI entra in gioco solo se l'utente chiede la proposta di adeguamento.
 */
object FitnessWeeklyReportBuilder {

    fun report(weekIndex: Int, plan: FitnessPlanDocument): FitnessWeeklyReport? {
        val week = plan.weeks.firstOrNull { it.index == weekIndex } ?: return null
        val trackable = week.sessions.filterNot { it.isRest }
        val done = trackable.filter { it.status == FitnessSessionStatus.DONE }
        val skipped = trackable.filter { it.status == FitnessSessionStatus.SKIPPED }

        return FitnessWeeklyReport(
            weekIndex = weekIndex,
            weekStartEpochMillis = FitnessPlanDates.plusDays(
                plan.startDateEpochMillis,
                (weekIndex - 1) * 7,
            ),
            plannedSessions = trackable.size,
            completedSessions = done.size,
            skippedSessions = skipped.size,
            totalMinutes = done.sumOf { it.actualMinutes ?: it.durationMinutes },
            totalKcal = done.sumOf { it.actualKcal ?: it.targetKcal ?: 0 },
            totalDistanceMeters = done.sumOf { it.actualDistanceMeters ?: 0.0 },
            substitutedSessions = done.count { it.wasSubstituted },
            chronicallySkippedWeekdays = chronicallySkippedWeekdays(plan, weekIndex),
        )
    }

    /**
     * L'ultima settimana **conclusa** del piano, cioè quella il cui ultimo
     * giorno è già passato. È il report che la dashboard propone il lunedì.
     */
    fun lastCompletedWeekIndex(plan: FitnessPlanDocument): Int? {
        val today = FitnessPlanDates.today()
        return plan.weeks
            .map { it.index }
            .filter { index ->
                FitnessPlanDates.plusDays(plan.startDateEpochMillis, index * 7 - 1) < today
            }
            .maxOrNull()
    }

    /**
     * Consuntivo dell'intero piano: le settimane sommate, più quello che solo
     * il mese intero mostra (andamento per settimana, attività fuori programma).
     */
    fun recap(plan: FitnessPlanDocument): FitnessPlanRecap {
        val weekly = plan.weeks.map { it.index }.sorted().mapNotNull { report(it, plan) }
        val lastWeek = weekly.lastOrNull()?.weekIndex ?: FitnessPlanPromptBuilder.PLAN_WEEKS
        return FitnessPlanRecap(
            startDateEpochMillis = FitnessPlanDates.startOfDay(plan.startDateEpochMillis),
            endDateEpochMillis = plan.lastDayEpochMillis,
            goal = plan.input.goal,
            plannedSessions = weekly.sumOf { it.plannedSessions },
            completedSessions = weekly.sumOf { it.completedSessions },
            skippedSessions = weekly.sumOf { it.skippedSessions },
            substitutedSessions = weekly.sumOf { it.substitutedSessions },
            totalMinutes = weekly.sumOf { it.totalMinutes },
            totalKcal = weekly.sumOf { it.totalKcal },
            totalDistanceMeters = weekly.sumOf { it.totalDistanceMeters },
            weeklyCompletionPercents = weekly.map { it.completionPercent },
            chronicallySkippedWeekdays = chronicallySkippedWeekdays(plan, lastWeek),
            extraWorkouts = plan.loggedWorkouts.size,
        )
    }

    /**
     * Giorni della settimana saltati almeno due volte: sono il segnale che l'AI
     * usa per proporre di spostare quella seduta.
     */
    private fun chronicallySkippedWeekdays(plan: FitnessPlanDocument, upToWeekIndex: Int): List<Int> {
        val counts = mutableMapOf<Int, Int>()
        plan.allSessions
            .filter { it.weekIndex <= upToWeekIndex && !it.isRest }
            .filter {
                it.status == FitnessSessionStatus.SKIPPED || it.status == FitnessSessionStatus.MOVED
            }
            .forEach { session ->
                val weekday = FitnessPlanDates.weekdayOf(
                    session.originalDateEpochMillis ?: session.dateEpochMillis,
                )
                counts[weekday] = (counts[weekday] ?: 0) + 1
            }
        return counts.filterValues { it >= 2 }.keys.sorted()
    }
}
