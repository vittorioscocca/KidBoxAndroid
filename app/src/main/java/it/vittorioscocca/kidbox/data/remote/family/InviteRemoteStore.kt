package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Scrive la membership quando si entra in una famiglia.
 *
 * I codici invito testuali non esistono più: creavano membri privi della chiave
 * di cifratura, incapaci di leggere password, documenti, wallet e allegati della
 * chat. Si entra dal QR o dal link, che portano entrambi `familyId` e il
 * materiale crittografico.
 */
class InviteRemoteStore(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val db get() = FirebaseFirestore.getInstance()
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

    /** Info mostrabili PRIMA del join: nome famiglia e di chi ha invitato. */
    data class InvitePreview(
        val familyName: String?,
        val inviterDisplayName: String?,
    )

    /**
     * Legge il documento invito senza consumarlo, per mostrare a chi riceve
     * il link "stai per entrare nella famiglia di…" prima che confermi.
     *
     * `families/{familyId}/invites/{inviteId}` è leggibile da ogni utente
     * autenticato (non solo dai membri): è la stessa regola che permette a
     * QR e link di funzionare per chi non è ancora dentro la famiglia.
     */
    suspend fun fetchInvitePreview(familyId: String, inviteId: String): InvitePreview {
        return runCatching {
            val doc = db.collection("families").document(familyId)
                .collection("invites").document(inviteId)
                .get().await()
            InvitePreview(
                familyName = doc.getString("familyName"),
                inviterDisplayName = doc.getString("createdByDisplayName"),
            )
        }.getOrElse { InvitePreview(familyName = null, inviterDisplayName = null) }
    }
}
