package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Come iOS [FamilyMemberRemoteStore.upsertMyMemberProfileIfNeeded]:
 * merge su `families/{familyId}/members/{uid}` senza sovrascrivere il ruolo.
 */
@Singleton
class FamilyMemberProfileRemoteStore @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    suspend fun upsertMyMemberProfileIfNeeded(familyId: String, displayName: String? = null) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val ref = db.collection("families").document(familyId).collection("members").document(uid)
        val data = mutableMapOf<String, Any>(
            "uid" to uid,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
            "isDeleted" to false,
        )
        val resolvedName = displayName?.trim()
        if (!resolvedName.isNullOrEmpty() && resolvedName != "Utente") {
            data["displayName"] = resolvedName
        }
        user.email?.trim()?.takeIf { it.isNotEmpty() }?.let { data["email"] = it }
        user.photoUrl?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { data["photoURL"] = it }
        ref.set(data, SetOptions.merge()).await()
    }
}
