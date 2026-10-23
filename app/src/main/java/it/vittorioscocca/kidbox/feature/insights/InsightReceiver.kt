package it.vittorioscocca.kidbox.feature.insights

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.notifications.KidBoxFirebaseMessagingService
import it.vittorioscocca.kidbox.util.KBLog

/**
 * Mostra un avviso proattivo quando scatta l'allarme armato da [InsightRunner].
 *
 * Il testo si compone qui, alla consegna, con i dati negli extra: così è nella
 * lingua del telefono di quel momento, e l'allarme non ha bisogno del database
 * né della rete. Le azioni rapide passano da [InsightActionReceiver]; il tocco
 * usa gli stessi `push_type` delle notifiche dei to-do e delle cure, che il
 * router dei deep link sa già aprire.
 */
class InsightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW) return
        val insightId = intent.getStringExtra(EXTRA_INSIGHT_ID).orEmpty()
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
        if (insightId.isBlank()) return
        KidBoxFirebaseMessagingService.createNotificationChannels(context)

        val notificationId = insightId.hashCode()
        val builder = NotificationCompat.Builder(context, KidBoxFirebaseMessagingService.CHANNEL_ID_FAMILY_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(
                if (intent.getBooleanExtra(EXTRA_HIGH, false)) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )

        val tap = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("push_family_id", familyId)
        }

        when (intent.getStringExtra(EXTRA_TYPE)) {
            InsightType.TODO_UNASSIGNED_DUE_SOON.raw -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val due = intent.getLongExtra(EXTRA_DUE_AT, 0)
                val body = context.getString(
                    R.string.insight_todo_body, title, relativeDue(context, due, intent.getBooleanExtra(EXTRA_HAS_TIME, true)),
                )
                builder.setContentTitle(context.getString(R.string.insight_todo_title))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                tap.putExtra("push_type", "todo_reminder")
                    .putExtra("push_child_id", intent.getStringExtra(EXTRA_CHILD_ID))
                    .putExtra("push_list_id", intent.getStringExtra(EXTRA_LIST_ID))
                    .putExtra("push_todo_id", intent.getStringExtra(EXTRA_TODO_ID))
                // Android mostra al massimo tre pulsanti. Con qualcuno a cui
                // chiedere, «Chiedi agli altri» prende il posto di «Non mi interessa»:
                // è il pulsante che risolve, l'altro si trova nelle preferenze.
                builder.addAction(0, context.getString(R.string.insight_action_take), action(context, intent, InsightActionReceiver.ACTION_TAKE, 1))
                if (intent.getBooleanExtra(EXTRA_CAN_ASK, false)) {
                    builder.addAction(0, context.getString(R.string.insight_action_ask), action(context, intent, InsightActionReceiver.ACTION_ASK, 2))
                    builder.addAction(0, context.getString(R.string.insight_action_snooze), action(context, intent, InsightActionReceiver.ACTION_SNOOZE, 3))
                } else {
                    builder.addAction(0, context.getString(R.string.insight_action_snooze), action(context, intent, InsightActionReceiver.ACTION_SNOOZE, 3))
                    builder.addAction(0, context.getString(R.string.insight_action_dismiss), action(context, intent, InsightActionReceiver.ACTION_DISMISS, 4))
                }
            }
            InsightType.TREATMENT_DOSE_MISSING.raw -> {
                // Nessun dettaglio sanitario: di chi è la dose, non quale farmaco.
                val name = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
                val body = if (name.isBlank()) context.getString(R.string.insight_dose_body_generic)
                else context.getString(R.string.insight_dose_body, name)
                builder.setContentTitle(context.getString(R.string.insight_dose_title)).setContentText(body)
                tap.putExtra("push_type", "treatment_reminder")
                    .putExtra("push_child_id", intent.getStringExtra(EXTRA_CHILD_ID))
                    .putExtra("push_treatment_id", intent.getStringExtra(EXTRA_TREATMENT_ID))
                builder.addAction(0, context.getString(R.string.insight_action_dose_taken), action(context, intent, InsightActionReceiver.ACTION_DOSE_TAKEN, 1))
                builder.addAction(0, context.getString(R.string.insight_action_snooze), action(context, intent, InsightActionReceiver.ACTION_SNOOZE, 3))
                builder.addAction(0, context.getString(R.string.insight_action_dismiss), action(context, intent, InsightActionReceiver.ACTION_DISMISS, 4))
            }
            InsightType.HOUSE_PAYMENT_DUE.raw -> {
                val name = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val due = intent.getLongExtra(EXTRA_DUE_AT, 0)
                builder.setContentTitle(context.getString(R.string.insight_payment_title, name, relativeDue(context, due, false)))
                    .setContentText(context.getString(R.string.insight_payment_body))
                tap.putExtra("push_type", "house_payment_reminder")
                    .putExtra("push_house_payment_id", intent.getStringExtra(EXTRA_PAYMENT_ID))
                // Stessi pulsanti del to-do: «chi paga» è il to-do «Pagare: …».
                builder.addAction(0, context.getString(R.string.insight_action_take), action(context, intent, InsightActionReceiver.ACTION_TAKE, 1))
                if (intent.getBooleanExtra(EXTRA_CAN_ASK, false)) {
                    builder.addAction(0, context.getString(R.string.insight_action_ask), action(context, intent, InsightActionReceiver.ACTION_ASK, 2))
                    builder.addAction(0, context.getString(R.string.insight_action_snooze), action(context, intent, InsightActionReceiver.ACTION_SNOOZE, 3))
                } else {
                    builder.addAction(0, context.getString(R.string.insight_action_snooze), action(context, intent, InsightActionReceiver.ACTION_SNOOZE, 3))
                    builder.addAction(0, context.getString(R.string.insight_action_dismiss), action(context, intent, InsightActionReceiver.ACTION_DISMISS, 4))
                }
            }
            TYPE_DIGEST -> {
                val count = intent.getIntExtra(EXTRA_COUNT, 0)
                if (count <= 0) return
                builder.setContentTitle(context.getString(R.string.insight_digest_title))
                    .setContentText(context.getString(R.string.insight_digest_body, count))
            }
            else -> return
        }
        builder.setContentIntent(
            PendingIntent.getActivity(
                context, notificationId, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, builder.build()) }
            .onFailure { KBLog.app.warning("Insight non mostrato: ${it.message}", TAG) }
    }

    private fun action(context: Context, source: Intent, action: String, offset: Int): PendingIntent {
        val intent = Intent(context, InsightActionReceiver::class.java).apply {
            this.action = action
            putExtras(source)
        }
        val id = source.getStringExtra(EXTRA_INSIGHT_ID).orEmpty()
        return PendingIntent.getBroadcast(
            context, id.hashCode() * 31 + offset, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** «domani, 09:00» / «oggi». */
    private fun relativeDue(context: Context, due: Long, hasTime: Boolean): String {
        val day = DateUtils.getRelativeTimeSpanString(due, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
        if (!hasTime) return day
        val time = DateUtils.formatDateTime(context, due, DateUtils.FORMAT_SHOW_TIME)
        return "$day, $time"
    }

    companion object {
        private const val TAG = "Insight"
        const val ACTION_SHOW = "it.vittorioscocca.kidbox.INSIGHT_SHOW"
        const val TYPE_DIGEST = "digest"
        const val EXTRA_INSIGHT_ID = "kb.insight.id"
        const val EXTRA_TYPE = "kb.insight.type"
        const val EXTRA_FAMILY_ID = "kb.insight.familyId"
        const val EXTRA_HIGH = "kb.insight.high"
        const val EXTRA_AT = "kb.insight.at"
        const val EXTRA_TODO_ID = "kb.insight.todoId"
        const val EXTRA_CHILD_ID = "kb.insight.childId"
        const val EXTRA_LIST_ID = "kb.insight.listId"
        const val EXTRA_TITLE = "kb.insight.title"
        const val EXTRA_DUE_AT = "kb.insight.dueAt"
        const val EXTRA_HAS_TIME = "kb.insight.hasTime"
        const val EXTRA_CAN_ASK = "kb.insight.canAsk"
        const val EXTRA_TREATMENT_ID = "kb.insight.treatmentId"
        const val EXTRA_SUBJECT = "kb.insight.subject"
        const val EXTRA_COUNT = "kb.insight.count"
        const val EXTRA_PAYMENT_ID = "kb.insight.paymentId"
    }
}
