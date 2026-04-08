package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FamilyLeaveService @Inject constructor(
    private val database: KidBoxDatabase,
) {
    private val db = FirebaseFirestore.getInstance()

    suspend fun leaveFamily(familyId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")

        db.collection("families")
            .document(familyId)
            .collection("members")
            .document(uid)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()

        db.collection("users")
            .document(uid)
            .collection("memberships")
            .document(familyId)
            .delete()
            .await()

        database.clearAllTables()
    }
}
