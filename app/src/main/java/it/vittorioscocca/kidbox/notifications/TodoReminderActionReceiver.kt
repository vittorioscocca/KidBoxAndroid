package it.vittorioscocca.kidbox.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import it.vittorioscocca.kidbox.util.KBLog
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * **Fatto** sul promemoria di un to-do, normale o urgente: lo chiude senza
 * aprire l'app. Gemello di `TodoReminderDoneAction` su iOS.
 *
 * Le notifiche dei promemoria le costruisce il telefono, quindi il pulsante c'è
 * anche ad app chiusa. Ad app chiusa però la sessione Firebase può non essere
 * ancora pronta, e la si aspetta prima di toccare qualsiasi dato.
 */
class TodoReminderActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TodoActionEntryPoint {
        fun todoRepository(): TodoRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val todoId = intent.getStringExtra(EXTRA_TODO_ID).orEmpty()
        if (todoId.isBlank()) return

        // La notifica sparisce subito: l'utente ha già scelto.
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, todoId.hashCode())
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (awaitSignedIn() == null) error("Sessione non pronta")
                val repository = EntryPointAccessors
                    .fromApplication(appContext, TodoActionEntryPoint::class.java)
                    .todoRepository()
                withTimeout(WRITE_TIMEOUT_MS) { repository.markTodoDoneFromReminder(todoId) }
                KBLog.app.info("to-do chiuso dal promemoria id=$todoId", TAG)
            } catch (_: TimeoutCancellationException) {
                // Senza rete: la scrittura è in coda su Firestore e partirà da sola.
                KBLog.app.info("to-do chiuso dal promemoria, invio in coda id=$todoId", TAG)
            } catch (e: Exception) {
                KBLog.app.error("«Fatto» non riuscito id=$todoId", TAG, e)
                showRetryNotice(appContext, intent, todoId, notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun awaitSignedIn(): String? {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.uid?.let { return it }
        return withTimeoutOrNull(AUTH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : FirebaseAuth.AuthStateListener {
                    override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                        val uid = firebaseAuth.currentUser?.uid ?: return
                        firebaseAuth.removeAuthStateListener(this)
                        if (continuation.isActive) continuation.resume(uid)
                    }
                }
                auth.addAuthStateListener(listener)
                continuation.invokeOnCancellation { auth.removeAuthStateListener(listener) }
            }
        }
    }

    /** Un «Fatto» non deve perdersi in silenzio: l'avviso riapre il to-do. */
    private fun showRetryNotice(context: Context, source: Intent, todoId: String, notificationId: Int) {
        TodoReminderReceiver.ensureChannel(context)
        val title = source.getStringExtra(EXTRA_TITLE).orEmpty()
        val deepLink = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("push_type", "todo_due_changed")
            putExtra("push_family_id", source.getStringExtra(EXTRA_FAMILY_ID).orEmpty())
            putExtra("push_child_id", source.getStringExtra(EXTRA_CHILD_ID))
            putExtra("push_list_id", source.getStringExtra(EXTRA_LIST_ID))
            putExtra("push_todo_id", todoId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (title.isBlank()) {
            context.getString(R.string.requests_retry_body_short)
        } else {
            context.getString(R.string.requests_retry_body, title)
        }
        val notification = NotificationCompat.Builder(context, TodoReminderReceiver.CHANNEL_ID_TODO_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.todo_reminder_done_failed_title))
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    companion object {
        private const val TAG = "TodoReminderAction"
        private const val ACTION_DONE = "it.vittorioscocca.kidbox.TODO_REMINDER_DONE"
        private const val EXTRA_TODO_ID = "extra_todo_id"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_FAMILY_ID = "extra_family_id"
        private const val EXTRA_CHILD_ID = "extra_child_id"
        private const val EXTRA_LIST_ID = "extra_list_id"
        private const val AUTH_TIMEOUT_MS = 8_000L
        private const val WRITE_TIMEOUT_MS = 10_000L

        /** Il pulsante da aggiungere alla notifica; `null` se manca il to-do. */
        fun doneAction(
            context: Context,
            todoId: String,
            notificationId: Int,
            title: String,
            familyId: String,
            childId: String?,
            listId: String?,
        ): NotificationCompat.Action? {
            if (todoId.isBlank()) return null
            val intent = Intent(context, TodoReminderActionReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_TODO_ID, todoId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FAMILY_ID, familyId)
                putExtra(EXTRA_CHILD_ID, childId)
                putExtra(EXTRA_LIST_ID, listId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action(0, context.getString(R.string.todo_reminder_action_done), pendingIntent)
        }
    }
}
