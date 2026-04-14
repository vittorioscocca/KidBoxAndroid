package it.vittorioscocca.kidbox.data.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class CountersService @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun reset(
        familyId: String,
        field: CounterField,
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (familyId.isBlank()) return
        db.collection("families")
            .document(familyId)
            .collection("counters")
            .document(uid)
            .set(
                mapOf(
                    field.raw to 0,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }
}

