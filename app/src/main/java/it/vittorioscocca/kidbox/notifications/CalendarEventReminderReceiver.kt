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
import it.vittorioscocca.kidbox.data.notification.CalendarEventReminderScheduler

/** L'avviso di un evento del calendario non urgente: una notifica normale. */
class CalendarEventReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { context.getString(R.string.calendar_event_reminder_fallback) }

        val deepLink = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("push_type", "new_calendar_event")
            putExtra("push_family_id", familyId)
            putExtra("push_event_id", eventId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CALENDAR_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.calendar_event_reminder_title))
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        }
        // Una serie arma subito la ripetizione successiva.
        CalendarEventReminderScheduler.rearmAfterFire(context, eventId)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID_CALENDAR_REMINDERS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_CALENDAR_REMINDERS,
                context.getString(R.string.calendar_event_reminder_title),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.calendar_event_reminder_channel_description) },
        )
    }

    companion object {
        const val CHANNEL_ID_CALENDAR_REMINDERS = "calendar_event_reminders"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FAMILY_ID = "extra_family_id"
    }
}
