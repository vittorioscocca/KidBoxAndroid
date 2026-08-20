package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HOUR_MS = 60L * 60L * 1000L
private const val MIN_MS = 60L * 1000L

/**
 * Offset di default per categoria (minuti prima dell'evento), applicati quando
 * `reminderOffsetHours == null` — biglietto mai toccato dall'utente nel picker
 * del dettaglio. Mirror esatto di `KBWalletTicketKind.defaultReminderOffsets`
 * (iOS): stessi valori, stesse categorie, così un volo avvisa a T-24h *e* T-3h
 * su entrambe le piattaforme invece che solo su iOS.
 */
private fun WalletTicketKind.legacyOffsetsMinutes(): List<Long> = when (this) {
    WalletTicketKind.FLIGHT -> listOf(24 * 60L, 3 * 60L)
    WalletTicketKind.FERRY -> listOf(12 * 60L, 2 * 60L)
    WalletTicketKind.TRAIN -> listOf(12 * 60L, 60L)
    WalletTicketKind.BUS -> listOf(6 * 60L, 60L)
    WalletTicketKind.CINEMA -> listOf(30L)
    WalletTicketKind.CONCERT, WalletTicketKind.MUSEUM -> listOf(24 * 60L, 2 * 60L)
    WalletTicketKind.PARKING -> listOf(15L)
    WalletTicketKind.OTHER -> listOf(24 * 60L, 2 * 60L)
}

/**
 * Universo di TUTTI gli offset (in minuti) mai usati da questo scheduler:
 * l'unione di tutti gli offset legacy per categoria più le tre scelte esplicite
 * del picker (1h/1g/2g). `AlarmManager` non ha un modo per elencare gli alarm
 * pendenti (a differenza di `UNUserNotificationCenter.pendingNotificationRequests()`
 * su iOS), quindi per cancellare in modo affidabile TUTTI i promemoria di un
 * biglietto — comprese le combinazioni schedulate prima di un cambio di scelta —
 * si cancellano preventivamente gli alarm per ciascun offset possibile: quelli
 * mai armati sono no-op innocui.
 */
private val ALL_POSSIBLE_OFFSETS_MINUTES: List<Long> =
    (WalletTicketKind.entries.flatMap { it.legacyOffsetsMinutes() } + listOf(60L, 24 * 60L, 48 * 60L))
        .distinct()

@Singleton
class WalletReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletTicketDao: WalletTicketDao,
    private val auth: FirebaseAuth,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleForFamily(familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank()) return@withContext
        val uid = auth.currentUser?.uid.orEmpty()
        val tickets = walletTicketDao.getActiveByFamilyId(familyId, uid)
        for (t in tickets) {
            cancelTicket(t.id)
        }
        val now = System.currentTimeMillis()
        for (t in tickets) {
            val event = t.eventDateEpochMillis ?: continue
            val offsetsMinutes = offsetsMinutesFor(t)
            for (offsetMinutes in offsetsMinutes) {
                val fireAt = event - offsetMinutes * MIN_MS
                if (fireAt <= now) continue
                val title = t.title.ifBlank { "Biglietto" }
                val body = "Tra poco: $title"
                val pi = buildPendingIntent(
                    ticketId = t.id,
                    offsetMinutes = offsetMinutes,
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
    }

    /**
     * `null` (mai toccato dall'utente) → offset multipli di default per la
     * categoria del biglietto, come su iOS. `0` → nessun promemoria. Valore
     * esplicito (1/24/48) → un singolo promemoria a quell'offset in ore.
     */
    private fun offsetsMinutesFor(t: KBWalletTicketEntity): List<Long> {
        val explicitHours = t.reminderOffsetHours
        return when {
            explicitHours == null -> WalletTicketKind.from(t.kindRaw).legacyOffsetsMinutes()
            explicitHours <= 0 -> emptyList()
            else -> listOf(explicitHours.toLong() * 60L)
        }
    }

    fun cancelTicket(ticketId: String) {
        for (offsetMinutes in ALL_POSSIBLE_OFFSETS_MINUTES) {
            val pi = buildPendingIntent(ticketId, offsetMinutes, "", "", "")
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun buildPendingIntent(
        ticketId: String,
        offsetMinutes: Long,
        familyId: String,
        title: String,
        body: String,
    ): PendingIntent {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.wallet.reminder.$ticketId.$offsetMinutes"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_WALLET_REMINDER)
            putExtra(HealthReminderReceiver.EXTRA_WALLET_TICKET_ID, ticketId)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, title)
            putExtra(HealthReminderReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            ("wallet:$ticketId:$offsetMinutes").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
