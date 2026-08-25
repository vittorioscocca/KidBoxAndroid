package it.vittorioscocca.kidbox.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.notifications.nudge.NudgeEngine
import it.vittorioscocca.kidbox.notifications.nudge.NudgeState
import it.vittorioscocca.kidbox.ui.screens.ai.planning.DailyBriefingService
import it.vittorioscocca.kidbox.ui.screens.ai.planning.HealthPatternAnalyzerService
import it.vittorioscocca.kidbox.ui.screens.ai.planning.WeeklySummaryService
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Azzera ogni promemoria locale al logout.
 *
 * Gli allarmi `AlarmManager` non vivono nel database: svuotare Room non li tocca,
 * e continuerebbero a scattare mostrando dati dell'account precedente. Qui si
 * annulla tutto — allarmi pianificati, notifiche già a schermo, badge e stato
 * locale di nudge e terapie.
 *
 * Va invocato **prima** di cancellare le preferenze di sessione: gli allarmi AI
 * hanno un requestCode che dipende dall'id famiglia attivo.
 */
@Singleton
class ReminderLogoutCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
    private val treatmentNotificationManager: TreatmentNotificationManager,
    private val nudgeEngine: NudgeEngine,
) {
    fun clearEverything() {
        // Registro unico: to-do, salute, terapie, Wallet, veicoli, pagamenti casa, password.
        runCatching { alarmRegistry.cancelAll() }
        runCatching { treatmentNotificationManager.clearAllRecords() }
        runCatching { nudgeEngine.cancelAllScheduled() }
        runCatching { NudgeState.setFires(context, emptyList()) }

        val familyId = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
            .getString("active_family_id", null)
            .orEmpty()
        if (familyId.isNotBlank()) {
            runCatching { DailyBriefingService.cancelScheduledNotification(context, familyId) }
            runCatching { WeeklySummaryService.cancelScheduledNotification(context, familyId) }
            runCatching { HealthPatternAnalyzerService.cancelScheduledNotification(context, familyId) }
        }

        // Notifiche già mostrate: restano nel cassetto finché l'utente non le tocca.
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
        runCatching { NotificationBadgeStore.reset(context) }

        KBLog.app.info("logout — tutti i promemoria locali annullati", "ReminderLogoutCleaner")
    }
}
