package it.vittorioscocca.kidbox.data.health.mealplan

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

/**
 * Generazione del piano alimentare via Cloud Function `askAI` con
 * `purpose: "mealPlan"` (Anthropic Haiku lato server, max_tokens esteso).
 */
object MealPlanGenerator {

    /** Overhead delle regole aggiunte lato server (`MEAL_PLAN_SYSTEM_RULES` in index.js). */
    private const val SERVER_RULES_OVERHEAD = 900

    data class Estimate(val totalChars: Int, val messageUnits: Int)

    data class Payload(
        val systemPrompt: String,
        val userContent: String,
        val profileSummary: List<String>,
        val hasWeight: Boolean,
        val hasHeight: Boolean,
    )

    fun buildPayload(
        subjectName: String,
        birthDateEpochMillis: Long?,
        input: MealPlanInput,
        snapshot: HealthImportSnapshot?,
        profile: KBPediatricProfile?,
        treatments: List<KBTreatmentEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
    ): Payload {
        val healthContext = ClinicalRecordHealthContextBuilder.buildClinicalPrompt(
            subjectName = subjectName,
            treatments = treatments,
            visits = visits,
            exams = exams,
            health = snapshot,
            healthLabel = "Health Connect",
            birthDateEpochMillis = birthDateEpochMillis ?: snapshot?.birthDateEpochMillis,
            refertoMaxChars = MealPlanPromptBuilder.REFERTO_MAX_CHARS,
        )
        val profileSummary = MealPlanPromptBuilder.profileSummaryLines(
            birthDateEpochMillis = birthDateEpochMillis,
            snapshot = snapshot,
            profile = profile,
            input = input,
        )
        return Payload(
            systemPrompt = MealPlanPromptBuilder.systemPrompt(
                MealPlanPromptBuilder.responseLanguageName(),
            ),
            userContent = MealPlanPromptBuilder.userContent(
                subjectName = subjectName,
                input = input,
                profileSummary = profileSummary,
                healthContext = healthContext,
            ),
            profileSummary = profileSummary,
            hasWeight = snapshot?.weightKg != null || input.manualWeightValue != null,
            hasHeight = snapshot?.heightCm != null || input.manualHeightValue != null,
        )
    }

    fun estimate(payload: Payload): Estimate {
        val total = AIAskAIPayload.totalChars(
            payload.systemPrompt,
            listOf(aiUserMessage(payload.userContent)),
        ) + SERVER_RULES_OVERHEAD
        return Estimate(total, AIAskAIPayload.mealPlanMessageUnits(total))
    }

    data class Result(val document: MealPlanDocument, val usage: MealPlanAIUsageInfo)

    suspend fun generate(
        aiRepository: AiRepository,
        usageTracker: AIUsageTracker,
        familyId: String,
        subjectName: String,
        input: MealPlanInput,
        payload: Payload,
    ): Result {
        // Feature dei soli piani a pagamento (il server rifiuta comunque i Free).
        if (CurrentPlanStore.plan.value == KBPlan.FREE) throw MealPlanError.PlanNotIncluded
        if (!payload.hasWeight || !payload.hasHeight) throw MealPlanError.MissingHealthData

        val estimate = estimate(payload)
        if (estimate.totalChars > AIAskAIPayload.ABSOLUTE_MAX_CHARS) {
            throw MealPlanError.PayloadTooLarge(estimate.totalChars, AIAskAIPayload.ABSOLUTE_MAX_CHARS)
        }

        val usageSnapshot = usageTracker.state.value
        if (usageSnapshot.dailyLimit > 0) {
            val remaining = (usageSnapshot.dailyLimit - usageSnapshot.usageToday).coerceAtLeast(0)
            if (estimate.messageUnits > remaining) {
                throw MealPlanError.QuotaWouldExceed(
                    needed = estimate.messageUnits,
                    remaining = remaining,
                    dailyLimit = usageSnapshot.dailyLimit,
                )
            }
        }

        val reply = aiRepository.askAI(
            familyId = familyId,
            systemPrompt = payload.systemPrompt,
            messages = listOf(aiUserMessage(payload.userContent)),
            purpose = "mealPlan",
        ).getOrElse { throw it }

        val document = MealPlanDocument(
            subjectName = subjectName,
            input = input,
            text = sanitize(reply.reply),
            generatedAtEpochMillis = System.currentTimeMillis(),
            messageUnitsConsumed = reply.messageUnitsConsumed,
        )
        val usage = MealPlanAIUsageInfo(
            messageUnitsConsumed = reply.messageUnitsConsumed,
            usageToday = reply.usageToday,
            dailyLimit = reply.dailyLimit,
            totalPayloadChars = estimate.totalChars,
            period = reply.period,
        )
        return Result(document, usage)
    }

    /** Rimuove il Markdown residuo: la UI rende testo semplice. */
    private fun sanitize(text: String): String {
        var cleaned = text
        listOf("**", "###", "##", "`").forEach { cleaned = cleaned.replace(it, "") }
        return cleaned.trim()
    }

    private fun aiUserMessage(content: String) = KBAIMessage(
        id = "",
        conversationId = "",
        roleRaw = "user",
        content = content,
        createdAtEpochMillis = System.currentTimeMillis(),
    )
}
