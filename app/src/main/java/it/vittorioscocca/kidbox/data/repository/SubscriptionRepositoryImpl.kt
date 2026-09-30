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
                // Prova scaduta ma non ancora riportata al Free dal job orario del
                // server: per le quote il server la considera già finita, e così qui.
                if (data["planSource"] == "trial") {
                    val end = (data["planExpiresAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                    if (end != null && end <= System.currentTimeMillis()) plan = KBPlan.FREE
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

    override suspend fun loadTrialState(familyId: String): KBTrialState {
        if (familyId.isBlank()) return KBTrialState()
        return runCatching {
            val data = firestore.collection("families").document(familyId).get().await().data.orEmpty()
            val override = (data["planOverride"] as? String)?.trim()?.lowercase()
            if (override == KBPlan.PRO.rawValue || override == KBPlan.MAX.rawValue) return@runCatching KBTrialState()
            val end = (data["planExpiresAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
            when (data["planSource"]) {
                "trial" -> if (end != null && end > System.currentTimeMillis()) {
                    KBTrialState(endsAtMillis = end)
                } else {
                    KBTrialState(ended = true)
                }
                "trial_ended" -> KBTrialState(ended = true)
                else -> KBTrialState()
            }
        }.getOrDefault(KBTrialState())
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

    override suspend fun loadTrialOffer(familyId: String): KBTrialOfferStatus? {
        if (familyId.isBlank()) return null
        return runCatching {
            val result = functions.getHttpsCallable("getProTrialStatus")
                .call(hashMapOf("familyId" to familyId))
                .await()
            val data = result.getData() as? Map<*, *> ?: error("Risposta non valida")
            KBTrialOfferStatus(
                eligible = data["eligible"] == true,
                days = (data["days"] as? Number)?.toInt() ?: 14,
                aiLimit = (data["aiLimit"] as? Number)?.toInt() ?: 50,
                ownerCanStart = data["ownerCanStart"] == true,
                askedOwner = data["askedOwner"] == true,
            )
        }.getOrNull()
    }

    override suspend fun askOwnerForTrial(familyId: String): Result<Boolean> = runCatching {
        if (familyId.isBlank()) error("Famiglia non disponibile")
        val result = functions.getHttpsCallable("askOwnerForProTrial")
            .call(hashMapOf("familyId" to familyId))
            .await()
        (result.getData() as? Map<*, *>)?.get("sent") == true
    }

    override suspend fun startTrial(familyId: String): Result<Unit> = runCatching {
        if (familyId.isBlank()) error("Famiglia non disponibile")
        functions.getHttpsCallable("startProTrial")
            .call(hashMapOf("familyId" to familyId))
            .await()
        Unit
    }

    override suspend fun getPlan(familyId: String): KBPlan {
        val uid = auth.currentUser?.uid.orEmpty()
        return loadPlan(familyId, uid)
    }
}
