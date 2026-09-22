package it.vittorioscocca.kidbox.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLog

/**
 * La sveglia dei promemoria **urgenti**.
 *
 * Una notifica normale non suona col telefono in silenzioso né dentro una
 * modalità Non disturbare: è una notifica, e il sistema la tratta come tale.
 * Un promemoria segnato urgente promette l'opposto, quindi qui si usa tutto
 * ciò che Android riserva alle sveglie:
 *
 * - l'alarm è armato con `setAlarmClock` (vedi [ExactAlarmScheduler]), che è
 *   esente da Doze e mostra l'icona della sveglia nella barra di stato;
 * - il canale ha il suono di **sveglia** con `USAGE_ALARM`, la categoria
 *   `CATEGORY_ALARM` e prova a chiedere il salto del Non disturbare;
 * - la notifica ha un `fullScreenIntent`, così a schermo bloccato compare
 *   a tutto schermo invece di restare una riga in cima.
 *
 * Su iOS lo stesso lavoro lo fa AlarmKit (`KBUrgentAlarmService`).
 */
class UrgentAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)

        val kind = intent.getStringExtra(EXTRA_KIND).orEmpty().ifBlank { KIND_TODO }
        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { context.getString(R.string.todo_reminder_title_fallback) }
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()

        val deepLink = Intent(context, MainActivity::class.java).apply {
            // NEW_TASK perché si parte da un BroadcastReceiver; niente CLEAR_TOP,
            // che da app aperta ricreerebbe la MainActivity perdendo l'extra.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("push_family_id", familyId)
            if (kind == KIND_CALENDAR_EVENT) {
                putExtra("push_type", "new_calendar_event")
                putExtra("push_event_id", entityId)
            } else {
                putExtra("push_type", "todo_due_changed")
                putExtra("push_child_id", intent.getStringExtra(EXTRA_CHILD_ID))
                putExtra("push_list_id", intent.getStringExtra(EXTRA_LIST_ID))
                putExtra("push_todo_id", entityId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            entityId.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_URGENT_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.urgent_reminder_notification_title))
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // A schermo bloccato apre direttamente la schermata; sbloccato
            // Android la degrada da sé in un avviso in cima.
            .setFullScreenIntent(pendingIntent, true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(entityId.hashCode(), notification)
        }.onFailure {
            KBLog.app.error("sveglia non mostrata entityId=$entityId", TAG, it)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID_URGENT_REMINDERS) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID_URGENT_REMINDERS,
            context.getString(R.string.urgent_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.urgent_reminder_channel_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    // `USAGE_ALARM` è ciò che fa suonare il telefono anche col
                    // volume notifiche a zero: segue il volume della sveglia.
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            // Senza l'accesso alle policy di sistema questa riga viene ignorata
            // in silenzio: è un tentativo, non una garanzia. Il grosso del
            // lavoro lo fa comunque `USAGE_ALARM`, che il Non disturbare
            // lascia passare quando le sveglie sono consentite.
            runCatching { setBypassDnd(true) }
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "UrgentAlarmReceiver"
        const val CHANNEL_ID_URGENT_REMINDERS = "urgent_reminders"
        const val KIND_TODO = "todo"
        const val KIND_CALENDAR_EVENT = "calendarEvent"
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_ENTITY_ID = "extra_entity_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FAMILY_ID = "extra_family_id"
        const val EXTRA_CHILD_ID = "extra_child_id"
        const val EXTRA_LIST_ID = "extra_list_id"
    }
}
