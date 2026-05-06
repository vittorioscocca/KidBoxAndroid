package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.wallet.WalletReminderPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REMINDER_OFFSET_MS = 60L * 60L * 1000L

@Singleton
class WalletReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletTicketDao: WalletTicketDao,
    private val walletReminderPrefs: WalletReminderPrefs,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleForFamily(familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank()) return@withContext
        val tickets = walletTicketDao.getActiveByFamilyId(familyId)
        for (t in tickets) {
            cancelTicket(t.id)
        }
        if (!walletReminderPrefs.isReminderEnabled()) return@withContext
        val now = System.currentTimeMillis()
        for (t in tickets) {
            val event = t.eventDateEpochMillis ?: continue
            val fireAt = event - REMINDER_OFFSET_MS
            if (fireAt <= now) continue
            val title = t.title.ifBlank { "Biglietto" }
            val body = "Tra poco: $title"
            val pi = buildPendingIntent(
                ticketId = t.id,
                familyId = familyId,
                title = title,
                body = body,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
        }
    }

    fun cancelTicket(ticketId: String) {
        val pi = buildPendingIntent(ticketId, "", "", "")
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun buildPendingIntent(
        ticketId: String,
        familyId: String,
        title: String,
        body: String,
    ): PendingIntent {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.wallet.reminder.$ticketId"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_WALLET_REMINDER)
            putExtra(HealthReminderReceiver.EXTRA_WALLET_TICKET_ID, ticketId)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, title)
            putExtra(HealthReminderReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            ("wallet:$ticketId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
