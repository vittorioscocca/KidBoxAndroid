package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.ai.DailyBriefingPrefs
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
import it.vittorioscocca.kidbox.notifications.ExactAlarmScheduler
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

object DailyBriefingService {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val PREF_LAST_ISO_DATE = "kb_dailyBriefing_lastISODate"
    private const val PREF_LAST_TEXT = "kb_dailyBriefing_lastText"
    private const val WORK_NAME = "kb_daily_briefing_generation"
    private const val WORK_TAG = "kb_daily_briefing"
    private const val KEY_FAMILY_ID = "familyId"
    private const val KEY_FAMILY_NAME = "familyName"

    fun scheduleDailyIfNeeded(context: Context, familyId: String) {
        if (familyId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DailyBriefingPrefs.WORKER_KEY_ENABLED, true)) return
        if (prefs.getString(PREF_LAST_ISO_DATE, null) == todayIsoDate()) return

        val familyName = prefs.getString("active_family_name", context.getString(R.string.ai_your_family)) ?: context.getString(R.string.ai_your_family)
        val request = OneTimeWorkRequestBuilder<DailyBriefingWorker>()
            .setInputData(
                Data.Builder()
                    .putString(KEY_FAMILY_ID, familyId)
                    .putString(KEY_FAMILY_NAME, familyName)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun todayIsoDate(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(System.currentTimeMillis())
    }

    fun scheduleDaily8amNotification(
        context: Context,
        familyId: String,
        familyName: String,
        briefingText: String,
    ) {
        ensureChannel(context)
        val triggerAt = nextTodayOrTomorrowAt8am()
        val intent = Intent(context, DailyBriefingBroadcastReceiver::class.java).apply {
            action = DailyBriefingBroadcastReceiver.ACTION_DAILY_BRIEFING
            putExtra("familyId", familyId)
            putExtra("familyName", familyName)
            putExtra("fullText", briefingText.take(500))
            putExtra("type", "daily_briefing")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("daily:$familyId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ExactAlarmScheduler.scheduleRtcWakeupAllowWhileIdle(context, triggerAt, pendingIntent)
    }

    private fun nextTodayOrTomorrowAt8am(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(DailyBriefingBroadcastReceiver.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                DailyBriefingBroadcastReceiver.CHANNEL_ID,
                context.getString(R.string.ai_daily_briefing),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.ai_daily_briefing_sub)
            },
        )
    }

    @HiltWorker
    class DailyBriefingWorker @AssistedInject constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val aiService: AIService,
        private val dailyDataMessageBuilder: DailyBriefingDataMessageBuilder,
        private val aiSettings: AiSettings,
        private val dailyBriefingPrefs: DailyBriefingPrefs,
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            if (!dailyBriefingPrefs.isEnabledNow()) return Result.success()
            if (!aiSettings.isEnabled.value) return Result.success()

            val familyId = inputData.getString(KEY_FAMILY_ID) ?: return Result.failure()
            val familyName = inputData.getString(KEY_FAMILY_NAME) ?: applicationContext.getString(R.string.ai_your_family)
            val today = todayIsoDate()
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(PREF_LAST_ISO_DATE, null) == today) return Result.success()

            val systemPrompt = """
                Sei l'assistente AI di KidBox per la famiglia $familyName.
                Genera un briefing del mattino ULTRA-BREVE (max 4 bullet) focalizzato SOLO su oggi e domani.
                REGOLE:
                - Scrivi in italiano, tono pratico e diretto.
                - Esattamente 3-4 punti bullet (•), ognuno su una riga.
                - Ogni punto: max 12 parole.
                - Zero intestazioni, zero markdown, zero intro.
                - Includi solo: eventi oggi/domani con orario, dosi medicine oggi, todo in scadenza oggi, scadenze critiche nelle prossime 48h.
                - Termina SEMPRE con una riga vuota e poi: context.getString(R.string.ai_what_organize)
                - Se non c'è nulla di rilevante: context.getString(R.string.ai_free_day)
            """.trimIndent()

            val userMessage = dailyDataMessageBuilder.buildDailyDataMessage(familyId)
            val syntheticMessage = KBAIMessage(
                id = UUID.randomUUID().toString(),
                conversationId = "daily-briefing-$familyId",
                roleRaw = "user",
                content = userMessage,
                createdAtEpochMillis = System.currentTimeMillis(),
            )

            val result = aiService.sendMessage(
                messages = listOf(syntheticMessage),
                systemPrompt = systemPrompt,
                familyId = familyId,
            )

            result.onSuccess { response ->
                prefs.edit()
                    .putString(PREF_LAST_ISO_DATE, today)
                    .putString(PREF_LAST_TEXT, response.reply)
                    .apply()
                scheduleDaily8amNotification(applicationContext, familyId, familyName, response.reply)
            }

            if (result.isSuccess) return Result.success()
            val err = result.exceptionOrNull()
            val retry = err is AIServiceException &&
                (err.serviceError == AIServiceError.NetworkError || err.serviceError == AIServiceError.RateLimitReached)
            return if (retry) Result.retry() else Result.failure()
        }
    }

    /**
     * Annulla l'allarme e il lavoro periodico. Serve al logout: senza questo la
     * notifica generata per la famiglia precedente continuerebbe ad arrivare.
     */
    fun cancelScheduledNotification(context: Context, familyId: String) {
        val intent = Intent(context, DailyBriefingBroadcastReceiver::class.java).apply {
            action = DailyBriefingBroadcastReceiver.ACTION_DAILY_BRIEFING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("daily:$familyId").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
            pendingIntent.cancel()
        }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

}

class DailyBriefingBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val familyId = intent.getStringExtra("familyId").orEmpty()
        val familyName = intent.getStringExtra("familyName").orEmpty().ifBlank { context.getString(R.string.ai_your_family) }
        val fullText = intent.getStringExtra("fullText").orEmpty()
        if (fullText.isNotBlank()) {
            DailyBriefingDraftStore.save(context, fullText)
        }
        val firstLine = fullText.lineSequence().firstOrNull { it.isNotBlank() }?.take(100).orEmpty()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = "KB_OPEN_AI_CHAT"
            // NEW_TASK necessario: contesto non-Activity (worker/receiver). Senza, con
            // l'app in background Android può aprire un secondo task invece di
            // riportare avanti quello esistente.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra("push_type", "daily_briefing")
            putExtra("push_family_id", familyId)
            putExtra("familyName", familyName)
            putExtra("fullText", fullText.take(500))
            putExtra("type", "daily_briefing")
        }
        val pending = PendingIntent.getActivity(
            context,
            ("daily-open:$familyId").hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kidbox)
            .setContentTitle(context.getString(R.string.ai_daily_briefing_notification_title, familyName))
            .setContentText(firstLine.ifBlank { context.getString(R.string.ai_briefing_ready) })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(("daily:$familyId").hashCode(), notification)
        }.onSuccess {
            AppAnalytics.aiBriefingReceived(context)
        }

        DailyBriefingService.scheduleDaily8amNotification(context, familyId, familyName, fullText)
    }

    companion object {
        const val CHANNEL_ID = "kb_daily_briefing"
        const val ACTION_DAILY_BRIEFING = "it.vittorioscocca.kidbox.DAILY_BRIEFING"
    }

}
