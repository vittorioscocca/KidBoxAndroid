package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.domain.model.KBPlan

interface SubscriptionRepository {
    suspend fun getPlan(familyId: String): KBPlan
}
