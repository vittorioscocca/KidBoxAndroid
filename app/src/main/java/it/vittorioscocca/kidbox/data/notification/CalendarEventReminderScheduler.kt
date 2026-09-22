package it.vittorioscocca.kidbox.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.notifications.CalendarEventReminderReceiver
import it.vittorioscocca.kidbox.notifications.ReminderAlarmRegistry
import it.vittorioscocca.kidbox.notifications.UrgentAlarmReceiver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Il promemoria di un evento del calendario.
 *
 * Fino al 22/09/2026 l'interruttore «Promemoria» della scheda evento scriveva
 * `reminderMinutes` e **non armava niente**, né qui né su iOS: l'utente
 * accendeva l'avviso e non arrivava mai. Questo scheduler chiude quel buco.
 *
 * Se l'evento è **urgente** l'avviso diventa una sveglia
 * ([UrgentAlarmScheduler]); altrimenti è una notifica normale.
 */
@Singleton
class CalendarEventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
    private val urgentAlarmScheduler: UrgentAlarmScheduler,
) {
    /**
     * Allinea l'avviso allo stato dell'evento: lo arma, lo sposta o lo toglie.
     * Si chiama a ogni salvataggio, così non serve sapere cosa è cambiato.
     */
    fun sync(
        eventId: String,
        familyId: String,
        title: String,
        startEpochMillis: Long,
        reminderMinutes: Int?,
        isUrgent: Boolean,
    ) {
        cancel(eventId)
        val minutes = reminderMinutes ?: return
        val fireAt = startEpochMillis - minutes * 60_000L
        if (fireAt <= System.currentTimeMillis()) return

        if (isUrgent) {
            urgentAlarmScheduler.schedule(
                kind = UrgentAlarmReceiver.KIND_CALENDAR_EVENT,
                entityId = eventId,
                title = title,
                fireAtMillis = fireAt,
                familyId = familyId,
            )
            return
        }

        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.calendarEventKey(eventId),
                target = ReminderAlarmRegistry.Target.CALENDAR_EVENT,
                requestCode = requestCode(eventId),
                fireAtMillis = fireAt,
                stringExtras = mapOf(
                    CalendarEventReminderReceiver.EXTRA_EVENT_ID to eventId,
                    CalendarEventReminderReceiver.EXTRA_TITLE to title,
                    CalendarEventReminderReceiver.EXTRA_FAMILY_ID to familyId,
                ),
            ),
        )
    }

    /** Toglie notifica **e** sveglia: non si sa quale delle due fosse armata. */
    fun cancel(eventId: String) {
        if (eventId.isBlank()) return
        urgentAlarmScheduler.cancel(UrgentAlarmReceiver.KIND_CALENDAR_EVENT, eventId)
        alarmRegistry.forget(ReminderAlarmRegistry.calendarEventKey(eventId))
        val intent = Intent(context, CalendarEventReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(eventId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun requestCode(eventId: String): Int = "calendarEvent:$eventId".hashCode()
}
