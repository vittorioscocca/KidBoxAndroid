package it.vittorioscocca.kidbox.data.remote.ai

import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import kotlin.math.ceil

/** Allineato a askAI in functions/index.js. */
object AIAskAIPayload {
    const val STANDARD_CHARS: Int = 50_000
    const val ABSOLUTE_MAX_CHARS: Int = 500_000

    /** Unità minime per la cartella clinica (Sonnet ~3× Haiku + niente caching).
     *  Parity con `CLINICAL_RECORD_MIN_UNITS` in `functions/index.js`. */
    const val CLINICAL_RECORD_MIN_UNITS: Int = 3

    /** Unità minime per il piano alimentare (Haiku + output lungo: piano 90 giorni).
     *  Parity con `MEAL_PLAN_MIN_UNITS` in `functions/index.js`. */
    const val MEAL_PLAN_MIN_UNITS: Int = 5

    /** Unità minime per il piano fitness (JSON lungo su 4 settimane), prima del
     *  moltiplicatore. Parity con `FITNESS_PLAN_MIN_UNITS` in `functions/index.js`. */
    const val FITNESS_PLAN_MIN_UNITS: Int = 5

    /** Tutto il modulo Piano Fitness gira su Sonnet (~3× Haiku): ogni chiamata
     *  scala il triplo delle unità. Parity con `FITNESS_UNITS_MULTIPLIER`. */
    const val FITNESS_UNITS_MULTIPLIER: Int = 3

    fun totalChars(systemPrompt: String, messages: List<KBAIMessage>, pendingUserText: String = ""): Int {
        val history = messages.sumOf { it.content.length }
        val pending = pendingUserText.trim().length
        return systemPrompt.length + history + pending
    }

    fun messageUnits(totalChars: Int): Int {
        if (totalChars <= 0) return 1
        return maxOf(1, ceil(totalChars.toDouble() / STANDARD_CHARS.toDouble()).toInt())
    }

    fun isLargeContext(totalChars: Int): Boolean = messageUnits(totalChars) > 1

    /** Unità per la cartella clinica: minimo fisso [CLINICAL_RECORD_MIN_UNITS],
     *  oppure le unità del payload se il contesto è molto grande. */
    fun clinicalRecordMessageUnits(totalChars: Int): Int =
        maxOf(CLINICAL_RECORD_MIN_UNITS, messageUnits(totalChars))

    /** Unità per il piano alimentare: minimo fisso [MEAL_PLAN_MIN_UNITS],
     *  oppure le unità del payload se il contesto è molto grande. */
    fun mealPlanMessageUnits(totalChars: Int): Int =
        maxOf(MEAL_PLAN_MIN_UNITS, messageUnits(totalChars))

    /** Unità per il piano fitness: minimo fisso [FITNESS_PLAN_MIN_UNITS] (o le
     *  unità del payload se il contesto è molto grande), per [FITNESS_UNITS_MULTIPLIER]. */
    fun fitnessPlanMessageUnits(totalChars: Int): Int =
        FITNESS_UNITS_MULTIPLIER * maxOf(FITNESS_PLAN_MIN_UNITS, messageUnits(totalChars))

    /** Unità per le chiamate brevi del Piano Fitness (spostamento seduta,
     *  adeguamento settimanale, copilota): unità del payload per [FITNESS_UNITS_MULTIPLIER]. */
    fun fitnessAssistMessageUnits(totalChars: Int): Int =
        FITNESS_UNITS_MULTIPLIER * messageUnits(totalChars)

    fun transientLargeContextNotice(context: android.content.Context): String =
        context.getString(it.vittorioscocca.kidbox.R.string.health_ctx_large_notice)

    fun choiceDialogMessage(
        context: android.content.Context,
        fullUnits: Int,
        compactAskUnits: Int,
        compactSetupUnits: Int,
        hasCompactCache: Boolean,
    ): String =
        if (hasCompactCache) {
            context.getString(
                it.vittorioscocca.kidbox.R.string.health_ctx_choice_cached,
                STANDARD_CHARS, fullUnits, compactAskUnits,
            )
        } else {
            context.getString(
                it.vittorioscocca.kidbox.R.string.health_ctx_choice_fresh,
                fullUnits, compactAskUnits, compactSetupUnits,
            )
        }

    fun compactChoiceButtonLabel(context: android.content.Context, askUnits: Int, setupUnits: Int): String =
        if (setupUnits > 0) {
            context.getString(it.vittorioscocca.kidbox.R.string.health_ctx_compact_btn_setup, askUnits, setupUnits)
        } else {
            context.getString(it.vittorioscocca.kidbox.R.string.health_ctx_compact_btn, askUnits)
        }
}
