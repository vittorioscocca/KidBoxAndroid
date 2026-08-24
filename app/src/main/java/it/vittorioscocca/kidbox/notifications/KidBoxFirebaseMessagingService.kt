package it.vittorioscocca.kidbox.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KidBoxFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
                .await()
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val type = remoteMessage.data["type"]
            ?: remoteMessage.data["deep_link"]
            ?: remoteMessage.data["route"]
            ?: ""
        // L'utente è già dentro la sezione in cui è appena stato creato il
        // contenuto: niente notifica e niente badge, lo vede comparire da solo.
        // Qui si arriva solo con l'app in foreground — in background/killed la
        // notifica la mostra il sistema e questo codice non gira, ma in quel
        // caso l'utente per definizione non sta guardando nulla.
        if (isAlreadyOnScreen(type, remoteMessage.data)) return
        // Le menzioni hanno la stessa forma di un new_chat_message ma con un
        // titolo dedicato per dare priorità visiva al messaggio diretto.
        val senderName = remoteMessage.data["senderName"].orEmpty()
        // Tutte le push del server sono ormai messaggi dati puri (niente `notification`,
        // di proposito: vedi commento su `buildDataOnlyMessage` in functions/index.js),
        // quindi `remoteMessage.notification` è sempre null — titolo/corpo viaggiano
        // dentro `data`. Il fallback a `remoteMessage.notification` resta solo per le
        // poche push non ancora convertite (es. broadcast/nudge).
        val rawTitle = remoteMessage.data["title"] ?: remoteMessage.notification?.title
        val title = when {
            type == "chat_mention" && senderName.isNotBlank() -> "$senderName ti ha menzionato"
            type == "chat_mention" -> "Sei stato menzionato"
            // La chat non manda `title`/`body` in chiaro dentro `data`: il titolo va
            // ricavato dal solo `senderName`.
            type == "new_chat_message" && senderName.isNotBlank() -> senderName
            !rawTitle.isNullOrBlank() -> rawTitle
            else -> "KidBox"
        }
        val body = remoteMessage.data["body"]
            ?: remoteMessage.data["fallbackBody"]
            ?: remoteMessage.notification?.body
            ?: "Nuova notifica"
        showNotification(title, body, remoteMessage.data, type)
    }

    /**
     * True se la notifica riguarda la sezione che l'utente ha già davanti.
     *
     * I to-do sono gli unici con `scoped = true`: stare in una lista non deve
     * zittire le notifiche delle altre liste, quindi si sopprime solo quando
     * combacia anche il `listId`.
     *
     * I tipi non elencati (promemoria, menzioni di scadenza, annunci) non
     * vengono mai soppressi: non nascono da qualcosa che stai guardando
     * comparire, quindi vale la pena mostrarli comunque.
     */
    private fun isAlreadyOnScreen(type: String, data: Map<String, String>): Boolean {
        val familyId = data["familyId"]
        fun viewing(section: AppSection) =
            ScreenPresenceTracker.isViewing(section, familyId)

        return when (type) {
            // Le menzioni si vedono comunque a schermo come i messaggi normali.
            "new_chat_message", "chat_mention" -> viewing(AppSection.CHAT)
            "todo_assigned", "todo_reassigned", "todo_due_changed" ->
                ScreenPresenceTracker.isViewing(
                    section = AppSection.TODO_LIST,
                    familyId = familyId,
                    scopeId = data["listId"],
                    scoped = true,
                )
            "new_grocery_item" -> viewing(AppSection.SHOPPING_LIST)
            "new_calendar_event", "calendar_event" -> viewing(AppSection.CALENDAR)
            "new_note" -> viewing(AppSection.NOTES)
            "new_expense" -> viewing(AppSection.EXPENSES)
            "new_document" -> viewing(AppSection.DOCUMENTS)
            "new_wallet_ticket", "new_loyalty_card" -> viewing(AppSection.WALLET)
            "location_sharing_started", "location_sharing_stopped", "geofenceEvent" ->
                viewing(AppSection.FAMILY_LOCATION)
            else -> false
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>,
        type: String,
    ) {
        ensureChannel()
        val unreadCount = NotificationBadgeStore.increment(this)
        val deepLinkIntent = Intent(this, MainActivity::class.java).apply {
            // NEW_TASK è necessario perché il tap parte dal system tray, un contesto
            // non-Activity: senza, con l'app in background Android a volte apre un
            // SECONDO task con una MainActivity nuova invece di riportare avanti quello
            // esistente — sembra un riavvio completo dell'app e l'intent, finendo in una
            // istanza mai esistita prima, non passa da onNewIntent quindi non naviga.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra("push_type", type)
            putExtra("push_family_id", data["familyId"])
            putExtra("push_child_id", data["childId"])
            putExtra("push_list_id", data["listId"])
            putExtra("push_todo_id", data["todoId"])
            putExtra("push_item_id", data["itemId"] ?: data["docId"] ?: data["expenseId"])
            putExtra("push_doc_id", data["docId"])
            putExtra("push_note_id", data["noteId"])
            putExtra("push_expense_id", data["expenseId"])
            putExtra("push_event_id", data["eventId"])
            putExtra("push_visit_id", data["visitId"])
            putExtra("push_treatment_id", data["treatmentId"])
            putExtra("push_exam_id", data["examId"])
            putExtra("push_entry_id", data["entryId"])
            putExtra("ticketId", data["ticketId"])
            putExtra("cardId", data["cardId"])
            putExtra("push_deep_link", data["deep_link"] ?: data["route"])
            putExtra("push_message_id", data["messageId"])
            // Annunci dalla console admin: il testo integrale sta in `data`,
            // perché quello mostrato nella tendina è già troncato dal sistema.
            putExtra("push_broadcast_id", data["broadcastId"])
            putExtra("push_title", data["title"])
            putExtra("push_body", data["body"])
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID_FAMILY_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .build()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FAMILY_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(unreadCount)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun ensureChannel() = createNotificationChannels(this)

    companion object {
        const val CHANNEL_ID_FAMILY_UPDATES = "family_updates_v2"
        private const val CHANNEL_ID_LEGACY = "family_updates"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Rimuovi il vecchio canale che potrebbe avere lockscreenVisibility errata
            if (manager.getNotificationChannel(CHANNEL_ID_LEGACY) != null) {
                manager.deleteNotificationChannel(CHANNEL_ID_LEGACY)
            }
            if (manager.getNotificationChannel(CHANNEL_ID_FAMILY_UPDATES) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_FAMILY_UPDATES,
                    "Aggiornamenti Famiglia",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifiche su lista spesa e aggiornamenti condivisi"
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
        }
    }
}
