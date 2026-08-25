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

        val body = buildString {
            append(context.getString(R.string.exam_reminder_body_format, childName, exam.name))
            if (exam.isUrgent) append(context.getString(R.string.exam_reminder_body_urgent_suffix))
        }
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.examKey(exam.id),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = ("exam:${exam.id}").hashCode(),
                fireAtMillis = fireAt,
                action = examAction(exam.id),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_EXAM_REMINDER,
                    HealthReminderReceiver.EXTRA_EXAM_ID to exam.id,
                    HealthReminderReceiver.EXTRA_TITLE to body,
                    HealthReminderReceiver.EXTRA_FAMILY_ID to exam.familyId,
                    HealthReminderReceiver.EXTRA_CHILD_ID to exam.childId,
                ),
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
