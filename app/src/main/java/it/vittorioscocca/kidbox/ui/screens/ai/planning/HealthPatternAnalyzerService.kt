package it.vittorioscocca.kidbox.ui.screens.ai.planning

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
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.ai.HealthPatternPrefs
import it.vittorioscocca.kidbox.data.local.dao.KBHealthInsightDao
import it.vittorioscocca.kidbox.data.local.entity.KBHealthInsightEntity
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
import it.vittorioscocca.kidbox.notifications.ExactAlarmScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

object HealthPatternAnalyzerService {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val PREF_LAST_MONTH_KEY = "kb_healthPattern_lastMonth"
    private const val PREF_LAST_TEXT = "kb_healthPattern_lastText"
    private const val PREF_ENABLED = "kb_healthPatternEnabled"
    private const val WORK_NAME = "kb_health_pattern_generation"
    private const val WORK_TAG = "kb_health_pattern"
    private const val KEY_FAMILY_ID = "familyId"
    private const val KEY_FAMILY_NAME = "familyName"

    fun scheduleMonthlyIfNeeded(context: Context, familyId: String) {
        if (familyId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_ENABLED, true)) return
        if (prefs.getString(PREF_LAST_MONTH_KEY, null) == currentMonthKey()) return

        val familyName = prefs.getString("active_family_name", context.getString(R.string.ai_your_family)) ?: context.getString(R.string.ai_your_family)
        val request = OneTimeWorkRequestBuilder<HealthPatternWorker>()
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

    fun currentMonthKey(): String {
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        return fmt.format(Date())
    }

    fun scheduleFirstOfMonth9am(
        context: Context,
        familyId: String,
        familyName: String,
        fullText: String,
    ) {
        ensureChannel(context)
        val triggerAt = nextFirstOfMonth9am()
        val intent = Intent(context, HealthPatternBroadcastReceiver::class.java).apply {
            action = HealthPatternBroadcastReceiver.ACTION_HEALTH_PATTERN
            putExtra("familyId", familyId)
            putExtra("familyName", familyName)
            putExtra("fullText", fullText.take(500))
            putExtra("type", "health_pattern")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("health_pattern:$familyId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ExactAlarmScheduler.scheduleRtcWakeupAllowWhileIdle(context, triggerAt, pendingIntent)
    }

    private fun nextFirstOfMonth9am(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.MONTH, 1)
        }
        return target.timeInMillis
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(HealthPatternBroadcastReceiver.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                HealthPatternBroadcastReceiver.CHANNEL_ID,
                context.getString(R.string.ai_health_patterns),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.ai_health_patterns_sub)
            },
        )
    }

    private fun isNoPattern(reply: String): Boolean {
        val normalized = reply.trim().uppercase(Locale.ROOT)
        return normalized == "NESSUN_PATTERN" || normalized.startsWith("NESSUN_PATTERN\n")
    }

    @HiltWorker
    class HealthPatternWorker @AssistedInject constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val aiService: AIService,
        private val healthInsightDao: KBHealthInsightDao,
        private val dataMessageBuilder: HealthPatternDataMessageBuilder,
        private val aiSettings: AiSettings,
        private val healthPatternPrefs: HealthPatternPrefs,
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            if (!healthPatternPrefs.isEnabledNow()) return Result.success()
            if (!aiSettings.isEnabled.value) return Result.success()

            val familyId = inputData.getString(KEY_FAMILY_ID) ?: return Result.failure()
            val familyName = inputData.getString(KEY_FAMILY_NAME) ?: applicationContext.getString(R.string.ai_your_family)
            val monthKey = currentMonthKey()
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (prefs.getString(PREF_LAST_MONTH_KEY, null) == monthKey) {
                return Result.success()
            }

            val systemPrompt = """
                Sei un assistente sanitario AI per la famiglia $familyName su KidBox.
                Hai accesso alla storia sanitaria completa di tutti i figli negli anni.
                Il tuo compito è individuare PATTERN RICORRENTI e ANOMALIE rilevanti —
                cose che un genitore non riesce a vedere guardando i singoli eventi ma
                che emergono guardando la storia completa.
                REGOLE TASSATIVE:
                - Scrivi in italiano, tono caldo ma diretto, come un pediatra di fiducia.
                - Max 4 insight, ognuno su una riga che inizia con "• ".
                - Ogni insight: max 25 parole, specifica sempre il nome del bambino.
                - Ogni insight DEVE essere azionabile (suggerisci sempre cosa fare).
                - INCLUDI solo: ricorrenze stagionali (stessa malattia/tipo cura in stesso
                  periodo su anni diversi), uso frequente antibiotici (4+ volte in 6 mesi),
                  visite specialistiche mancanti da oltre 12 mesi, esami prescritti mai
                  eseguiti da oltre 3 mesi, vaccini pianificati con data passata non
                  somministrati, pattern peso/altezza se disponibili.
                - IGNORA: eventi isolati, dati già gestiti da reminder attivi, osservazioni
                  ovvie (es. "ha avuto la febbre").
                - Se non trovi pattern rilevanti: rispondi NESSUN_PATTERN
                - NON inventare, NON speculare oltre i dati, NON spaventare.
                Termina con una riga vuota e poi: "Vuoi approfondire uno di questi?"
            """.trimIndent()

            val userMessage = dataMessageBuilder.buildHealthDataMessage(familyId, familyName)
            val syntheticMessage = KBAIMessage(
                id = UUID.randomUUID().toString(),
                conversationId = "health-pattern-$familyId",
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
                val reply = response.reply.trim()
                prefs.edit().putString(PREF_LAST_MONTH_KEY, monthKey).apply()

                if (!isNoPattern(reply)) {
                    prefs.edit().putString(PREF_LAST_TEXT, response.reply).apply()
                    val insightId = "health-insight-$familyId-$monthKey"
                    healthInsightDao.insert(
                        KBHealthInsightEntity(
                            id = insightId,
                            familyId = familyId,
                            fullText = response.reply,
                            monthKey = monthKey,
                            createdAtEpochMillis = System.currentTimeMillis(),
                            isRead = false,
                        ),
                    )
                    scheduleFirstOfMonth9am(applicationContext, familyId, familyName, response.reply)
                }
            }

            if (result.isSuccess) return Result.success()
            val err = result.exceptionOrNull()
            val retry = err is AIServiceException &&
                (err.serviceError == AIServiceError.NetworkError || err.serviceError == AIServiceError.RateLimitReached)
            return if (retry) Result.retry() else Result.failure()
        }
    }
}

class HealthPatternBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val familyId = intent.getStringExtra("familyId").orEmpty()
        val familyName = intent.getStringExtra("familyName").orEmpty().ifBlank { context.getString(R.string.ai_your_family) }
        val fullText = intent.getStringExtra("fullText").orEmpty()
        val firstLine = fullText.lineSequence()
            .firstOrNull { line ->
                val t = line.trim()
                t.isNotBlank() && (t.startsWith("•") || t.startsWith("-"))
            }
            ?.take(100)
            ?: fullText.lineSequence().firstOrNull { it.isNotBlank() }?.take(100)
            .orEmpty()

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
            putExtra("push_type", "health_pattern")
            putExtra("push_family_id", familyId)
            putExtra("familyName", familyName)
            putExtra("fullText", fullText.take(500))
            putExtra("type", "health_pattern")
        }
        val pending = PendingIntent.getActivity(
            context,
            ("health-pattern-open:$familyId").hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kidbox)
            .setContentTitle("🔍 Pattern salute · $familyName")
            .setContentText(firstLine.ifBlank { context.getString(R.string.ai_open_health_insights) })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(
                ("health_pattern:$familyId").hashCode(),
                notification,
            )
        }

        HealthPatternAnalyzerService.scheduleFirstOfMonth9am(context, familyId, familyName, fullText)
    }

    companion object {
        const val CHANNEL_ID = "kb_health_pattern"
        const val ACTION_HEALTH_PATTERN = "it.vittorioscocca.kidbox.HEALTH_PATTERN"
    }
}
