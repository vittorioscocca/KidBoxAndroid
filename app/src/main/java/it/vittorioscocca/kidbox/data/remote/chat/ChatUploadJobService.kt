package it.vittorioscocca.kidbox.data.remote.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import it.vittorioscocca.kidbox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Invii lunghi della chat (video, file grandi) che devono finire anche con l'app
 * in background: su Android 14+ si chiede un *user-initiated data transfer job*,
 * l'equivalente del `BGContinuedProcessingTask` di iOS. Il sistema tiene vivo il
 * processo e mostra la notifica con l'avanzamento; lo stop (dalla notifica o dal
 * Task Manager) equivale allo stop nell'anello della bolla.
 *
 * Il job non fa l'upload: quello gira già nel repository. Il job lo «tiene per mano»
 * finché [ChatUploadProgress] dice che è attivo, poi si chiude.
 *
 * Sotto Android 14 resta la protezione di base: l'upload gira nello scope
 * dell'app e non in quello della schermata.
 */
object ChatUploadJobs {
    /** Oltre questa taglia l'invio merita il job, come `longUploadBytes` su iOS. */
    const val LONG_UPLOAD_BYTES = 8L * 1024 * 1024

    internal const val EXTRA_MESSAGE_ID = "messageId"
    internal const val CHANNEL_ID = "chat_uploads"
    private const val NAMESPACE = "chat_upload"

    internal fun jobIdFor(messageId: String): Int = messageId.hashCode() and 0x7fffffff

    fun start(context: Context, messageId: String, bytes: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        startUserInitiated(context.applicationContext, messageId, bytes)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startUserInitiated(context: Context, messageId: String, bytes: Long) {
        // Namespace proprio: WorkManager usa lo stesso JobScheduler e i suoi id.
        val scheduler = context.getSystemService(JobScheduler::class.java)?.forNamespace(NAMESPACE) ?: return
        if (!scheduler.canRunUserInitiatedJobs()) return
        val info = JobInfo.Builder(jobIdFor(messageId), ComponentName(context, ChatUploadJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(0, bytes)
            .setExtras(PersistableBundle().apply { putString(EXTRA_MESSAGE_ID, messageId) })
            .build()
        // Va chiesto con l'app visibile: se il sistema rifiuta, resta la protezione di base.
        runCatching { scheduler.schedule(info) }
    }

    internal fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.chat_upload_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}

class ChatUploadJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val watchers = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val messageId = params.extras.getString(ChatUploadJobs.EXTRA_MESSAGE_ID) ?: return false
        // Invio già finito (o fermato) prima che il sistema avviasse il job.
        if (!ChatUploadProgress.isActive(messageId)) return false

        ChatUploadJobs.ensureChannel(this)
        val notificationId = params.jobId
        setNotification(
            params,
            notificationId,
            buildNotification(messageId, notificationId, ChatUploadProgress.state.value[messageId]),
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
        val manager = getSystemService(NotificationManager::class.java)
        watchers[params.jobId] = scope.launch {
            ChatUploadProgress.progressOf(messageId)
                // Una notifica per punto percentuale, non per ogni evento di Storage.
                .map { p -> p?.let { if (it < 0f) -1 else (it * 100).toInt() } }
                .distinctUntilChanged()
                .collect { percent ->
                    if (percent == null) {
                        watchers.remove(params.jobId)
                        jobFinished(params, false)
                        cancel()
                    } else {
                        runCatching {
                            manager?.notify(
                                notificationId,
                                buildNotification(messageId, notificationId, if (percent < 0) -1f else percent / 100f),
                            )
                        }
                    }
                }
        }
        return true
    }

    /** Stop dal Task Manager, o il sistema che deve fermarlo: come lo stop nell'anello. */
    override fun onStopJob(params: JobParameters): Boolean {
        watchers.remove(params.jobId)?.cancel()
        params.extras.getString(ChatUploadJobs.EXTRA_MESSAGE_ID)?.let { ChatUploadProgress.cancel(it) }
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(messageId: String, requestCode: Int, progress: Float?): android.app.Notification {
        val cancelIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, ChatUploadCancelReceiver::class.java)
                .putExtra(ChatUploadJobs.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, requestCode, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val indeterminate = progress == null || progress < 0f
        return NotificationCompat.Builder(this, ChatUploadJobs.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kidbox)
            .setContentTitle(getString(R.string.chat_uploading))
            .setContentText(getString(R.string.settings_messages_chat_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, if (indeterminate) 0 else ((progress ?: 0f) * 100).toInt(), indeterminate)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.chat_cancel_upload), cancelIntent)
            .build()
    }
}

/** Azione «Annulla invio» della notifica. */
class ChatUploadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(ChatUploadJobs.EXTRA_MESSAGE_ID)?.let { ChatUploadProgress.cancel(it) }
    }
}
