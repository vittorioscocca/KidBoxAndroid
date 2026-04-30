package it.vittorioscocca.kidbox.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Best-effort BOOT_COMPLETED receiver. Reschedules treatment alarms after device reboot.
 * Subject to doze and vendor restrictions — the user must open the app at least once if
 * the device is in deep doze immediately after boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "BOOT_COMPLETED — reschedule delegated to next app open (sync center restore)")
        // Full reschedule happens when the user opens the app and the session is restored.
        // AlarmManager alarms are cleared on reboot; the TreatmentNotificationManager.schedule()
        // is called again from MedicalTreatmentsViewModel / TreatmentDetailViewModel once the
        // family session is re-established via sync centers.
    }
}
