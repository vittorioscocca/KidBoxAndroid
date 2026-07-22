package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.content.Context
import it.vittorioscocca.kidbox.ai.DailyBriefingPrefs
import it.vittorioscocca.kidbox.ai.HealthPatternPrefs
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

/**
 * Dopo BOOT_COMPLETED gli AlarmManager vanno persi: ripianifica briefing/settimanale
 * se esiste già un testo generato e la feature è attiva.
 */
object AiScheduledNotificationsRestorer {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val PREF_DAILY_TEXT = "kb_dailyBriefing_lastText"
    private const val PREF_WEEKLY_TEXT = "kb_weeklySummary_lastText"
    private const val PREF_WEEKLY_ENABLED = "kb_weeklySummaryEnabled"
    private const val PREF_HEALTH_TEXT = "kb_healthPattern_lastText"

    fun restoreAfterBoot(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val familyId = prefs.getString("active_family_id", null)?.takeIf { it.isNotBlank() } ?: return
        val familyName = prefs.getString("active_family_name", context.getString(R.string.ai_your_family)) ?: context.getString(R.string.ai_your_family)

        if (prefs.getBoolean(DailyBriefingPrefs.WORKER_KEY_ENABLED, true)) {
            prefs.getString(PREF_DAILY_TEXT, null)?.takeIf { it.isNotBlank() }?.let { text ->
                DailyBriefingService.scheduleDaily8amNotification(context, familyId, familyName, text)
            }
        }

        if (prefs.getBoolean(PREF_WEEKLY_ENABLED, true)) {
            prefs.getString(PREF_WEEKLY_TEXT, null)?.takeIf { it.isNotBlank() }?.let { text ->
                WeeklySummaryService.scheduleMonday8amNotification(context, familyId, familyName, text)
            }
        }

        if (prefs.getBoolean(HealthPatternPrefs.WORKER_KEY_ENABLED, true)) {
            prefs.getString(PREF_HEALTH_TEXT, null)?.takeIf { it.isNotBlank() }?.let { text ->
                HealthPatternAnalyzerService.scheduleFirstOfMonth9am(context, familyId, familyName, text)
            }
        }
    }
}
