package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
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
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule a reminder for the given visit.
     * The alarm fires at 09:00, exactly 1 day before [visitDateMillis].
     * If the computed fire time is in the past the alarm is not set.
     *
     * @param reminderKey Unique string key for this alarm (e.g. "{visitId}_reminder").
     * @param visitDateMillis The epoch millis of the visit date.
     * @param title Notification title (visit reason, un-prefixed).
     * @param visitId Visit document id.
     * @param familyId Family id (for deep-link).
     * @param childId Child id (for deep-link).
     * @param isNextVisit If true, prefixes [title] with the localized "Next visit: " label.
     */
    fun schedule(
        reminderKey: String,
        visitDateMillis: Long,
        title: String,
        visitId: String,
        familyId: String,
        childId: String,
        isNextVisit: Boolean = false,
    ) {
        val fireAt = dayBeforeAt9(visitDateMillis)
        if (fireAt <= System.currentTimeMillis()) return

        val resolvedTitle = if (isNextVisit) context.getString(R.string.visit_next_reminder_title, title) else title

        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.visitKey(reminderKey),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = reminderKey.hashCode(),
                fireAtMillis = fireAt,
                action = visitAction(reminderKey),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_VISIT_REMINDER,
                    HealthReminderReceiver.EXTRA_VISIT_ID to visitId,
                    HealthReminderReceiver.EXTRA_TITLE to resolvedTitle,
                    HealthReminderReceiver.EXTRA_FAMILY_ID to familyId,
                    HealthReminderReceiver.EXTRA_CHILD_ID to childId,
                ),
            ),
        )
    }

    /** Cancel the alarm for [reminderKey]. Safe to call even if no alarm exists. */
    fun cancel(reminderKey: String, visitId: String) {
        alarmRegistry.forget(ReminderAlarmRegistry.visitKey(reminderKey))
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = visitAction(reminderKey)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reminderKey.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun visitAction(reminderKey: String) = "kb.health.visit_reminder.$reminderKey"

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

}
