package it.vittorioscocca.kidbox.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.notifications.ReminderAlarmRegistry
import it.vittorioscocca.kidbox.notifications.UrgentAlarmReceiver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arma e disarma la **sveglia** di un promemoria urgente — to-do o evento del
 * calendario. Gemello di `KBUrgentAlarmService` su iOS.
 *
 * Come tutti i promemoria KidBox è del dispositivo che salva l'elemento: passa
 * da [ReminderAlarmRegistry], quindi sopravvive al reboot e sparisce al logout
 * insieme agli altri.
 */
@Singleton
class UrgentAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    fun schedule(
        kind: String,
        entityId: String,
        title: String,
        fireAtMillis: Long,
        familyId: String,
        childId: String? = null,
        listId: String? = null,
    ) {
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.urgentKey(kind, entityId),
                target = ReminderAlarmRegistry.Target.URGENT,
                requestCode = requestCode(kind, entityId),
                fireAtMillis = fireAtMillis,
                stringExtras = mapOf(
                    UrgentAlarmReceiver.EXTRA_KIND to kind,
                    UrgentAlarmReceiver.EXTRA_ENTITY_ID to entityId,
                    UrgentAlarmReceiver.EXTRA_TITLE to title,
                    UrgentAlarmReceiver.EXTRA_FAMILY_ID to familyId,
                    UrgentAlarmReceiver.EXTRA_CHILD_ID to childId,
                    UrgentAlarmReceiver.EXTRA_LIST_ID to listId,
                ),
            ),
        )
    }

    fun cancel(kind: String, entityId: String) {
        if (entityId.isBlank()) return
        alarmRegistry.forget(ReminderAlarmRegistry.urgentKey(kind, entityId))
        val intent = Intent(context, UrgentAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(kind, entityId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /**
     * Il `requestCode` distingue anche il tipo: un to-do e un evento con lo
     * stesso id si annullerebbero a vicenda.
     */
    private fun requestCode(kind: String, entityId: String): Int = "$kind:$entityId".hashCode()
}
