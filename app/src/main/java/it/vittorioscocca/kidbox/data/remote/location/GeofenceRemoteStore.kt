package it.vittorioscocca.kidbox.data.remote.location

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity
import it.vittorioscocca.kidbox.util.decodeStringList
import it.vittorioscocca.kidbox.util.encodeStringList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class GeofenceRemoteDto(
    val id: String,
    val familyId: String,
    val name: String,
    val emoji: String?,
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    val notifyOnArrive: Boolean,
    val notifyOnLeave: Boolean,
    val notifyMembers: List<String>,
    val monitoredMemberIds: List<String>,
    val isActive: Boolean,
    val createdBy: String?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val isDeleted: Boolean,
)

sealed interface GeofenceRemoteChange {
    data class Upsert(val dto: GeofenceRemoteDto) : GeofenceRemoteChange
    data class Remove(val id: String) : GeofenceRemoteChange
}

@Singleton
class GeofenceRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, geofenceId: String) =
        db.collection("families").document(familyId).collection("geofences").document(geofenceId)

    fun listenGeofences(
        familyId: String,
        onChange: (List<GeofenceRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("geofences")
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
                    val dto = decodeDto(doc.id, familyId, d) ?: return@mapNotNull null
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> GeofenceRemoteChange.Upsert(dto)
                        DocumentChange.Type.REMOVED -> GeofenceRemoteChange.Remove(doc.id)
                    }
                }
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertToFirestore(entity: KBGeofenceEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val notifyMembers = decodeStringList(entity.notifyMembersJson)
        val monitored = decodeStringList(entity.monitoredMemberIdsJson)
        val data = mutableMapOf<String, Any?>(
            "name" to entity.name,
            "latitude" to entity.latitude,
            "longitude" to entity.longitude,
            "radius" to entity.radius,
            "notifyOnArrive" to entity.notifyOnArrive,
            "notifyOnLeave" to entity.notifyOnLeave,
            "notifyMembers" to notifyMembers,
            "monitoredMemberIds" to monitored,
            "isActive" to entity.isActive,
            "isDeleted" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            data["createdAt"] = FieldValue.serverTimestamp()
            data["createdBy"] = entity.createdBy.ifEmpty { uid }
        }
        val emoji = entity.emoji?.trim().orEmpty()
        if (emoji.isNotEmpty()) {
            data["emoji"] = emoji
        } else {
            data["emoji"] = FieldValue.delete()
        }
        ref(entity.familyId, entity.id).set(data, SetOptions.merge()).await()
    }

    suspend fun softDelete(id: String, familyId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, id).set(
            mapOf(
                "isDeleted" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
            ),
            SetOptions.merge(),
        ).await()
    }

    private fun decodeDto(id: String, familyId: String, data: Map<String, Any>): GeofenceRemoteDto? {
        val name = (data["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val latitude = doubleValue(data["latitude"]) ?: doubleValue(data["lat"]) ?: return null
        val longitude = doubleValue(data["longitude"]) ?: doubleValue(data["lng"]) ?: return null
        val radius = doubleValue(data["radius"]) ?: 200.0
        return GeofenceRemoteDto(
            id = id,
            familyId = familyId,
            name = name,
            emoji = data["emoji"] as? String,
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            notifyOnArrive = data["notifyOnArrive"] as? Boolean ?: true,
            notifyOnLeave = data["notifyOnLeave"] as? Boolean ?: false,
            notifyMembers = stringList(data["notifyMembers"]),
            monitoredMemberIds = stringList(data["monitoredMemberIds"]),
            isActive = data["isActive"] as? Boolean ?: true,
            createdBy = data["createdBy"] as? String,
            createdAtMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time,
            updatedAtMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
        )
    }

    private fun doubleValue(any: Any?): Double? = when (any) {
        is Double -> any
        is Number -> any.toDouble()
        else -> null
    }

    private fun stringList(any: Any?): List<String> {
        if (any is List<*>) {
            return any.mapNotNull { it as? String }.filter { it.isNotBlank() }
        }
        return emptyList()
    }
}

fun geofenceEntityFromDto(dto: GeofenceRemoteDto, now: Long = System.currentTimeMillis()): KBGeofenceEntity =
    KBGeofenceEntity(
        id = dto.id,
        familyId = dto.familyId,
        name = dto.name,
        emoji = dto.emoji,
        latitude = dto.latitude,
        longitude = dto.longitude,
        radius = dto.radius,
        notifyOnArrive = dto.notifyOnArrive,
        notifyOnLeave = dto.notifyOnLeave,
        notifyMembersJson = encodeStringList(dto.notifyMembers),
        monitoredMemberIdsJson = encodeStringList(dto.monitoredMemberIds),
        isActive = dto.isActive,
        createdBy = dto.createdBy.orEmpty(),
        createdAtEpochMillis = dto.createdAtMillis ?: now,
        updatedAtEpochMillis = dto.updatedAtMillis ?: now,
        isDeleted = dto.isDeleted,
    )
