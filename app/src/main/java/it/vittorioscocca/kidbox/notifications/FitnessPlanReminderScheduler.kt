package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDates
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSessionStatus
import it.vittorioscocca.kidbox.util.KBLog
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Promemoria delle sedute del Piano Fitness.
 *
 * Come tutti i promemoria KidBox sono **del device** che ha creato il piano:
 * passano da [ReminderAlarmRegistry], così il ripristino al reboot ri-arma solo
 * ciò che è stato armato qui e non ciò che arriva dal sync.
 */
@Singleton
class FitnessPlanReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Quante sedute future tenere armate: oltre le due settimane il piano cambia
     * quasi sempre prima che l'alarm scatti, e ogni alarm è un record in più da
     * ripristinare al reboot.
     */
    private val maxScheduled = 10

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Ripianifica da zero i promemoria del piano: è l'unico punto di ingresso,
     * chiamato dopo generazione, spostamento, completamento e ricalcolo.
     */
    fun reschedule(childId: String, familyId: String, plan: FitnessPlanDocument) {
        cancelAll(childId)
        if (!plan.input.reminderEnabled) {
            KBLog.app.info("promemoria disattivati childId=$childId", TAG)
            return
        }

        val now = System.currentTimeMillis()
        val scheduled = plan.allSessions
            .filter { it.status == FitnessSessionStatus.PLANNED && !it.isRest }
            .mapNotNull { session ->
                val fireAt = fireTime(session.dateEpochMillis, plan.input.reminderHour, plan.input.reminderMinute)
                if (fireAt <= now) null else session to fireAt
            }
            .sortedBy { it.second }
            .take(maxScheduled)

        scheduled.forEach { (session, fireAt) ->
            val body = buildString {
                append(session.title)
                if (session.durationMinutes > 0) append(" · ${session.durationMinutes} min")
                session.targets.firstOrNull()?.let { append("\n").append(it) }
            }
            alarmRegistry.arm(
                ReminderAlarmRegistry.AlarmSpec(
                    key = ReminderAlarmRegistry.fitnessSessionKey(childId, session.id),
                    target = ReminderAlarmRegistry.Target.HEALTH,
                    requestCode = requestCode(childId, session.id),
                    fireAtMillis = fireAt,
                    action = action(childId, session.id),
                    stringExtras = buildMap<String, String> {
                        put(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_FITNESS_SESSION)
                        put(HealthReminderReceiver.EXTRA_CHILD_ID, childId)
                        put(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
                        put(HealthReminderReceiver.EXTRA_FITNESS_SESSION_ID, session.id)
                        put(HealthReminderReceiver.EXTRA_BODY, body)
                        KBNotificationText.put(this, titleKey = "fitness_reminder_title")
                    },
                ),
            )
        }

        prefs.edit()
            .putStringSet(entriesKey(childId), scheduled.map { it.first.id }.toSet())
            .apply()
        KBLog.app.info("armati ${scheduled.size} promemoria childId=$childId", TAG)
    }

    fun cancelAll(childId: String) {
        val sessionIds = prefs.getStringSet(entriesKey(childId), emptySet()).orEmpty()
        sessionIds.forEach { sessionId ->
            alarmRegistry.forget(ReminderAlarmRegistry.fitnessSessionKey(childId, sessionId))
            val intent = Intent(context, HealthReminderReceiver::class.java).apply {
                action = action(childId, sessionId)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(childId, sessionId),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pending)
            pending.cancel()
        }
        prefs.edit().remove(entriesKey(childId)).apply()
    }

    private fun fireTime(dayEpochMillis: Long, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = FitnessPlanDates.startOfDay(dayEpochMillis)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun action(childId: String, sessionId: String) = "kb.fitness.session.$childId.$sessionId"

    private fun requestCode(childId: String, sessionId: String) = "fitness:$childId:$sessionId".hashCode()

    private fun entriesKey(childId: String) = "sessions_$childId"

    private companion object {
        const val TAG = "FitnessReminderSched"
        const val PREFS_NAME = "kb_fitness_alarms"
    }
}
