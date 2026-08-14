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
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
) : SubscriptionRepository {

    /** Always [FirebaseFirestore.getInstance] — dopo [FirebaseFirestore.terminate] il singleton si rinnova. */
    private val firestore get() = FirebaseFirestore.getInstance()

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
            var plan = KBPlan.FREE

            if (familyId.isNotBlank()) {
                val familyDoc = firestore.collection("families").document(familyId).get().await()
                val data = familyDoc.data.orEmpty()
                val override = (data["planOverride"] as? String)?.trim()?.lowercase()
                plan = if (override == KBPlan.PRO.rawValue || override == KBPlan.MAX.rawValue) {
                    KBPlan.fromRawValue(override)
                } else {
                    KBPlan.fromRawValue(data["plan"] as? String)
                }
            }

            if (plan == KBPlan.FREE) {
                val effectiveUid = uid.ifBlank { auth.currentUser?.uid.orEmpty() }
                if (effectiveUid.isNotBlank()) {
                    val userDoc = firestore.collection("users").document(effectiveUid).get().await()
                    plan = KBPlan.fromRawValue(userDoc.getString("plan"))
                }
            }

            plan
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
        if (purchaseToken.isBlank()) error("Token di acquisto non disponibile")
        // `validatePurchase` verifica il token con Google Play prima di concedere
        // il piano: non inviamo più il piano desiderato, lo deduce il server dal
        // productId dell'acquisto verificato. (La vecchia callable "updatePlan"
        // non è mai esistita lato server: gli acquisti non venivano registrati.)
        functions.getHttpsCallable("validatePurchase")
            .call(
                hashMapOf(
                    "familyId" to familyId,
                    "platform" to "android",
                    "purchaseToken" to purchaseToken,
                    "productId" to (plan.productId ?: error("Piano senza productId")),
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
