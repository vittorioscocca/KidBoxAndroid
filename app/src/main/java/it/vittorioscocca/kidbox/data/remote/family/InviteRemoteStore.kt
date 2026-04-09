package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.random.Random

class InviteRemoteStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    suspend fun createInviteCode(familyId: String, ttlDays: Int = 7): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        repeat(10) {
            val code = generateCode()
            val ref = db.collection("invites").document(code)
            try {
                db.runTransaction { transaction ->
                    val snap = transaction.get(ref)
                    if (snap.exists()) {
                        error("collision")
                    }
                    val expiresAt = Date(System.currentTimeMillis() + ttlDays * 24L * 3600L * 1000L)
                    transaction.set(
                        ref,
                        mapOf(
                            "familyId" to familyId,
                            "createdBy" to uid,
                            "revoked" to false,
                            "usedAt" to null,
                            "usedBy" to null,
                            "createdAt" to Timestamp.now(),
                            "expiresAt" to Timestamp(expiresAt),
                        ),
                    )
                }.await()
                return code
            } catch (_: Exception) {
                // Retry on collision/transient transaction failures.
            }
        }
        error("Unable to generate unique invite code")
    }

    suspend fun resolveInvite(code: String): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("invites").document(code)
        return db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            if (!snap.exists()) error("Codice non valido")
            val data = snap.data ?: error("Codice non valido")
            if (data["revoked"] == true) error("Codice revocato")
            if (data["usedAt"] != null) error("Codice già utilizzato")
            val expiresAt = data["expiresAt"] as? Timestamp
            if (expiresAt != null && expiresAt.toDate().before(Date())) error("Codice scaduto")
            transaction.update(
                ref,
                mapOf(
                    "usedAt" to Timestamp.now(),
                    "usedBy" to uid,
                ),
            )
            data["familyId"] as? String ?: error("Invite malformato")
        }.await()
    }

    suspend fun addMember(familyId: String, role: String = "member") {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val memberRef = db.collection("families")
            .document(familyId)
            .collection("members")
            .document(uid)
        val membershipRef = db.collection("users")
            .document(uid)
            .collection("memberships")
            .document(familyId)
        val memberFields = mutableMapOf<String, Any>(
            "uid" to uid,
            "role" to role,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        auth.currentUser?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }?.let {
            memberFields["displayName"] = it
        }
        auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.let {
            memberFields["email"] = it
        }
        val batch = db.batch()
        batch.set(
            memberRef,
            memberFields,
            com.google.firebase.firestore.SetOptions.merge(),
        )
        batch.set(
            membershipRef,
            mapOf(
                "familyId" to familyId,
                "role" to role,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        )
        batch.commit().await()
    }

    private fun generateCode(length: Int = 8): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(length) {
            repeat(length) { append(alphabet[Random.nextInt(alphabet.length)]) }
        }
    }
}
