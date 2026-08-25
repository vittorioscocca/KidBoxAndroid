package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.wallet.WalletReminderPrefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Promemoria di scadenza per i documenti d'identità del Wallet. Porting 1:1
 * di `WalletDocumentReminderService` (iOS): un solo avviso, una settimana
 * prima della scadenza, id univoco per documento, rispetta sia il toggle
 * per-documento sia la preference globale [WalletReminderPrefs] (stessa usata
 * dai biglietti). Mirror di [WalletReminderScheduler] (`AlarmManager`, non
 * WorkManager: coerente col resto del modulo Wallet).
 */
@Singleton
class WalletDocumentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletReminderPrefs: WalletReminderPrefs,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(documentId: String, familyId: String, title: String, expiryDate: LocalDate) {
        cancel(documentId)
        if (!walletReminderPrefs.isReminderEnabled()) return

        val fireDate = expiryDate.minusDays(7)
        val fireAtMillis = fireDate.atTime(LocalTime.of(9, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val now = System.currentTimeMillis()
        if (fireAtMillis <= now) return

        val body = title.ifBlank { "Documento" }
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.walletDocumentKey(documentId),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = ("walletdoc:$documentId").hashCode(),
                fireAtMillis = fireAtMillis,
                action = documentAction(documentId),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_WALLET_DOCUMENT_REMINDER,
                    HealthReminderReceiver.EXTRA_WALLET_DOCUMENT_ID to documentId,
                    HealthReminderReceiver.EXTRA_FAMILY_ID to familyId,
                    HealthReminderReceiver.EXTRA_TITLE to "Documento in scadenza tra una settimana",
                    HealthReminderReceiver.EXTRA_BODY to body,
                ),
            ),
        )
    }

    fun cancel(documentId: String) {
        alarmRegistry.forget(ReminderAlarmRegistry.walletDocumentKey(documentId))
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = documentAction(documentId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ("walletdoc:$documentId").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun documentAction(documentId: String) = "kb.wallet.document.reminder.$documentId"

}
