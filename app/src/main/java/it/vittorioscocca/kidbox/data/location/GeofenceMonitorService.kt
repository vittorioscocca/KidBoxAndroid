package it.vittorioscocca.kidbox.data.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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

    /**
     * Registra/aggiorna le geofence di sistema per [uid] nella famiglia [familyId].
     *
     * Il monitoraggio è INDIPENDENTE dalla condivisione posizione live: una zona
     * "applicata" a una persona deve generare gli eventi arrivo/partenza anche quando
     * la condivisione live è spenta. Richiede solo ACCESS_BACKGROUND_LOCATION (verificato
     * qui internamente) perché il trigger arriva via broadcast ad app in background/chiusa.
     */
    fun syncMonitoring(
        familyId: String,
        uid: String,
        displayName: String,
        geofences: List<KBGeofenceEntity>,
    ) {
        if (familyId.isBlank() || uid.isBlank()) {
            removeAll()
            return
        }
        if (!hasBackgroundLocationPermission()) {
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
        // initialTrigger = 0: NON generare un ENTER sintetico se l'utente è già dentro
        // la zona al momento della registrazione. Senza questo, ogni ri-registrazione
        // (apertura schermata, update Firestore, watchdog ogni 15 min) faceva ripartire
        // un "è arrivato" per chi è fermo dentro la zona. Vogliamo solo gli attraversamenti reali.
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
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

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
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
