package it.vittorioscocca.kidbox.data.health.fitness

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenza locale del piano fitness per profilo (childId).
 *
 * Stessa logica di [it.vittorioscocca.kidbox.data.health.mealplan.MealPlanStore]:
 * generare il piano costa messaggi AI, quindi non va rigenerato a ogni apertura.
 * Qui però il documento cambia spesso (stati delle sedute, spostamenti), quindi
 * si riscrive a ogni modifica.
 */
@Singleton
class FitnessPlanStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private fun directory(): File {
        val dir = File(context.filesDir, "fitness_plans")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun file(childId: String) = File(directory(), "fitness_plan_$childId.json")

    fun save(childId: String, document: FitnessPlanDocument) {
        file(childId).writeText(FitnessPlanJson.encode(document))
    }

    fun load(childId: String): FitnessPlanDocument? {
        val file = file(childId)
        if (!file.exists()) return null
        return FitnessPlanJson.decode(file.readText())
    }

    fun clear(childId: String) {
        file(childId).delete()
        prefs.edit()
            .remove(reviewedKey(childId))
            .remove(lastSyncKey(childId))
            .remove(pendingRescheduleKey(childId))
            .apply()
    }

    /** Settimane per cui l'utente ha già chiuso il report: evita di riproporlo. */
    fun reviewedWeeks(childId: String): Set<Int> =
        prefs.getStringSet(reviewedKey(childId), emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    fun markWeekReviewed(childId: String, weekIndex: Int) {
        val weeks = reviewedWeeks(childId) + weekIndex
        prefs.edit()
            .putStringSet(reviewedKey(childId), weeks.map { it.toString() }.toSet())
            .apply()
    }

    fun lastHealthSync(childId: String): Long? =
        prefs.getLong(lastSyncKey(childId), 0L).takeIf { it > 0L }

    fun setLastHealthSync(childId: String, epochMillis: Long) {
        prefs.edit().putLong(lastSyncKey(childId), epochMillis).apply()
    }

    /**
     * Seduta spostata da una notifica, per la quale resta da chiedere all'AI di
     * riorganizzare la settimana: la richiesta ha bisogno di rete, quindi la fa
     * la schermata alla prima apertura.
     */
    fun pendingReschedule(childId: String): String? =
        prefs.getString(pendingRescheduleKey(childId), null)?.takeIf { it.isNotBlank() }

    fun setPendingReschedule(childId: String, sessionId: String) {
        prefs.edit().putString(pendingRescheduleKey(childId), sessionId).apply()
    }

    fun clearPendingReschedule(childId: String) {
        prefs.edit().remove(pendingRescheduleKey(childId)).apply()
    }

    private fun reviewedKey(childId: String) = "reviewed_weeks_$childId"
    private fun lastSyncKey(childId: String) = "last_health_sync_$childId"
    private fun pendingRescheduleKey(childId: String) = "pending_reschedule_$childId"

    private companion object {
        const val PREFS_NAME = "kb_fitness_plan"
    }
}
