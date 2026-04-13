package it.vittorioscocca.kidbox.data.remote.grocery

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class GroceryRemoteDto(
    val id: String,
    val familyId: String,
    val name: String,
    val category: String?,
    val notes: String?,
    val isPurchased: Boolean,
    val isDeleted: Boolean,
    val purchasedAtEpochMillis: Long?,
    val purchasedBy: String?,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdBy: String?,
)

sealed interface GroceryRemoteChange {
    data class Upsert(val dto: GroceryRemoteDto) : GroceryRemoteChange
    data class Remove(val id: String) : GroceryRemoteChange
}

@Singleton
class GroceryRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, itemId: String) = db.collection("families")
        .document(familyId)
        .collection("groceries")
        .document(itemId)

    fun listenGroceries(
        familyId: String,
        onChange: (List<GroceryRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families")
            .document(familyId)
            .collection("groceries")
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val changes = snap.documentChanges.mapNotNull { diff ->
                    val doc = diff.document
                    val d = doc.data
                    val name = (d["name"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> GroceryRemoteChange.Upsert(
                            GroceryRemoteDto(
                                id = doc.id,
                                familyId = familyId,
                                name = name,
                                category = (d["category"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                notes = (d["notes"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                isPurchased = d["isPurchased"] as? Boolean ?: false,
                                isDeleted = d["isDeleted"] as? Boolean ?: false,
                                purchasedAtEpochMillis = (d["purchasedAt"] as? Timestamp)?.toDate()?.time,
                                purchasedBy = d["purchasedBy"] as? String,
                                updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                updatedBy = d["updatedBy"] as? String,
                                createdBy = d["createdBy"] as? String,
                            ),
                        )

                        DocumentChange.Type.REMOVED -> GroceryRemoteChange.Remove(doc.id)
                    }
                }
                if (changes.isNotEmpty()) onChange(changes)
            }
    }

    suspend fun upsert(item: KBGroceryItemEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val payload = mutableMapOf<String, Any?>(
            "name" to item.name,
            "category" to item.category,
            "notes" to item.notes,
            "isPurchased" to item.isPurchased,
            "isDeleted" to false,
            "purchasedBy" to item.purchasedBy,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        payload["purchasedAt"] = item.purchasedAtEpochMillis?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
        if (item.createdBy != null) payload["createdBy"] = item.createdBy
        ref(item.familyId, item.id).set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, itemId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, itemId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
