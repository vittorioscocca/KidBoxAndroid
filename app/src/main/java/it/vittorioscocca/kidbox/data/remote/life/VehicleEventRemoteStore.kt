package it.vittorioscocca.kidbox.data.remote.life

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class VehicleEventRemoteDto(
    val id: String,
    val familyId: String,
    val vehicleId: String,
    val title: String,
    val eventTypeRaw: String,
    val dateMillis: Long,
    val km: Int?,
    val cost: Double?,
    val linkedExpenseId: String?,
    val garageName: String?,
    val notes: String?,
    val isDeleted: Boolean,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val createdBy: String?,
    val updatedBy: String?,
)

sealed interface VehicleEventRemoteChange {
    data class Upsert(val dto: VehicleEventRemoteDto) : VehicleEventRemoteChange
    data class Remove(val id: String) : VehicleEventRemoteChange
}

@Singleton
class VehicleEventRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, eventId: String) =
        db.collection("families").document(familyId).collection("vehicleEvents").document(eventId)

    fun listenVehicleEvents(
        familyId: String,
        onChange: (List<VehicleEventRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("vehicleEvents")
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
                    val vehicleId = (d["vehicleId"] as? String).orEmpty()
                    if (vehicleId.isEmpty()) return@mapNotNull null
                    val eventTypeRaw = (d["eventTypeRaw"] as? String) ?: "other"
                    val dateMillis = (d["date"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                    val km: Int? = when (val k = d["km"]) {
                        is Int -> k
                        is Number -> k.toInt()
                        else -> null
                    }
                    val cost: Double? = when (val c = d["cost"]) {
                        is Double -> c
                        is Number -> c.toDouble()
                        else -> null
                    }
                    val dto = VehicleEventRemoteDto(
                        id = doc.id,
                        familyId = familyId,
                        vehicleId = vehicleId,
                        title = title,
                        eventTypeRaw = eventTypeRaw,
                        dateMillis = dateMillis,
                        km = km,
                        cost = cost,
                        linkedExpenseId = d["linkedExpenseId"] as? String,
                        garageName = d["garageName"] as? String,
                        notes = d["notes"] as? String,
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        createdBy = d["createdBy"] as? String,
                        updatedBy = d["updatedBy"] as? String,
                    )
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> VehicleEventRemoteChange.Upsert(dto)
                        DocumentChange.Type.REMOVED -> VehicleEventRemoteChange.Remove(doc.id)
                    }
                }
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertVehicleEventToFirestore(entity: VehicleEventEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val data = mutableMapOf<String, Any?>(
            "vehicleId" to entity.vehicleId,
            "title" to entity.title,
            "eventTypeRaw" to entity.eventType,
            "date" to millisToTimestamp(entity.date),
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) data["createdAt"] = FieldValue.serverTimestamp()
        data["km"] = entity.km
        data["cost"] = entity.cost
        data["linkedExpenseId"] = entity.linkedExpenseId
        data["garageName"] = entity.garageName
        data["notes"] = entity.notes
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
