package it.vittorioscocca.kidbox.data.remote.location

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteSharedLocationDto(
    val id: String,
    val isSharing: Boolean,
    val name: String,
    val modeRaw: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val startedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val lastUpdateAtEpochMillis: Long?,
    val avatarUrl: String?,
)

@Singleton
class LocationRemoteStore @Inject constructor() {
    private val firestore get() = FirebaseFirestore.getInstance()

    fun listen(
        familyId: String,
        onChange: (List<RemoteSharedLocationDto>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = firestore
        .collection("families")
        .document(familyId)
        .collection("locations")
        .addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            val docs = snap?.documents.orEmpty()
            onChange(
                docs.map { doc ->
                    val data = doc.data.orEmpty()
                    RemoteSharedLocationDto(
                        id = doc.id,
                        isSharing = data["isSharing"] as? Boolean ?: false,
                        name = data["name"] as? String ?: "",
                        modeRaw = data["mode"] as? String ?: "realtime",
                        latitude = data.numberOrNull("lat")?.toDouble(),
                        longitude = data.numberOrNull("lon")?.toDouble(),
                        accuracyMeters = data.numberOrNull("accuracy")?.toDouble(),
                        startedAtEpochMillis = data.timestampOrNull("startedAt"),
                        expiresAtEpochMillis = data.timestampOrNull("expiresAt"),
                        lastUpdateAtEpochMillis = data.timestampOrNull("lastUpdateAt"),
                        avatarUrl = data["avatarURL"] as? String,
                    )
                },
            )
        }

    suspend fun startSharing(
        familyId: String,
        uid: String,
        displayName: String,
        modeRaw: String,
        expiresAtEpochMillis: Long?,
    ) {
        val data = mutableMapOf<String, Any?>(
            "isSharing" to true,
            "mode" to modeRaw,
            "name" to displayName,
            "startedAt" to FieldValue.serverTimestamp(),
            "lastUpdateAt" to FieldValue.serverTimestamp(),
        )
        if (expiresAtEpochMillis != null) {
            data["expiresAt"] = com.google.firebase.Timestamp(expiresAtEpochMillis / 1000, 0)
        } else {
            data["expiresAt"] = FieldValue.delete()
        }
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun updateLocation(
        familyId: String,
        uid: String,
        lat: Double,
        lon: Double,
        accuracy: Double?,
        displayName: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(
                mapOf(
                    "lat" to lat,
                    "lon" to lon,
                    "accuracy" to accuracy,
                    "name" to displayName,
                    "lastUpdateAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun stopSharing(
        familyId: String,
        uid: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(
                mapOf(
                    "isSharing" to false,
                    "lastUpdateAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun updateDisplayName(
        familyId: String,
        uid: String,
        displayName: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(
                mapOf(
                    "name" to displayName,
                    "lastUpdateAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun Map<String, Any>.numberOrNull(key: String): Number? = this[key] as? Number

    private fun Map<String, Any>.timestampOrNull(key: String): Long? =
        (this[key] as? com.google.firebase.Timestamp)?.toDate()?.time
}
