package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.data.local.mapper.displayTitle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleVaccineReminder(vaccine: KBVaccine, childName: String) {
        if (!vaccine.reminderOn) return
        if (vaccine.statusRaw != "planned") return
        val target = vaccine.nextDoseDateEpochMillis ?: return
        if (vaccine.computedStatus() == KBVaccineStatus.ADMINISTERED) return
        val fireAt = dayBeforeAt9(target)
        if (fireAt <= System.currentTimeMillis()) return

        val body = context.getString(R.string.vaccine_reminder_body_format, childName, vaccine.displayTitle())
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.vaccineKey(vaccine.id),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = ("vaccine:${vaccine.id}").hashCode(),
                fireAtMillis = fireAt,
                action = vaccineAction(vaccine.id),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_VACCINE_REMINDER,
                    HealthReminderReceiver.EXTRA_VACCINE_ID to vaccine.id,
                    HealthReminderReceiver.EXTRA_TITLE to body,
                    HealthReminderReceiver.EXTRA_FAMILY_ID to vaccine.familyId,
                    HealthReminderReceiver.EXTRA_CHILD_ID to vaccine.childId,
                ),
            ),
        )
    }

    fun cancelVaccineReminder(vaccineId: String) {
        alarmRegistry.forget(ReminderAlarmRegistry.vaccineKey(vaccineId))
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = vaccineAction(vaccineId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ("vaccine:$vaccineId").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun vaccineAction(vaccineId: String) = "kb.health.vaccine_reminder.$vaccineId"

    private fun dayBeforeAt9(scheduledMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = scheduledMillis
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

}
