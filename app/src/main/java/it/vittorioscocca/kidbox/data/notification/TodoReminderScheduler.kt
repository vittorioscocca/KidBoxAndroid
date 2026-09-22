package it.vittorioscocca.kidbox.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.notifications.ReminderAlarmRegistry
import it.vittorioscocca.kidbox.notifications.TodoReminderReceiver
import it.vittorioscocca.kidbox.notifications.UrgentAlarmReceiver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Il promemoria di un to-do.
 *
 * Due strade, scelte da `isUrgent`:
 * - **urgente** → sveglia ([UrgentAlarmScheduler]): suona anche in silenzioso
 *   e in Non disturbare, come la sveglia dell'orologio;
 * - **normale** → notifica locale ([TodoReminderReceiver]).
 *
 * Si arma sempre una sola delle due: l'altra viene annullata, altrimenti un
 * to-do che smette di essere urgente continuerebbe a suonare.
 */
@Singleton
class TodoReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
    private val urgentAlarmScheduler: UrgentAlarmScheduler,
) {
    fun schedule(
        todoId: String,
        title: String,
        dueAtEpochMillis: Long,
        familyId: String,
        childId: String,
        listId: String?,
        isUrgent: Boolean = false,
    ): String {
        val triggerAt = dueAtEpochMillis.coerceAtLeast(System.currentTimeMillis() + 3_000L)

        if (isUrgent) {
            cancelPlainReminder(todoId)
            urgentAlarmScheduler.schedule(
                kind = UrgentAlarmReceiver.KIND_TODO,
                entityId = todoId,
                title = title,
                fireAtMillis = triggerAt,
                familyId = familyId,
                childId = childId,
                listId = listId,
            )
            return todoId
        }

        urgentAlarmScheduler.cancel(UrgentAlarmReceiver.KIND_TODO, todoId)
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

    /** Toglie notifica **e** sveglia: non si sa quale delle due fosse armata. */
    fun cancel(todoId: String?) {
        if (todoId.isNullOrBlank()) return
        urgentAlarmScheduler.cancel(UrgentAlarmReceiver.KIND_TODO, todoId)
        cancelPlainReminder(todoId)
    }

    private fun cancelPlainReminder(todoId: String) {
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
