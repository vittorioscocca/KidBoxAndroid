package it.vittorioscocca.kidbox.notifications

import it.vittorioscocca.kidbox.util.KBLog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import it.vittorioscocca.kidbox.data.location.GeofenceMonitorEntryPoint
import it.vittorioscocca.kidbox.data.location.LocationSharingService
import it.vittorioscocca.kidbox.data.location.LocationSharingStateStore
import it.vittorioscocca.kidbox.data.location.LocationSharingWatchdogWorker
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AiScheduledNotificationsRestorer
import kotlinx.coroutines.launch

/**
 * Best-effort BOOT_COMPLETED receiver. AlarmManager alarms are cleared on reboot;
 * treatments are re-established when the app opens; vehicle deadline alarms are
 * rebuilt here from Room when an active family id is known.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // To-do, salute, terapie e Wallet: promemoria *del device*, quindi si
                // ri-armano dal registro locale e non da Room — rileggere Room
                // resusciterebbe anche gli elementi arrivati dal sync, che su questo
                // dispositivo non devono avvisare. Non dipende dalla famiglia attiva,
                // quindi va prima del guard qui sotto. Vedi [ReminderAlarmRegistry].
                runCatching {
                    EntryPointAccessors.fromApplication(appCtx, VehicleReminderEntryPoint::class.java)
                        .reminderAlarmRegistry()
                        .restoreAll()
                }

                val familyId = appCtx.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
                    .getString("active_family_id", null)
                if (familyId.isNullOrBlank()) {
                    KBLog.app.debug("BOOT_COMPLETED — no active family, skip vehicle alarms", "BootReceiver")
                    return@launch
                }
                // Il foreground service muore al reboot: se la condivisione posizione
                // era attiva, riavviala (BOOT_COMPLETED è esente dalle restrizioni di
                // avvio FGS da background) e ri-arma il watchdog.
                runCatching {
                    if (LocationSharingStateStore.shouldBeActive(appCtx)) {
                        LocationSharingService.start(
                            appCtx,
                            familyId,
                            LocationSharingStateStore.displayName(appCtx),
                        )
                        LocationSharingWatchdogWorker.enqueue(appCtx)
                    }
                }
                val ep = EntryPointAccessors.fromApplication(
                    appCtx,
                    VehicleReminderEntryPoint::class.java,
                )
                val dao = ep.vehicleDao()
                val sched = ep.vehicleDeadlineReminderScheduler()
                dao.listActiveByFamily(familyId).forEach { sched.syncVehicle(it) }
                val hpDao = ep.housePaymentDao()
                val hpSched = ep.housePaymentReminderScheduler()
                hpDao.listActiveByFamily(familyId).forEach { hpSched.syncPayment(it) }
                runCatching {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    if (uid.isNotEmpty()) {
                        val pwDao = ep.passwordEntryDao()
                        val pwCypher = ep.passwordCypher()
                        val pwSched = ep.passwordExpiryReminderScheduler()
                        pwDao.listVisibleForAutofill(familyId, uid).forEach { entry ->
                            val title = runCatching {
                                pwCypher.decrypt(entry.titleCipher, entry.familyId, entry.visibility, entry.createdBy, familyKeyUserId = uid)
                            }.getOrNull().orEmpty()
                            pwSched.sync(entry.id, entry.familyId, title, entry.expiresAtEpochMillis)
                        }
                    }
                }
                AiScheduledNotificationsRestorer.restoreAfterBoot(appCtx)
                // Le geofence di sistema vengono azzerate dall'OS al reboot: ri-registrale da Room.
                runCatching {
                    EntryPointAccessors.fromApplication(appCtx, GeofenceMonitorEntryPoint::class.java)
                        .geofenceMonitorRestorer()
                        .restore()
                }
                KBLog.app.debug("BOOT_COMPLETED — veicoli, pagamenti casa, password, AI, geofence e registro promemoria ripristinati", "BootReceiver")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
