package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordHealthContextBuilder
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.remote.ai.AIUsageTracker
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.util.KBLog
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Generazione del Piano Fitness via Cloud Function `askAI`:
 * - `purpose: "fitnessPlan"` per il piano mensile (JSON, max_tokens esteso);
 * - `purpose: "fitnessAdjust"` per lo spostamento di una seduta e per la
 *   proposta di adeguamento settimanale (payload piccolo, 1 messaggio).
 */
object FitnessPlanGenerator {

    private const val TAG = "FitnessPlanGenerator"

    /** Overhead delle regole aggiunte lato server (`FITNESS_PLAN_SYSTEM_RULES` in index.js). */
    private const val SERVER_RULES_OVERHEAD = 900

    data class Estimate(val totalChars: Int, val messageUnits: Int)

    data class Payload(
        val systemPrompt: String,
        val userContent: String,
        val profileSummary: List<String>,
        val healthContext: String,
        val startDateEpochMillis: Long,
        val hasWeight: Boolean,
        val hasHeight: Boolean,
    )

    fun buildPayload(
        subjectName: String,
        birthDateEpochMillis: Long?,
        input: FitnessPlanInput,
        snapshot: HealthImportSnapshot?,
        profile: KBPediatricProfile?,
        treatments: List<KBTreatmentEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        startDateEpochMillis: Long = FitnessPlanPromptBuilder.planStartDate(),
    ): Payload {
        val healthContext = ClinicalRecordHealthContextBuilder.buildClinicalPrompt(
            subjectName = subjectName,
            treatments = treatments,
            visits = visits,
            exams = exams,
            health = snapshot,
            healthLabel = "Health Connect",
            birthDateEpochMillis = birthDateEpochMillis ?: snapshot?.birthDateEpochMillis,
            refertoMaxChars = FitnessPlanPromptBuilder.REFERTO_MAX_CHARS,
        )
        val profileSummary = FitnessPlanPromptBuilder.profileSummaryLines(
            birthDateEpochMillis = birthDateEpochMillis,
            snapshot = snapshot,
            profile = profile,
            input = input,
        )
        return Payload(
            systemPrompt = FitnessPlanPromptBuilder.systemPrompt(
                FitnessPlanPromptBuilder.responseLanguageName(),
            ),
            userContent = FitnessPlanPromptBuilder.userContent(
                subjectName = subjectName,
                input = input,
                startDateEpochMillis = startDateEpochMillis,
                allowedDayOffsets = FitnessPlanPromptBuilder.allowedDayOffsets(
                    input,
                    startDateEpochMillis,
                ),
                profileSummary = profileSummary,
                healthContext = healthContext,
            ),
            profileSummary = profileSummary,
            healthContext = healthContext,
            startDateEpochMillis = startDateEpochMillis,
            hasWeight = snapshot?.weightKg != null || input.manualWeightValue != null,
            hasHeight = snapshot?.heightCm != null || input.manualHeightValue != null,
        )
    }

    fun estimate(payload: Payload): Estimate {
        val total = AIAskAIPayload.totalChars(
            payload.systemPrompt,
            listOf(aiUserMessage(payload.userContent)),
        ) + SERVER_RULES_OVERHEAD
        return Estimate(total, AIAskAIPayload.fitnessPlanMessageUnits(total))
    }

    data class Result(val document: FitnessPlanDocument, val usage: FitnessPlanAIUsageInfo)

    suspend fun generate(
        aiRepository: AiRepository,
        usageTracker: AIUsageTracker,
        familyId: String,
        subjectName: String,
        input: FitnessPlanInput,
        payload: Payload,
    ): Result {
        // Feature dei soli piani a pagamento: il gate vero è lato server, questo
        // evita di bruciare una chiamata e dà subito il messaggio giusto.
        if (CurrentPlanStore.plan.value == KBPlan.FREE) throw FitnessPlanError.PlanNotIncluded
        if (!input.isComplete) throw FitnessPlanError.IncompleteSetup
        if (!payload.hasWeight || !payload.hasHeight) throw FitnessPlanError.MissingHealthData

        val estimate = estimate(payload)
        if (estimate.totalChars > AIAskAIPayload.ABSOLUTE_MAX_CHARS) {
            throw FitnessPlanError.PayloadTooLarge(estimate.totalChars, AIAskAIPayload.ABSOLUTE_MAX_CHARS)
        }
        assertQuota(usageTracker, estimate.messageUnits)

        KBLog.ai.info(
            "request chars=${estimate.totalChars} units=${estimate.messageUnits}",
            TAG,
        )

        val reply = aiRepository.askAI(
            familyId = familyId,
            systemPrompt = payload.systemPrompt,
            messages = listOf(aiUserMessage(payload.userContent)),
            purpose = PURPOSE_PLAN,
        ).getOrElse { throw it }

        val document = FitnessPlanParser.parsePlan(
            raw = reply.reply,
            subjectName = subjectName,
            input = input,
            startDateEpochMillis = payload.startDateEpochMillis,
            messageUnitsConsumed = reply.messageUnitsConsumed,
        )
        KBLog.ai.info(
            "done weeks=${document.weeks.size} sessions=${document.allSessions.size}",
            TAG,
        )
        return Result(
            document = document,
            usage = FitnessPlanAIUsageInfo(
                messageUnitsConsumed = reply.messageUnitsConsumed,
                usageToday = reply.usageToday,
                dailyLimit = reply.dailyLimit,
                totalPayloadChars = estimate.totalChars,
                period = reply.period,
            ),
        )
    }

    data class RescheduleOutcome(
        val plan: FitnessPlanDocument,
        val rationale: String,
        val usage: FitnessPlanAIUsageInfo,
    )

    /**
     * Riorganizza i giorni rimanenti della settimana dopo uno spostamento.
     *
     * Costa poco perché il payload non contiene il contesto clinico completo:
     * vanno solo le sedute della settimana e le note di sicurezza già calcolate
     * alla generazione del piano.
     */
    suspend fun reschedule(
        aiRepository: AiRepository,
        usageTracker: AIUsageTracker,
        familyId: String,
        plan: FitnessPlanDocument,
        sessionId: String,
        newDateEpochMillis: Long,
    ): RescheduleOutcome {
        if (CurrentPlanStore.plan.value == KBPlan.FREE) throw FitnessPlanError.PlanNotIncluded
        val session = plan.session(sessionId) ?: throw FitnessPlanError.InvalidPlanFormat

        val systemPrompt = FitnessPlanPromptBuilder.rescheduleSystemPrompt(
            FitnessPlanPromptBuilder.responseLanguageName(),
        )
        val userContent = rescheduleUserContent(plan, session, newDateEpochMillis, session.weekIndex)
        assertQuota(
            usageTracker,
            AIAskAIPayload.messageUnits(
                AIAskAIPayload.totalChars(systemPrompt, listOf(aiUserMessage(userContent))),
            ),
        )

        val reply = aiRepository.askAI(
            familyId = familyId,
            systemPrompt = systemPrompt,
            messages = listOf(aiUserMessage(userContent)),
            purpose = PURPOSE_ADJUST,
        ).getOrElse { throw it }

        val updates = FitnessPlanParser.parseSessionUpdates(
            raw = reply.reply,
            startDateEpochMillis = plan.startDateEpochMillis,
            fallbackWeekIndex = session.weekIndex,
        )

        // Lo spostamento vero lo applica il client: l'AI riorganizza il resto,
        // ma la data scelta dall'utente non è negoziabile.
        var updated = plan.updateSession(sessionId) { moved ->
            moved.copy(
                originalDateEpochMillis = moved.originalDateEpochMillis ?: moved.dateEpochMillis,
                dateEpochMillis = FitnessPlanDates.startOfDay(newDateEpochMillis),
                status = FitnessSessionStatus.PLANNED,
            )
        }
        updated = apply(updates.sessions, updated, skipping = setOf(sessionId))

        return RescheduleOutcome(
            plan = updated,
            rationale = updates.rationale,
            usage = FitnessPlanAIUsageInfo(
                messageUnitsConsumed = reply.messageUnitsConsumed,
                usageToday = reply.usageToday,
                dailyLimit = reply.dailyLimit,
                totalPayloadChars = 0,
                period = reply.period,
            ),
        )
    }

    data class AdjustmentOutcome(
        val proposal: FitnessAdjustmentProposal,
        val usage: FitnessPlanAIUsageInfo,
    )

    suspend fun weeklyAdjustment(
        aiRepository: AiRepository,
        usageTracker: AIUsageTracker,
        familyId: String,
        plan: FitnessPlanDocument,
        report: FitnessWeeklyReport,
    ): AdjustmentOutcome {
        if (CurrentPlanStore.plan.value == KBPlan.FREE) throw FitnessPlanError.PlanNotIncluded
        val nextWeekIndex = report.weekIndex + 1
        if (plan.weeks.none { it.index == nextWeekIndex }) throw FitnessPlanError.InvalidPlanFormat

        val systemPrompt = FitnessPlanPromptBuilder.weeklyAdjustSystemPrompt(
            FitnessPlanPromptBuilder.responseLanguageName(),
        )
        val userContent = weeklyAdjustUserContent(plan, report, nextWeekIndex)
        assertQuota(
            usageTracker,
            AIAskAIPayload.messageUnits(
                AIAskAIPayload.totalChars(systemPrompt, listOf(aiUserMessage(userContent))),
            ),
        )

        val reply = aiRepository.askAI(
            familyId = familyId,
            systemPrompt = systemPrompt,
            messages = listOf(aiUserMessage(userContent)),
            purpose = PURPOSE_ADJUST,
        ).getOrElse { throw it }

        val updates = FitnessPlanParser.parseSessionUpdates(
            raw = reply.reply,
            startDateEpochMillis = plan.startDateEpochMillis,
            fallbackWeekIndex = nextWeekIndex,
        )
        return AdjustmentOutcome(
            proposal = FitnessAdjustmentProposal(
                rationale = updates.rationale,
                changes = updates.changes,
                updatedSessions = updates.sessions,
                weekIndex = nextWeekIndex,
            ),
            usage = FitnessPlanAIUsageInfo(
                messageUnitsConsumed = reply.messageUnitsConsumed,
                usageToday = reply.usageToday,
                dailyLimit = reply.dailyLimit,
                totalPayloadChars = 0,
                period = reply.period,
            ),
        )
    }

    /** Applica al piano le sedute riscritte da una proposta accettata. */
    fun apply(
        sessions: List<FitnessSession>,
        plan: FitnessPlanDocument,
        skipping: Set<String> = emptySet(),
    ): FitnessPlanDocument {
        var updated = plan
        sessions.filterNot { it.id in skipping }.forEach { incoming ->
            val existing = updated.session(incoming.id) ?: return@forEach
            // Una seduta già chiusa non si riscrive: il resoconto della settimana
            // deve restare quello che è successo davvero.
            if (existing.status == FitnessSessionStatus.DONE) return@forEach
            updated = updated.updateSession(incoming.id) { session ->
                val moved = FitnessPlanDates.startOfDay(session.dateEpochMillis) !=
                    FitnessPlanDates.startOfDay(incoming.dateEpochMillis)
                session.copy(
                    originalDateEpochMillis = if (moved) {
                        session.originalDateEpochMillis ?: session.dateEpochMillis
                    } else {
                        session.originalDateEpochMillis
                    },
                    dateEpochMillis = incoming.dateEpochMillis,
                    title = incoming.title,
                    activityType = incoming.activityType,
                    durationMinutes = incoming.durationMinutes,
                    intensity = incoming.intensity,
                    exercises = incoming.exercises,
                    targets = incoming.targets,
                    targetKcal = incoming.targetKcal,
                    notes = incoming.notes,
                    status = FitnessSessionStatus.PLANNED,
                )
            }
        }
        return updated
    }

    private fun assertQuota(usageTracker: AIUsageTracker, units: Int) {
        val snapshot = usageTracker.state.value
        if (snapshot.dailyLimit <= 0) return
        val remaining = (snapshot.dailyLimit - snapshot.usageToday).coerceAtLeast(0)
        if (units <= remaining) return
        throw FitnessPlanError.QuotaWouldExceed(
            needed = units,
            remaining = remaining,
            dailyLimit = snapshot.dailyLimit,
        )
    }

    private fun rescheduleUserContent(
        plan: FitnessPlanDocument,
        movedSession: FitnessSession,
        newDateEpochMillis: Long,
        weekIndex: Int,
    ): String = buildString {
        appendLine("L'utente ha spostato una seduta e serve riorganizzare la settimana $weekIndex.")
        appendLine("Obiettivo del piano: ${plan.input.goal.promptLabel}")
        appendLine(
            "Giorni disponibili: ${FitnessPlanPromptBuilder.weekdayNames(plan.input.sortedWeekdays)}",
        )
        appendLine("Inizio del piano (dayOffset 0): ${shortDate(plan.startDateEpochMillis)}")
        val newOffset = FitnessPlanDates.daysBetween(plan.startDateEpochMillis, newDateEpochMillis)
        appendLine(
            "Seduta spostata: \"${movedSession.title}\" da ${shortDate(movedSession.dateEpochMillis)} " +
                "a ${shortDate(newDateEpochMillis)} (dayOffset $newOffset)",
        )
        if (plan.safetyNotes.isNotEmpty()) {
            appendLine()
            appendLine("--- VINCOLI CLINICI GIÀ STABILITI (da rispettare) ---")
            plan.safetyNotes.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("--- SEDUTE DELLA SETTIMANA $weekIndex ---")
        sessionLines(
            plan.weeks.firstOrNull { it.index == weekIndex }?.sessions.orEmpty(),
            plan.startDateEpochMillis,
        ).forEach { appendLine(it) }
    }

    private fun weeklyAdjustUserContent(
        plan: FitnessPlanDocument,
        report: FitnessWeeklyReport,
        nextWeekIndex: Int,
    ): String = buildString {
        appendLine(
            "Analizza la settimana ${report.weekIndex} e proponi come impostare la settimana $nextWeekIndex.",
        )
        appendLine("Obiettivo del piano: ${plan.input.goal.promptLabel}")
        appendLine(
            "Giorni disponibili: ${FitnessPlanPromptBuilder.weekdayNames(plan.input.sortedWeekdays)}",
        )
        appendLine("Inizio del piano (dayOffset 0): ${shortDate(plan.startDateEpochMillis)}")
        appendLine()
        appendLine("--- ANDAMENTO DELLA SETTIMANA ${report.weekIndex} ---")
        appendLine("Sedute previste: ${report.plannedSessions}")
        appendLine("Sedute completate: ${report.completedSessions} (${report.completionPercent}%)")
        appendLine("Sedute saltate: ${report.skippedSessions}")
        appendLine("Minuti totali di attività: ${report.totalMinutes}")
        if (report.totalKcal > 0) appendLine("Calorie attive stimate: ${report.totalKcal}")
        if (report.chronicallySkippedWeekdays.isNotEmpty()) {
            appendLine(
                "Giorni saltati in modo ricorrente: " +
                    FitnessPlanPromptBuilder.weekdayNames(report.chronicallySkippedWeekdays),
            )
        }
        if (plan.safetyNotes.isNotEmpty()) {
            appendLine()
            appendLine("--- VINCOLI CLINICI GIÀ STABILITI (da rispettare) ---")
            plan.safetyNotes.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("--- SEDUTE DELLA SETTIMANA $nextWeekIndex, COSÌ COME SONO ORA ---")
        sessionLines(
            plan.weeks.firstOrNull { it.index == nextWeekIndex }?.sessions.orEmpty(),
            plan.startDateEpochMillis,
        ).forEach { appendLine(it) }
    }

    /** Righe compatte di una seduta: id, data, contenuto e stato. */
    fun sessionLines(sessions: List<FitnessSession>, startDateEpochMillis: Long): List<String> {
        if (sessions.isEmpty()) return listOf("Nessuna seduta.")
        return sessions.map { session ->
            val offset = FitnessPlanDates.daysBetween(startDateEpochMillis, session.dateEpochMillis)
            val line = StringBuilder("id=${session.id} | dayOffset=$offset")
            line.append(" | ${shortDate(session.dateEpochMillis)} | ${session.title}")
            line.append(
                " | ${session.activityType}, ${session.durationMinutes} min, intensità ${session.intensity}",
            )
            line.append(" | stato: ${statusLabel(session.status)}")
            if (session.exercises.isNotEmpty()) {
                line.append(
                    " | esercizi: " +
                        session.exercises.joinToString("; ") { "${it.name} (${it.detail})" },
                )
            }
            if (session.targets.isNotEmpty()) {
                line.append(" | obiettivi: ${session.targets.joinToString("; ")}")
            }
            line.toString()
        }
    }

    private fun statusLabel(status: FitnessSessionStatus): String = when (status) {
        FitnessSessionStatus.PLANNED -> "da fare"
        FitnessSessionStatus.DONE -> "completata"
        FitnessSessionStatus.SKIPPED -> "saltata"
        FitnessSessionStatus.MOVED -> "spostata"
    }

    private fun shortDate(epochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMillis))

    private fun aiUserMessage(content: String) = KBAIMessage(
        id = "",
        conversationId = "",
        roleRaw = "user",
        content = content,
        createdAtEpochMillis = System.currentTimeMillis(),
    )

    const val PURPOSE_PLAN = "fitnessPlan"
    const val PURPOSE_ADJUST = "fitnessAdjust"
    const val PURPOSE_COPILOT = "fitnessCopilot"
}
