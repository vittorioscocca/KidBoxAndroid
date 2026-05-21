package it.vittorioscocca.kidbox.data.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.decodeStringList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class GeofenceMonitorService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "GeofenceMonitor"
        const val MAX_RADIUS_METERS = 100_000f
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun syncMonitoring(
        familyId: String,
        uid: String,
        displayName: String,
        isSharing: Boolean,
        geofences: List<KBGeofenceEntity>,
        hasBackgroundLocation: Boolean,
    ) {
        if (!isSharing || familyId.isBlank() || uid.isBlank()) {
            removeAll()
            return
        }
        if (!hasBackgroundLocation) {
            KBLog.app.warning(
                "GeofenceMonitor: ACCESS_BACKGROUND_LOCATION required for geofence monitoring",
                TAG,
            )
            removeAll()
            return
        }
        val active = geofences.filter { g ->
            g.familyId == familyId &&
                g.isActive &&
                !g.isDeleted &&
                geofenceAppliesToUser(g, uid)
        }
        val geofenceList = active.mapNotNull { toAndroidGeofence(it) }
        if (geofenceList.isEmpty()) {
            removeAll()
            return
        }
        GeofenceMonitorState.save(context, familyId, uid, displayName)
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()
        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener {
                    KBLog.app.info(
                        "GeofenceMonitor: registered count=${geofenceList.size} familyId=$familyId",
                        TAG,
                    )
                }
                .addOnFailureListener { err ->
                    KBLog.app.error(
                        "GeofenceMonitor: addGeofences failed familyId=$familyId: ${err.message}",
                        TAG,
                        err,
                    )
                }
        }
    }

    fun removeAll() {
        GeofenceMonitorState.clear(context)
        geofencingClient.removeGeofences(pendingIntent)
    }

    private fun geofenceAppliesToUser(geofence: KBGeofenceEntity, uid: String): Boolean {
        val monitored = decodeStringList(geofence.monitoredMemberIdsJson)
        if (monitored.isEmpty()) return true
        return monitored.contains(uid)
    }

    private fun toAndroidGeofence(entity: KBGeofenceEntity): Geofence? {
        if (entity.latitude == 0.0 && entity.longitude == 0.0) return null
        val radiusMeters = min(
            (if (entity.radius > 0) entity.radius else 200.0).toFloat(),
            MAX_RADIUS_METERS,
        )
        var transitions = 0
        if (entity.notifyOnArrive) transitions = transitions or Geofence.GEOFENCE_TRANSITION_ENTER
        if (entity.notifyOnLeave) transitions = transitions or Geofence.GEOFENCE_TRANSITION_EXIT
        if (transitions == 0) return null
        return Geofence.Builder()
            .setRequestId(entity.id)
            .setCircularRegion(entity.latitude, entity.longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            .build()
    }
}
