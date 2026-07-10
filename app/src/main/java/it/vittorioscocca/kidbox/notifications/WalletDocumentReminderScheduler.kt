package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val pi = buildPendingIntent(
            documentId = documentId,
            familyId = familyId,
            title = "Documento in scadenza tra una settimana",
            body = body,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
        }
    }

    fun cancel(documentId: String) {
        val pi = buildPendingIntent(documentId, "", "", "")
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun buildPendingIntent(
        documentId: String,
        familyId: String,
        title: String,
        body: String,
    ): PendingIntent {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.wallet.document.reminder.$documentId"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_WALLET_DOCUMENT_REMINDER)
            putExtra(HealthReminderReceiver.EXTRA_WALLET_DOCUMENT_ID, documentId)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, title)
            putExtra(HealthReminderReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            ("walletdoc:$documentId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
