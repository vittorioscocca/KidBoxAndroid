package it.vittorioscocca.kidbox.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.notifications.TodoReminderReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(
        todoId: String,
        title: String,
        dueAtEpochMillis: Long,
        familyId: String,
        childId: String,
        listId: String?,
    ): String {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = todoId.hashCode()
        val triggerAt = dueAtEpochMillis.coerceAtLeast(System.currentTimeMillis() + 3_000L)
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            putExtra(TodoReminderReceiver.EXTRA_TODO_ID, todoId)
            putExtra(TodoReminderReceiver.EXTRA_TITLE, title)
            putExtra(TodoReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(TodoReminderReceiver.EXTRA_CHILD_ID, childId)
            putExtra(TodoReminderReceiver.EXTRA_LIST_ID, listId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }.onFailure {
            // Final fallback for OEM restrictions / permission variance.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        return todoId
    }

    fun cancel(todoId: String?) {
        if (todoId.isNullOrBlank()) return
        val requestCode = todoId.hashCode()
        val intent = Intent(context, TodoReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
        pending.cancel()
    }
}
