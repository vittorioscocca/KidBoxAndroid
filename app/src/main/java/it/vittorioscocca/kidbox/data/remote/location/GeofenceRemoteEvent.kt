package it.vittorioscocca.kidbox.data.remote.location

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.util.KBLog
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

enum class GeofenceTransitionType(val raw: String) {
    ARRIVE("arrive"),
    LEAVE("leave"),
}

@Singleton
class GeofenceRemoteEvent @Inject constructor() {
    private companion object {
        const val TAG = "GeofenceRemoteEvent"
    }

    private val db get() = FirebaseFirestore.getInstance()

    suspend fun writeEvent(
        familyId: String,
        geofenceId: String,
        uid: String,
        displayName: String,
        type: GeofenceTransitionType,
    ) {
        val eventId = UUID.randomUUID().toString()
        val data = mapOf(
            "geofenceId" to geofenceId,
            "uid" to uid,
            "displayName" to displayName,
            "type" to type.raw,
            "timestamp" to FieldValue.serverTimestamp(),
        )
        runCatching {
            db.collection("families")
                .document(familyId)
                .collection("geofenceEvents")
                .document(eventId)
                .set(data)
                .await()
            KBLog.sync.info(
                "[GeofenceRemoteEvent] write OK eventId=$eventId familyId=$familyId geofenceId=$geofenceId type=${type.raw}",
                TAG,
            )
        }.onFailure { err ->
            KBLog.sync.error(
                "[GeofenceRemoteEvent] write failed familyId=$familyId geofenceId=$geofenceId: ${err.message}",
                TAG,
                err,
            )
        }
    }
}
