package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
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
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.repository.KBAIRepository
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import java.util.Calendar
import java.util.Locale
import java.util.UUID

object WeeklySummaryService {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val PREF_LAST_ISO_WEEK = "kb_weeklySummary_lastISOWeek"
    private const val PREF_LAST_TEXT = "kb_weeklySummary_lastText"
    private const val PREF_ENABLED = "kb_weeklySummaryEnabled"
    private const val WORK_NAME = "kb_weekly_summary_generation"
    private const val WORK_TAG = "kb_weekly_summary"
    private const val KEY_FAMILY_ID = "familyId"
    private const val KEY_FAMILY_NAME = "familyName"

    fun scheduleWeeklyIfNeeded(context: Context, familyId: String) {
        if (familyId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_ENABLED, true)) return
        if (prefs.getString(PREF_LAST_ISO_WEEK, null) == currentISOWeek()) return
        val familyName = prefs.getString("active_family_name", "la tua famiglia") ?: "la tua famiglia"

        val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
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

    fun currentISOWeek(): String {
        val c = Calendar.getInstance(Locale.getDefault())
        return "%04d-W%02d".format(Locale.getDefault(), currentISOYear(), c.get(Calendar.WEEK_OF_YEAR))
    }

    fun currentISOYear(): Int = Calendar.getInstance(Locale.getDefault()).weekYear

    fun scheduleMonday8amNotification(
        context: Context,
        familyId: String,
        familyName: String,
        recap: String,
    ) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextMondayAt8am()
        val intent = Intent(context, WeeklySummaryBroadcastReceiver::class.java).apply {
            action = WeeklySummaryBroadcastReceiver.ACTION_WEEKLY_SUMMARY
            putExtra("familyId", familyId)
            putExtra("familyName", familyName)
            putExtra("fullText", recap.take(500))
            putExtra("type", "weekly_summary")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("weekly:$familyId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun nextMondayAt8am(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(WeeklySummaryBroadcastReceiver.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                WeeklySummaryBroadcastReceiver.CHANNEL_ID,
                "Sintesi settimanale",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Recap automatico settimanale della famiglia"
            },
        )
    }

    @HiltWorker
    class WeeklySummaryWorker @AssistedInject constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val aiService: AIService,
        private val kbAIRepository: KBAIRepository,
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            val familyId = inputData.getString(KEY_FAMILY_ID) ?: return Result.failure()
            val familyName = inputData.getString(KEY_FAMILY_NAME) ?: "la tua famiglia"

            val systemPrompt = """
                Sei l'assistente AI di KidBox per la famiglia $familyName.
                Genera una sintesi settimanale BREVE (max 5 punti bullet).
                - Scrivi in italiano, tono caldo e pratico
                - Max 5 punti, ognuno max 15 parole
                - Niente intestazioni, markdown o emoji
                - Evidenzia: scadenze importanti, cure, eventi, todo urgenti, spese significative
                - Termina con UN solo suggerimento pratico
                - Se non c'è niente di significativo: "Settimana tranquilla! Nessuna scadenza urgente."
            """.trimIndent()

            val userMessage = "Genera il recap della settimana corrente per la mia famiglia."
            val syntheticMessage = KBAIMessage(
                id = UUID.randomUUID().toString(),
                conversationId = "weekly-summary-$familyId",
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
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(PREF_LAST_ISO_WEEK, currentISOWeek())
                    .putString(PREF_LAST_TEXT, response.reply)
                    .apply()
                // Keep recap trace in AI conversation scope.
                runCatching {
                    val conversation = kbAIRepository.getOrCreateConversation(
                        scopeId = "planning-agent-$familyId",
                        familyId = familyId,
                    )
                    kbAIRepository.addMessage(
                        conversationId = conversation.id,
                        role = it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole.ASSISTANT,
                        content = response.reply,
                    )
                }
                scheduleMonday8amNotification(applicationContext, familyId, familyName, response.reply)
            }
            return if (result.isSuccess) Result.success() else Result.retry()
        }
    }
}

class WeeklySummaryBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val familyId = intent.getStringExtra("familyId").orEmpty()
        val familyName = intent.getStringExtra("familyName").orEmpty().ifBlank { "la tua famiglia" }
        val fullText = intent.getStringExtra("fullText").orEmpty()
        val firstLine = fullText.lineSequence().firstOrNull()?.take(100).orEmpty()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = "KB_OPEN_AI_CHAT"
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("push_type", "weekly_summary")
            putExtra("push_family_id", familyId)
            putExtra("familyName", familyName)
            putExtra("fullText", fullText.take(500))
            putExtra("type", "weekly_summary")
        }
        val pending = PendingIntent.getActivity(
            context,
            ("weekly-open:$familyId").hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Settimana di $familyName")
            .setContentText(firstLine.ifBlank { "Apri il recap settimanale" })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(("weekly:$familyId").hashCode(), notification)
        }

        WeeklySummaryService.scheduleMonday8amNotification(context, familyId, familyName, fullText)
    }

    companion object {
        const val CHANNEL_ID = "kb_weekly_summary"
        const val ACTION_WEEKLY_SUMMARY = "it.vittorioscocca.kidbox.WEEKLY_SUMMARY"
    }
}
