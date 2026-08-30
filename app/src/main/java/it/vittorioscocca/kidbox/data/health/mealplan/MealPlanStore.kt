package it.vittorioscocca.kidbox.data.health.mealplan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenza locale dell'ultimo piano alimentare generato per profilo (childId).
 * Stessa logica di [it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordStore]:
 * il piano costa messaggi AI, quindi non va rigenerato a ogni apertura.
 */
@Singleton
class MealPlanStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun directory(): File {
        val dir = File(context.filesDir, "meal_plans")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun file(childId: String) = File(directory(), "meal_plan_$childId.json")

    fun save(childId: String, document: MealPlanDocument) {
        val json = JSONObject().apply {
            put("subjectName", document.subjectName)
            put("text", document.text)
            put("generatedAtEpochMillis", document.generatedAtEpochMillis)
            put("messageUnitsConsumed", document.messageUnitsConsumed)
            put("goal", document.input.goal.name)
            put("activityLevel", document.input.activityLevel.name)
            put("preferredFoods", document.input.preferredFoods)
            put("avoidedFoods", document.input.avoidedFoods)
            put("notes", document.input.notes)
            put("manualAgeYears", document.input.manualAgeYears)
            put("manualWeightKg", document.input.manualWeightKg)
            put("manualHeightCm", document.input.manualHeightCm)
        }
        file(childId).writeText(json.toString())
    }

    fun load(childId: String): MealPlanDocument? {
        val file = file(childId)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            MealPlanDocument(
                subjectName = json.optString("subjectName"),
                input = MealPlanInput(
                    goal = enumValueOrDefault(json.optString("goal"), MealPlanGoal.FAT_LOSS),
                    activityLevel = enumValueOrDefault(
                        json.optString("activityLevel"),
                        MealPlanActivityLevel.MODERATE,
                    ),
                    preferredFoods = json.optString("preferredFoods"),
                    avoidedFoods = json.optString("avoidedFoods"),
                    notes = json.optString("notes"),
                    manualAgeYears = json.optString("manualAgeYears"),
                    manualWeightKg = json.optString("manualWeightKg"),
                    manualHeightCm = json.optString("manualHeightCm"),
                ),
                text = json.optString("text"),
                generatedAtEpochMillis = json.optLong("generatedAtEpochMillis", file.lastModified()),
                messageUnitsConsumed = json.optInt("messageUnitsConsumed", 0),
            )
        }.getOrNull()
    }

    fun clear(childId: String) {
        file(childId).delete()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default
}
