package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.domain.model.KBPlan
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    fun planFlow(familyId: String, uid: String): Flow<KBPlan>

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
