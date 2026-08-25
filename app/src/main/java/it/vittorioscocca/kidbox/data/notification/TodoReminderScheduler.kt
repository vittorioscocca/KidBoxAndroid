package it.vittorioscocca.kidbox.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.notifications.ReminderAlarmRegistry
import it.vittorioscocca.kidbox.notifications.TodoReminderReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    fun schedule(
        todoId: String,
        title: String,
        dueAtEpochMillis: Long,
        familyId: String,
        childId: String,
        listId: String?,
    ): String {
        val triggerAt = dueAtEpochMillis.coerceAtLeast(System.currentTimeMillis() + 3_000L)
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.todoKey(todoId),
                target = ReminderAlarmRegistry.Target.TODO,
                requestCode = todoId.hashCode(),
                fireAtMillis = triggerAt,
                stringExtras = mapOf(
                    TodoReminderReceiver.EXTRA_TODO_ID to todoId,
                    TodoReminderReceiver.EXTRA_TITLE to title,
                    TodoReminderReceiver.EXTRA_FAMILY_ID to familyId,
                    TodoReminderReceiver.EXTRA_CHILD_ID to childId,
                    TodoReminderReceiver.EXTRA_LIST_ID to listId,
                ),
            ),
        )
        return todoId
    }

    fun cancel(todoId: String?) {
        if (todoId.isNullOrBlank()) return
        alarmRegistry.forget(ReminderAlarmRegistry.todoKey(todoId))
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
