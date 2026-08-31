package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExamReminder(exam: KBMedicalExam, childName: String) {
        val deadline = exam.deadlineEpochMillis ?: return
        val fireAt = dayBeforeAt9(deadline)
        if (fireAt <= System.currentTimeMillis()) return

        // Chiave e argomenti al posto della frase: la compone il receiver quando
        // l'alarm scatta, nella lingua di allora (vedi KBNotificationText).
        val bodyKey = if (exam.isUrgent) {
            "exam_reminder_body_format_urgent"
        } else {
            "exam_reminder_body_format"
        }
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.examKey(exam.id),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = ("exam:${exam.id}").hashCode(),
                fireAtMillis = fireAt,
                action = examAction(exam.id),
                stringExtras = buildMap<String, String> {
                    put(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_EXAM_REMINDER)
                    put(HealthReminderReceiver.EXTRA_EXAM_ID, exam.id)
                    put(HealthReminderReceiver.EXTRA_FAMILY_ID, exam.familyId)
                    put(HealthReminderReceiver.EXTRA_CHILD_ID, exam.childId)
                    KBNotificationText.put(
                        this,
                        titleKey = "exam_reminder_notification_title",
                        bodyKey = bodyKey,
                        bodyArgs = listOf(childName, exam.name),
                    )
                },
            ),
        )
    }

    fun cancelExamReminder(examId: String) {
        alarmRegistry.forget(ReminderAlarmRegistry.examKey(examId))
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = examAction(examId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ("exam:$examId").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun examAction(examId: String) = "kb.health.exam_reminder.$examId"

    private fun dayBeforeAt9(deadlineMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = deadlineMillis
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

}
