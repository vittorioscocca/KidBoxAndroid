package it.vittorioscocca.kidbox.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCompletionSource
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDates
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanRemoteStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSessionStatus
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Azioni rapide del promemoria di allenamento: **Fatto** e **Sposta**.
 *
 * Lo spostamento è deterministico e avviene subito (prima giornata utile fra i
 * giorni scelti), così il calendario resta coerente anche con l'app chiusa. La
 * riorganizzazione AI del resto della settimana costa un messaggio e richiede
 * rete: viene marcata come in sospeso e la esegue la schermata alla prima
 * apertura, come su iOS.
 */
class FitnessSessionActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FitnessActionEntryPoint {
        fun fitnessPlanStore(): FitnessPlanStore
        fun fitnessPlanRemoteStore(): FitnessPlanRemoteStore
        fun fitnessPlanReminderScheduler(): FitnessPlanReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_DONE && action != ACTION_MOVE) return

        val childId = intent.getStringExtra(EXTRA_CHILD_ID).orEmpty()
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (childId.isBlank() || sessionId.isBlank()) return

        // La notifica sparisce subito: l'utente ha già scelto, aspettare la
        // scrittura su Firestore la lascerebbe lì per qualche secondo.
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    FitnessActionEntryPoint::class.java,
                )
                val store = entryPoint.fitnessPlanStore()
                val plan = store.load(childId) ?: return@launch
                if (plan.session(sessionId) == null) return@launch

                val updated = if (action == ACTION_DONE) {
                    plan.updateSession(sessionId) { session ->
                        session.copy(
                            status = FitnessSessionStatus.DONE,
                            completedAtEpochMillis = System.currentTimeMillis(),
                            completionSource = FitnessCompletionSource.NOTIFICATION,
                        )
                    }
                } else {
                    val newDate = nextAvailableDate(plan, sessionId) ?: return@launch
                    store.setPendingReschedule(childId, sessionId)
                    plan.updateSession(sessionId) { session ->
                        session.copy(
                            originalDateEpochMillis = session.originalDateEpochMillis
                                ?: session.dateEpochMillis,
                            dateEpochMillis = newDate,
                            status = FitnessSessionStatus.PLANNED,
                        )
                    }
                }

                store.save(childId, updated)
                entryPoint.fitnessPlanRemoteStore().upsert(childId, updated)
                entryPoint.fitnessPlanReminderScheduler().reschedule(childId, familyId, updated)
                KBLog.app.info("quick action $action childId=$childId session=$sessionId", TAG)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Prima giornata utile fra i giorni di allenamento scelti, saltando quelle
     * che hanno già una seduta.
     */
    private fun nextAvailableDate(plan: FitnessPlanDocument, sessionId: String): Long? {
        val session = plan.session(sessionId) ?: return null
        val occupied = plan.allSessions
            .filter { it.id != sessionId }
            .map { FitnessPlanDates.startOfDay(it.dateEpochMillis) }
            .toSet()
        for (offset in 1..14) {
            val candidate = FitnessPlanDates.plusDays(session.dateEpochMillis, offset)
            if (FitnessPlanDates.weekdayOf(candidate) !in plan.input.trainingWeekdays) continue
            if (candidate in occupied) continue
            return candidate
        }
        // Nessun giorno libero fra quelli scelti: il giorno dopo va comunque bene.
        return FitnessPlanDates.plusDays(session.dateEpochMillis, 1)
    }

    companion object {
        const val ACTION_DONE = "it.vittorioscocca.kidbox.FITNESS_SESSION_DONE"
        const val ACTION_MOVE = "it.vittorioscocca.kidbox.FITNESS_SESSION_MOVE"
        const val EXTRA_CHILD_ID = "extra_child_id"
        const val EXTRA_FAMILY_ID = "extra_family_id"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val TAG = "FitnessSessionAction"
    }
}
