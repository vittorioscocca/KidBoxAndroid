package it.vittorioscocca.kidbox.data.location

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBGeofenceDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ri-registra le geofence di sistema dalla cache locale (Room), così il monitoraggio
 * arrivo/partenza non dipende dall'apertura della schermata Posizione.
 *
 * Viene invocato:
 *  - all'avvio dell'app ([it.vittorioscocca.kidbox.KidBoxApplication]),
 *  - dopo il riavvio del dispositivo ([it.vittorioscocca.kidbox.notifications.BootReceiver]),
 *    perché l'OS azzera le geofence registrate al reboot.
 */
@Singleton
class GeofenceMonitorRestorer @Inject constructor(
    private val geofenceDao: KBGeofenceDao,
    private val geofenceMonitor: GeofenceMonitorService,
    private val profileDao: KBUserProfileDao,
    private val familySessionPreferences: FamilySessionPreferences,
    private val auth: FirebaseAuth,
) {
    private companion object {
        const val TAG = "GeofenceRestorer"
    }

    /** @return true se almeno una geofence è stata (ri)registrata. */
    suspend fun restore(): Boolean {
        val familyId = familySessionPreferences.getActiveFamilyId().orEmpty()
        val uid = auth.currentUser?.uid.orEmpty()
        if (familyId.isBlank() || uid.isBlank()) {
            KBLog.app.debug("GeofenceRestorer: no active family/uid, skip", TAG)
            return false
        }
        val geofences = runCatching { geofenceDao.listByFamily(familyId) }.getOrDefault(emptyList())
        if (geofences.isEmpty()) {
            geofenceMonitor.removeAll()
            return false
        }
        val displayName = profileDao.getByUid(uid)
            ?.displayName?.trim()?.takeIf { it.isNotBlank() } ?: "Utente"
        KBLog.app.info("GeofenceRestorer: restoring count=${geofences.size} familyId=$familyId", TAG)
        geofenceMonitor.syncMonitoring(
            familyId = familyId,
            uid = uid,
            displayName = displayName,
            geofences = geofences,
        )
        return true
    }
}
