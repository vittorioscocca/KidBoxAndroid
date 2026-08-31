package it.vittorioscocca.kidbox.notifications.nudge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.notifications.KBNotificationText
import it.vittorioscocca.kidbox.notifications.KidBoxFirebaseMessagingService
import it.vittorioscocca.kidbox.util.KBLog

/**
 * Mostra un nudge quando scatta l'allarme pianificato da [NudgeEngine].
 *
 * Il testo viaggia negli extra dell'allarme e non viene riletto da nessuna
 * parte al momento della consegna: l'allarme deve poter scattare ad app chiusa,
 * senza database aperto e senza rete. È la stessa proprietà che rende utile
 * tutto l'impianto — raggiungere chi non torna.
 */
class NudgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val campaignId = intent.getStringExtra(EXTRA_CAMPAIGN_ID).orEmpty()
        val title = KBNotificationText.title(context, intent)
            ?: intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = KBNotificationText.body(context, intent)
            ?: intent.getStringExtra(EXTRA_BODY).orEmpty()
        val destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty()
        if (campaignId.isBlank() || body.isBlank()) {
            KBLog.app.warning("Nudge senza contenuto, ignorato", TAG)
            return
        }

        KidBoxFirebaseMessagingService.createNotificationChannels(context)

        // Stesse chiavi `push_*` che usa il router per i deep link push: il tap
        // su un nudge e il tap su un annuncio devono finire nello stesso posto.
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            // NEW_TASK necessario: parte da un BroadcastReceiver, un contesto non-Activity.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra("push_type", "nudge")
            putExtra("push_campaign_id", campaignId)
            putExtra("push_title", title)
            putExtra("push_body", body)
            putExtra("push_destination", destination)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            campaignId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat
            .Builder(context, KidBoxFirebaseMessagingService.CHANNEL_ID_FAMILY_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { context.getString(R.string.nudge_notification_title_fallback) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // Priorità normale, non alta: un suggerimento non è un promemoria.
            // Alzarla lo farebbe comparire come heads-up sopra quello che
            // l'utente sta facendo, ed è il modo più rapido per farsi togliere
            // il permesso.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(campaignId.hashCode(), notification)
        }.onFailure {
            // POST_NOTIFICATIONS revocato fra pianificazione e consegna.
            KBLog.app.warning("Nudge non mostrato: ${it.message}", TAG)
        }
    }

    companion object {
        private const val TAG = "Nudge"
        const val EXTRA_CAMPAIGN_ID = "kb.nudge.campaignId"
        const val EXTRA_TITLE = "kb.nudge.title"
        const val EXTRA_BODY = "kb.nudge.body"
        const val EXTRA_DESTINATION = "kb.nudge.destination"
    }
}
