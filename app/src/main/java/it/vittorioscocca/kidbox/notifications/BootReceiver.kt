package it.vittorioscocca.kidbox.notifications

import it.vittorioscocca.kidbox.util.KBLog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
                val familyId = appCtx.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
                    .getString("active_family_id", null)
                if (familyId.isNullOrBlank()) {
                    KBLog.app.debug("BOOT_COMPLETED — no active family, skip vehicle alarms", "BootReceiver")
                    return@launch
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
                AiScheduledNotificationsRestorer.restoreAfterBoot(appCtx)
                KBLog.app.debug("BOOT_COMPLETED — vehicle, house payment, AI briefing alarms refreshed", "BootReceiver")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
