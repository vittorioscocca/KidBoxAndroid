package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
) : SubscriptionRepository {

    override fun planFlow(familyId: String, uid: String): Flow<KBPlan> = callbackFlow {
        if (familyId.isBlank()) {
            trySend(KBPlan.FREE)
            close()
            return@callbackFlow
        }

        val familyListener = firestore.collection("families").document(familyId)
            .addSnapshotListener { _, _ ->
                launch {
                    trySend(loadPlan(familyId, uid))
                }
            }

        val userListener = if (uid.isNotBlank()) {
            firestore.collection("users").document(uid)
                .addSnapshotListener { _, _ ->
                    launch {
                        trySend(loadPlan(familyId, uid))
                    }
                }
        } else {
            null
        }

        launch {
            trySend(loadPlan(familyId, uid))
        }

        awaitClose {
            familyListener.remove()
            userListener?.remove()
        }
    }

    override suspend fun loadPlan(familyId: String, uid: String): KBPlan {
        return runCatching {
            var rawPlan: String? = null

            if (familyId.isNotBlank()) {
                val familyDoc = firestore.collection("families").document(familyId).get().await()
                rawPlan = familyDoc.getString("planOverride")
                    ?.takeIf { it.isNotBlank() }
                    ?: familyDoc.getString("plan")
            }

            if (rawPlan.isNullOrBlank() || KBPlan.fromRawValue(rawPlan) == KBPlan.FREE) {
                val effectiveUid = uid.ifBlank { auth.currentUser?.uid.orEmpty() }
                if (effectiveUid.isNotBlank()) {
                    val userDoc = firestore.collection("users").document(effectiveUid).get().await()
                    val userPlan = userDoc.getString("plan")
                    if (!userPlan.isNullOrBlank()) rawPlan = userPlan
                }
            }

            KBPlan.fromRawValue(rawPlan)
        }.getOrDefault(KBPlan.FREE)
    }

    override suspend fun updatePlanAfterPurchase(
        plan: KBPlan,
        purchaseToken: String,
        familyId: String,
        uid: String,
    ): Result<Unit> = runCatching {
        if (familyId.isBlank()) error("Famiglia non disponibile")
        if (uid.isBlank()) error("Utente non autenticato")
        functions.getHttpsCallable("updatePlan")
            .call(
                hashMapOf(
                    "plan" to plan.rawValue,
                    "transactionId" to purchaseToken,
                    "familyId" to familyId,
                ),
            )
            .await()
        Unit
    }

    override suspend fun getPlan(familyId: String): KBPlan {
        val uid = auth.currentUser?.uid.orEmpty()
        return loadPlan(familyId, uid)
    }
}
