package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.domain.model.KBPlan
import kotlinx.coroutines.flow.Flow

/**
 * Prova Pro della famiglia, concessa dal server (functions/proTrial.js) con
 * `planSource: "trial"` e `planExpiresAt`.
 * @property endsAtMillis fine della prova se è in corso adesso, altrimenti null
 * @property ended la prova c'è stata ed è finita senza abbonamento
 */
data class KBTrialState(val endsAtMillis: Long? = null, val ended: Boolean = false) {
    /** Giorni interi rimasti (1 nell'ultimo giorno), null se non in prova. */
    fun daysLeft(nowMillis: Long = System.currentTimeMillis()): Int? {
        val end = endsAtMillis ?: return null
        val left = end - nowMillis
        if (left <= 0) return null
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

interface SubscriptionRepository {
    fun planFlow(familyId: String, uid: String): Flow<KBPlan>

    /** Stato della prova Pro della famiglia (una lettura del documento famiglia). */
    suspend fun loadTrialState(familyId: String): KBTrialState

    suspend fun loadPlan(familyId: String, uid: String): KBPlan

    suspend fun updatePlanAfterPurchase(
        plan: KBPlan,
        purchaseToken: String,
        familyId: String,
        uid: String,
    ): Result<Unit>

    // Backward-compatible method used by existing screens/viewmodels.
    suspend fun getPlan(familyId: String): KBPlan
}
