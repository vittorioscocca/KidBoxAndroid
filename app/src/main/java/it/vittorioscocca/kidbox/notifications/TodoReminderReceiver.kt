package it.vittorioscocca.kidbox.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)
        val todoId = intent.getStringExtra(EXTRA_TODO_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "To-Do" }
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
        val childId = intent.getStringExtra(EXTRA_CHILD_ID)
        val listId = intent.getStringExtra(EXTRA_LIST_ID)

        val deepLink = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("push_type", "todo_due_changed")
            putExtra("push_family_id", familyId)
            putExtra("push_child_id", childId)
            putExtra("push_list_id", listId)
            putExtra("push_todo_id", todoId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            todoId.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TODO_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Promemoria To-Do")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(todoId.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID_TODO_REMINDERS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_TODO_REMINDERS,
                "Promemoria To-Do",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Promemoria locali dei To-Do" },
        )
    }

    companion object {
        const val CHANNEL_ID_TODO_REMINDERS = "todo_reminders"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FAMILY_ID = "extra_family_id"
        const val EXTRA_CHILD_ID = "extra_child_id"
        const val EXTRA_LIST_ID = "extra_list_id"
    }
}
