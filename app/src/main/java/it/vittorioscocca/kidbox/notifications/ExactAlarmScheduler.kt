package it.vittorioscocca.kidbox.notifications

import it.vittorioscocca.kidbox.util.KBLog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Schedula sveglie alle 8:00 con allarme esatto quando consentito (Android 12+),
 * altrimenti usa [AlarmManager.setAndAllowWhileIdle] come fallback.
 */
object ExactAlarmScheduler {
    private const val TAG = "ExactAlarmScheduler"

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun requestExactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun scheduleRtcWakeupAllowWhileIdle(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val useExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            when {
                useExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    KBLog.app.info("exact alarms not granted, using inexact RTC_WAKEUP", TAG)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
                useExact -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
        } catch (security: SecurityException) {
            KBLog.app.error("alarm schedule denied, inexact fallback", TAG, security)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }
}
