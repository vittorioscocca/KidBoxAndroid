package it.vittorioscocca.kidbox.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.dao.KBGeofenceDao
import it.vittorioscocca.kidbox.data.remote.location.GeofenceRemoteEvent
import it.vittorioscocca.kidbox.data.remote.location.GeofenceTransitionType
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GeofenceTransitionReceiver : BroadcastReceiver() {
    private companion object {
        const val TAG = "GeofenceTransitionRx"
    }

    @Inject lateinit var geofenceDao: KBGeofenceDao
    @Inject lateinit var remoteEvent: GeofenceRemoteEvent

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            val error = event?.errorCode ?: -1
            KBLog.app.error("GeofenceTransitionReceiver: error=$error", TAG)
            return
        }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) return

        val familyId = GeofenceMonitorState.familyId(context) ?: return
        val uid = GeofenceMonitorState.uid(context) ?: return
        val displayName = GeofenceMonitorState.displayName(context)

        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransitionType.ARRIVE
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceTransitionType.LEAVE
            else -> return
        }

        val pending = goAsync()
        scope.launch {
            try {
                for (triggering in event.triggeringGeofences.orEmpty()) {
                    val geofenceId = triggering.requestId
                    val entity = geofenceDao.getById(geofenceId) ?: continue
                    when (type) {
                        GeofenceTransitionType.ARRIVE -> if (!entity.notifyOnArrive) continue
                        GeofenceTransitionType.LEAVE -> if (!entity.notifyOnLeave) continue
                    }
                    KBLog.app.info(
                        "GeofenceTransitionReceiver: ${type.raw} id=$geofenceId familyId=$familyId",
                        TAG,
                    )
                    remoteEvent.writeEvent(
                        familyId = familyId,
                        geofenceId = geofenceId,
                        uid = uid,
                        displayName = displayName,
                        type = type,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
