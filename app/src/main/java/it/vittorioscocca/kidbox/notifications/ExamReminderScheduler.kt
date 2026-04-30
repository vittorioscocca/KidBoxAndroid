package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExamReminder(exam: KBMedicalExam, childName: String) {
        val deadline = exam.deadlineEpochMillis ?: return
        val fireAt = dayBeforeAt9(deadline)
        if (fireAt <= System.currentTimeMillis()) return

        val body = buildString {
            append("Esame per $childName: ${exam.name}")
            if (exam.isUrgent) append(" (urgente)")
        }
        val pi = buildPendingIntent(exam.id, body, exam.familyId, exam.childId)

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

    fun cancelExamReminder(examId: String) {
        val pi = buildPendingIntent(examId, "", "", "")
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun dayBeforeAt9(deadlineMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = deadlineMillis
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun buildPendingIntent(
        examId: String,
        body: String,
        familyId: String,
        childId: String,
    ): PendingIntent {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.health.exam_reminder.$examId"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_EXAM_REMINDER)
            putExtra(HealthReminderReceiver.EXTRA_EXAM_ID, examId)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, body)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_CHILD_ID, childId)
        }
        return PendingIntent.getBroadcast(
            context,
            ("exam:$examId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
