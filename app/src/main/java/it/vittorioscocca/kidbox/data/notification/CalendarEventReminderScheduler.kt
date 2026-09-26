package it.vittorioscocca.kidbox.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.domain.calendar.EventRecurrence
import it.vittorioscocca.kidbox.notifications.CalendarEventReminderReceiver
import it.vittorioscocca.kidbox.notifications.ReminderAlarmRegistry
import it.vittorioscocca.kidbox.notifications.UrgentAlarmReceiver
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Il promemoria di un evento del calendario.
 *
 * Fino al 22/09/2026 l'interruttore «Promemoria» della scheda evento scriveva
 * `reminderMinutes` e **non armava niente**, né qui né su iOS: l'utente
 * accendeva l'avviso e non arrivava mai. Questo scheduler chiude quel buco.
 *
 * Se l'evento è **urgente** l'avviso diventa una sveglia
 * ([UrgentAlarmScheduler]); altrimenti è una notifica normale.
 *
 * **Eventi ricorrenti.** Si arma sempre la **prossima** ripetizione (prima si
 * armava la prima data della serie: se era passata, l'avviso non suonava
 * mai). Quando suona, il ricevitore chiama [rearmAfterFire], che rilegge
 * l'evento da Room e arma la successiva: la catena si regge da sola, anche ad
 * app chiusa, e dopo un riavvio la riprende `ReminderAlarmRegistry.restoreAll`.
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
        recurrenceRaw: String? = null,
    ) {
        cancel(eventId)
        val minutes = reminderMinutes ?: return
        val lead = minutes * 60_000L
        val now = System.currentTimeMillis()
        // L'inizio della prossima ripetizione il cui avviso cade nel futuro.
        val nextStart = EventRecurrence.nextStartAfter(
            startMillis = startEpochMillis,
            recurrenceRaw = recurrenceRaw,
            afterMillis = now + lead,
        ) ?: return
        val fireAt = nextStart - lead
        if (fireAt <= now) return

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

    /** Allinea l'avviso a un evento di Room. */
    fun sync(event: KBCalendarEventEntity) {
        if (event.isDeleted) {
            cancel(event.id)
            return
        }
        sync(
            eventId = event.id,
            familyId = event.familyId,
            title = event.title,
            startEpochMillis = event.startDateEpochMillis,
            reminderMinutes = event.reminderMinutes,
            isUrgent = event.priorityRaw == 1,
            recurrenceRaw = event.recurrenceRaw,
        )
    }

    /**
     * Vero se questo dispositivo ha un avviso armato per l'evento, in una
     * qualunque delle due strade. Serve alla sincronizzazione: un evento
     * spostato o cancellato da un altro device deve poter riallineare
     * l'avviso **solo** dove era stato acceso.
     */
    fun hasArmed(eventId: String): Boolean =
        eventId.isNotBlank() && (
            alarmRegistry.isArmed(ReminderAlarmRegistry.calendarEventKey(eventId)) ||
                alarmRegistry.isArmed(
                    ReminderAlarmRegistry.urgentKey(UrgentAlarmReceiver.KIND_CALENDAR_EVENT, eventId),
                )
            )

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

    companion object {
        /**
         * Chiamato dai ricevitori quando l'avviso di un evento è appena
         * suonato: se l'evento è una serie si arma la ripetizione successiva.
         * Legge Room, quindi prende anche le modifiche arrivate da altri
         * dispositivi; un evento cancellato nel frattempo si ferma qui.
         */
        fun rearmAfterFire(context: Context, eventId: String) {
            if (eventId.isBlank()) return
            val appCtx = context.applicationContext
            val ep = EntryPointAccessors.fromApplication(appCtx, CalendarReminderEntryPoint::class.java)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val event = ep.calendarEventDao().getById(eventId) ?: return@runCatching
                    if (event.isDeleted || !EventRecurrence.isRecurring(event.recurrenceRaw)) return@runCatching
                    ep.calendarEventReminderScheduler().sync(event)
                }.onFailure { KBLog.app.error("riarmo serie fallito eventId=$eventId", "CalendarReminder", it) }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarReminderEntryPoint {
    fun calendarEventDao(): KBCalendarEventDao
    fun calendarEventReminderScheduler(): CalendarEventReminderScheduler
}
