package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import it.vittorioscocca.kidbox.R

/**
 * Allarmi locali one-shot per la scadenza password: fino a 3 avvisi (30/7/1 giorni prima, ore 9).
 * A differenza di [VehicleDeadlineReminderScheduler] non c'è ricorrenza annuale — la scadenza è
 * una data fissa impostata dall'utente, non ripetuta — quindi non serve alcun reschedule dopo il fire.
 * Equivalente Android di `NotificationManager.syncPasswordExpiryNotifications` (iOS).
 */
@Singleton
class PasswordExpiryReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun sync(entryId: String, familyId: String, title: String, expiresAtEpochMillis: Long?) {
        cancel(entryId)
        if (expiresAtEpochMillis == null) return

        val now = System.currentTimeMillis()
        val expiryDayStart = startOfDay(expiresAtEpochMillis)
        if (expiryDayStart < startOfDay(now)) return

        val displayTitle = title.trim().ifEmpty { context.getString(R.string.password_entry_fallback) }

        ALLOWED_OFFSETS.forEach { days ->
            val fireAt = nineAmDaysBefore(expiryDayStart, days)
            if (fireAt <= now + 5_000L) return@forEach

            alarmRegistry.arm(
                ReminderAlarmRegistry.AlarmSpec(
                    key = ReminderAlarmRegistry.passwordExpiryKey(entryId, days),
                    target = ReminderAlarmRegistry.Target.HEALTH,
                    requestCode = requestCode(entryId, days),
                    fireAtMillis = fireAt,
                    dataUri = alarmUri(entryId, days),
                    stringExtras = buildMap<String, String> {
                        put(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_PASSWORD_EXPIRY)
                        put(HealthReminderReceiver.EXTRA_PASSWORD_ENTRY_ID, entryId)
                        put(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
                        KBNotificationText.put(
                            this,
                            titleKey = "password_expiry_reminder_title",
                            bodyKey = bodyKeyFor(days),
                            bodyArgs = listOf(displayTitle, expiryLabel(expiresAtEpochMillis)),
                        )
                    },
                ),
            )
        }
    }

    fun cancel(entryId: String) {
        ALLOWED_OFFSETS.forEach { days ->
            alarmRegistry.forget(ReminderAlarmRegistry.passwordExpiryKey(entryId, days))
            val intent = baseIntent(entryId, familyId = "", days = days)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(entryId, days),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun alarmUri(entryId: String, days: Int): String = "kidbox://password-expiry/$entryId/$days"

    private fun baseIntent(entryId: String, familyId: String, days: Int): Intent =
        Intent(context, HealthReminderReceiver::class.java).apply {
            data = Uri.parse(alarmUri(entryId, days))
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_PASSWORD_EXPIRY)
            putExtra(HealthReminderReceiver.EXTRA_PASSWORD_ENTRY_ID, entryId)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
        }

    private fun bodyKeyFor(days: Int): String = when (days) {
        1 -> "password_expiry_body_tomorrow"
        7 -> "password_expiry_body_7d"
        else -> "password_expiry_body_30d"
    }

    /**
     * La data resta formattata ora, con la lingua di adesso.
     *
     * Per rifarla alla consegna dovrebbe viaggiare l'epoch e non il testo: qui
     * cambierebbe solo il nome del mese, e non vale un formato di extra a parte.
     */
    private fun expiryLabel(expiresAtEpochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiresAtEpochMillis))

    private fun startOfDay(epochMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun nineAmDaysBefore(expiryDayStartMillis: Long, days: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = expiryDayStartMillis
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    companion object {
        private val ALLOWED_OFFSETS = listOf(30, 7, 1)
        private fun requestCode(entryId: String, days: Int): Int = "kb_pwexp|$entryId|$days".hashCode()
    }
}
