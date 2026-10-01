package it.vittorioscocca.kidbox.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequestRemoteStore
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Azione «Non posso» sulla notifica di una richiesta di famiglia, in
 * background. Se la risposta non parte, una notifica lo dice e il suo tap
 * riapre la richiesta: una risposta non deve mai perdersi in silenzio.
 * Gemello di `FamilyRequestActionHandler` su iOS.
 */
class FamilyRequestActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE) return
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        if (familyId.isBlank() || requestId.isBlank()) return

        // La notifica sparisce subito: l'utente ha già scelto.
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FamilyRequestRemoteStore.respond(familyId, requestId, yes = false)
            } catch (e: Exception) {
                KBLog.app.warning("FamilyRequestActionReceiver: risposta non inviata requestId=$requestId", TAG)
                postRetryNotice(appContext, familyId, requestId, body)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postRetryNotice(context: Context, familyId: String, requestId: String, body: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("push_type", TYPE_NEW)
            putExtra("push_family_id", familyId)
            putExtra("push_request_id", requestId)
        }
        val pending = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (body.isBlank()) {
            context.getString(R.string.requests_retry_body_short)
        } else {
            context.getString(R.string.requests_retry_body, body)
        }
        KidBoxFirebaseMessagingService.createNotificationChannels(context)
        val notification = NotificationCompat.Builder(context, KidBoxFirebaseMessagingService.CHANNEL_ID_FAMILY_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.requests_retry_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(requestId.hashCode(), notification) }
    }

    companion object {
        private const val TAG = "FamilyRequestAction"
        /** `data.type` della push di una richiesta nuova (functions/familyRequests.js). */
        const val TYPE_NEW = "family_request"
        const val ACTION_DECLINE = "it.vittorioscocca.kidbox.FAMILY_REQUEST_DECLINE"
        private const val EXTRA_FAMILY_ID = "familyId"
        private const val EXTRA_REQUEST_ID = "requestId"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val EXTRA_BODY = "body"

        fun declineIntent(
            context: Context,
            familyId: String,
            requestId: String,
            notificationId: Int,
            body: String,
        ): PendingIntent {
            val intent = Intent(context, FamilyRequestActionReceiver::class.java).apply {
                action = ACTION_DECLINE
                putExtra(EXTRA_FAMILY_ID, familyId)
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_BODY, body)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
