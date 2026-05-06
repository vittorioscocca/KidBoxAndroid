package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SubscriptionRepository {

    override suspend fun getPlan(familyId: String): KBPlan {
        if (familyId.isBlank()) return KBPlan.FREE
        return runCatching {
            val doc = firestore.collection("families").document(familyId).get().await()
            val raw = doc.getString("plan")
            KBPlan.from(raw)
        }.getOrDefault(KBPlan.FREE)
    }
}
