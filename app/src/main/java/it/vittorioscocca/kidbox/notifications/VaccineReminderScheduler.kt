package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.data.local.mapper.displayTitle
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleVaccineReminder(vaccine: KBVaccine, childName: String) {
        if (!vaccine.reminderOn) return
        if (vaccine.statusRaw != "planned") return
        val target = vaccine.nextDoseDateEpochMillis ?: return
        if (vaccine.computedStatus() == KBVaccineStatus.ADMINISTERED) return
        val fireAt = dayBeforeAt9(target)
        if (fireAt <= System.currentTimeMillis()) return

        val body = "Vaccino per $childName: ${vaccine.displayTitle()}"
        val pi = buildPendingIntent(vaccine.id, body, vaccine.familyId, vaccine.childId)

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

    fun cancelVaccineReminder(vaccineId: String) {
        val pi = buildPendingIntent(vaccineId, "", "", "")
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun dayBeforeAt9(scheduledMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = scheduledMillis
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun buildPendingIntent(
        vaccineId: String,
        body: String,
        familyId: String,
        childId: String,
    ): PendingIntent {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.health.vaccine_reminder.$vaccineId"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_VACCINE_REMINDER)
            putExtra(HealthReminderReceiver.EXTRA_VACCINE_ID, vaccineId)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, body)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_CHILD_ID, childId)
        }
        return PendingIntent.getBroadcast(
            context,
            ("vaccine:$vaccineId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
