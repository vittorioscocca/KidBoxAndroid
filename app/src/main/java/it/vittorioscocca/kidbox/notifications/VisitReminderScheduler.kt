package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (or cancels) an AlarmManager alarm that fires [HealthReminderReceiver]
 * 1 day before a visit at 09:00.
 */
@Singleton
class VisitReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule a reminder for the given visit.
     * The alarm fires at 09:00, exactly 1 day before [visitDateMillis].
     * If the computed fire time is in the past the alarm is not set.
     *
     * @param reminderKey Unique string key for this alarm (e.g. "{visitId}_reminder").
     * @param visitDateMillis The epoch millis of the visit date.
     * @param title Notification title.
     * @param visitId Visit document id.
     * @param familyId Family id (for deep-link).
     * @param childId Child id (for deep-link).
     */
    fun schedule(
        reminderKey: String,
        visitDateMillis: Long,
        title: String,
        visitId: String,
        familyId: String,
        childId: String,
    ) {
        val fireAt = dayBeforeAt9(visitDateMillis)
        if (fireAt <= System.currentTimeMillis()) return

        val pi = buildPendingIntent(reminderKey, visitId, familyId, childId, title)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
    }

    /** Cancel the alarm for [reminderKey]. Safe to call even if no alarm exists. */
    fun cancel(reminderKey: String, visitId: String) {
        val pi = buildPendingIntent(reminderKey, visitId, "", "", "")
        alarmManager.cancel(pi)
        pi.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dayBeforeAt9(visitDateMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = visitDateMillis
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun buildPendingIntent(
        reminderKey: String,
        visitId: String,
        familyId: String,
        childId: String,
        title: String,
    ): PendingIntent {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            putExtra(HealthReminderReceiver.EXTRA_VISIT_ID, visitId)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, title)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_CHILD_ID, childId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
