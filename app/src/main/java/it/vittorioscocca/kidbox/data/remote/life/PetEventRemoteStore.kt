package it.vittorioscocca.kidbox.data.remote.life

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class PetEventRemoteDto(
    val id: String,
    val familyId: String,
    val petId: String,
    val title: String,
    val eventTypeRaw: String,
    val dateMillis: Long,
    val nextDueDateMillis: Long?,
    val notes: String?,
    val vetName: String?,
    val cost: Double?,
    val reminderEnabled: Boolean,
    val isDeleted: Boolean,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val createdBy: String?,
    val updatedBy: String?,
)

sealed interface PetEventRemoteChange {
    data class Upsert(val dto: PetEventRemoteDto) : PetEventRemoteChange
    data class Remove(val id: String) : PetEventRemoteChange
}

@Singleton
class PetEventRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, eventId: String) =
        db.collection("families").document(familyId).collection("petEvents").document(eventId)

    fun listenPetEvents(
        familyId: String,
        onChange: (List<PetEventRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("petEvents")
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val changes = snap.documentChanges.mapNotNull { diff ->
                    val doc = diff.document
                    val d = doc.data ?: return@mapNotNull null
                    val title = (d["title"] as? String)?.trim().orEmpty()
                    if (title.isEmpty()) return@mapNotNull null
                    val petId = (d["petId"] as? String).orEmpty()
                    if (petId.isEmpty()) return@mapNotNull null
                    val eventTypeRaw = (d["eventTypeRaw"] as? String) ?: "other"
                    val dateMillis = (d["date"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                    val cost: Double? = when (val c = d["cost"]) {
                        is Double -> c
                        is Number -> c.toDouble()
                        else -> null
                    }
                    val dto = PetEventRemoteDto(
                        id = doc.id,
                        familyId = familyId,
                        petId = petId,
                        title = title,
                        eventTypeRaw = eventTypeRaw,
                        dateMillis = dateMillis,
                        nextDueDateMillis = (d["nextDueDate"] as? Timestamp)?.toDate()?.time,
                        notes = d["notes"] as? String,
                        vetName = d["vetName"] as? String,
                        cost = cost,
                        reminderEnabled = d["reminderEnabled"] as? Boolean ?: false,
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        createdBy = d["createdBy"] as? String,
                        updatedBy = d["updatedBy"] as? String,
                    )
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> PetEventRemoteChange.Upsert(dto)
                        DocumentChange.Type.REMOVED -> PetEventRemoteChange.Remove(doc.id)
                    }
                }
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertPetEventToFirestore(entity: PetEventEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val data = mutableMapOf<String, Any?>(
            "petId" to entity.petId,
            "title" to entity.title,
            "eventTypeRaw" to entity.eventType,
            "date" to millisToTimestamp(entity.date),
            "isDeleted" to false,
            "reminderEnabled" to entity.reminderEnabled,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) data["createdAt"] = FieldValue.serverTimestamp()
        data["nextDueDate"] = entity.nextDueDate?.let { millisToTimestamp(it) }
        data["notes"] = entity.notes
        data["vetName"] = entity.vetName
        data["cost"] = entity.cost
        if (isNew) data["createdBy"] = entity.createdBy.ifEmpty { uid }
        ref(entity.familyId, entity.id).set(data, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, eventId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, eventId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
