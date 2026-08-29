package it.vittorioscocca.kidbox.data.health.mealplan

import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod

/** Obiettivo del piano alimentare scelto dall'utente. */
enum class MealPlanGoal(val labelRes: Int, val promptLabel: String) {
    FAT_LOSS(
        R.string.meal_plan_goal_fat_loss,
        "perdere grasso mantenendo la massa muscolare",
    ),
    MAINTENANCE(
        R.string.meal_plan_goal_maintenance,
        "mantenere il peso migliorando la qualità della dieta",
    ),
    MUSCLE_GAIN(
        R.string.meal_plan_goal_muscle_gain,
        "aumentare la massa magra con un surplus calorico contenuto",
    ),
}

/** Livello di attività dichiarato, usato per stimare le calorie di mantenimento. */
enum class MealPlanActivityLevel(val labelRes: Int, val promptLabel: String) {
    SEDENTARY(R.string.meal_plan_activity_sedentary, "sedentario (poco o nessun allenamento)"),
    LIGHT(R.string.meal_plan_activity_light, "leggero (1-2 allenamenti a settimana)"),
    MODERATE(R.string.meal_plan_activity_moderate, "moderato (3-4 allenamenti a settimana)"),
    INTENSE(R.string.meal_plan_activity_intense, "intenso (5 o più allenamenti a settimana)"),
}

/** Input raccolti nel form prima della generazione. */
data class MealPlanInput(
    val goal: MealPlanGoal = MealPlanGoal.FAT_LOSS,
    val activityLevel: MealPlanActivityLevel = MealPlanActivityLevel.MODERATE,
    val preferredFoods: String = "",
    val avoidedFoods: String = "",
    val notes: String = "",
)

/** Piano alimentare generato, salvato in locale per non doverlo rigenerare. */
data class MealPlanDocument(
    val subjectName: String,
    val input: MealPlanInput,
    val text: String,
    val generatedAtEpochMillis: Long,
    val messageUnitsConsumed: Int,
) {
    val sections: List<MealPlanSection> get() = MealPlanSection.parse(text)
}

/** Blocco di testo del piano, ricavato dai titoli in MAIUSCOLO prodotti dall'AI. */
data class MealPlanSection(
    val id: String,
    val title: String,
    val body: String,
) {
    companion object {
        fun parse(text: String): List<MealPlanSection> {
            val sections = mutableListOf<MealPlanSection>()
            var currentTitle = ""
            var currentBody = mutableListOf<String>()

            fun flush() {
                val body = currentBody.joinToString("\n").trim()
                if (currentTitle.isBlank() && body.isBlank()) return
                sections += MealPlanSection("${sections.size}-$currentTitle", currentTitle, body)
            }

            text.lines().forEach { raw ->
                val line = raw.trim()
                if (line == "---") return@forEach
                if (isTitle(line)) {
                    flush()
                    currentTitle = line
                    currentBody = mutableListOf()
                } else {
                    currentBody += raw
                }
            }
            flush()
            return sections
        }

        private fun isTitle(line: String): Boolean {
            if (line.length <= 3 || line.length > 80) return false
            if (line.none { it.isLetter() }) return false
            return line == line.uppercase()
        }
    }
}

/** Contatore messaggi AI dopo la generazione (allineato a askAI / AIAskAIPayload). */
data class MealPlanAIUsageInfo(
    val messageUnitsConsumed: Int,
    val usageToday: Int,
    val dailyLimit: Int,
    val totalPayloadChars: Int,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
)

/** Errori del piano alimentare mappati su stringhe localizzate dalla UI. */
sealed class MealPlanError : Exception() {
    data object PlanNotIncluded : MealPlanError()
    data class QuotaWouldExceed(val needed: Int, val remaining: Int, val dailyLimit: Int) : MealPlanError()
    data class PayloadTooLarge(val chars: Int, val maxChars: Int) : MealPlanError()
    data object MissingHealthData : MealPlanError()
}
