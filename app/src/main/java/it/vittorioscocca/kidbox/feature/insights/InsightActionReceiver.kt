package it.vittorioscocca.kidbox.feature.insights

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequestRemoteStore
import it.vittorioscocca.kidbox.data.repository.DoseLogRepository
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InsightEntryPoint {
    fun insightRunner(): InsightRunner
    fun todoRepository(): TodoRepository
    fun todoDao(): KBTodoItemDao
    fun treatmentDao(): KBTreatmentDao
    fun doseLogDao(): KBDoseLogDao
    fun doseLogRepository(): DoseLogRepository
    fun todoListDao(): KBTodoListDao
    fun housePaymentDao(): HousePaymentDao
}

/**
 * Azioni rapide sugli avvisi, senza aprire l'app — gemello di
 * `KBInsightActionHandler` su iOS:
 * - «Ci penso io» assegna il to-do a chi tocca (se nessuno l'ha preso intanto);
 * - «Chiedi agli altri» manda una richiesta di famiglia per quel to-do a chi lo
 *   vede: il primo sì se lo prende (`sourceTodoId`, gestito dal server);
 * - «Presa» registra la prima dose di oggi ancora scoperta;
 * - «Rimanda di un'ora» e «Non mi interessa» (chiude solo quell'avviso).
 * Dopo ogni azione il motore rifà subito il giro.
 */
class InsightActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ALL) return
        val insightId = intent.getStringExtra(InsightReceiver.EXTRA_INSIGHT_ID).orEmpty()
        val familyId = intent.getStringExtra(InsightReceiver.EXTRA_FAMILY_ID).orEmpty()
        if (insightId.isBlank()) return
        runCatching { NotificationManagerCompat.from(context).cancel(insightId.hashCode()) }

        val pending = goAsync()
        val app = context.applicationContext
        val ep = EntryPointAccessors.fromApplication(app, InsightEntryPoint::class.java)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, ep, action, insightId, familyId, intent)
            } catch (e: Exception) {
                KBLog.app.warning("InsightAction $action fallita: ${e.message}", TAG)
            } finally {
                ep.insightRunner().requestRun("action", 0)
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        ep: InsightEntryPoint,
        action: String,
        insightId: String,
        familyId: String,
        intent: Intent,
    ) {
        val runner = ep.insightRunner()
        val rows = runner.loadRows()
        var row = rows.firstOrNull { it.id == insightId } ?: return
        val now = System.currentTimeMillis()
        val typeRaw = row.typeRaw
        // Toccare un pulsante vuol dire che la notifica è arrivata.
        if (row.shownReportedAtMillis == null) {
            AppAnalytics.insightShown(context, typeRaw, "notification")
            row = row.copy(shownReportedAtMillis = row.notifiedAtMillis ?: now)
        }
        fun acted(via: String) {
            if (row.actedReportedAtMillis != null) return
            AppAnalytics.insightActed(context, typeRaw, via, InsightRunner.minutesSince(row.shownReportedAtMillis, now))
            row = row.copy(actedReportedAtMillis = now)
        }

        // Pagamento di casa: «Ci penso io» e «Chiedi agli altri» lavorano sul
        // to-do «Pagare: …» di quella scadenza, che nasce qui se non c'è ancora.
        var targetTodoId = intent.getStringExtra(InsightReceiver.EXTRA_TODO_ID).orEmpty()
        if (row.type == InsightType.HOUSE_PAYMENT_DUE && (action == ACTION_TAKE || action == ACTION_ASK)) {
            targetTodoId = ensurePaymentTodo(context, ep, row).orEmpty()
        }

        when (action) {
            ACTION_TAKE -> {
                val todoId = targetTodoId
                if (takeTodo(ep, todoId)) {
                    row = row.mark(InsightState.DONE, now)
                    acted("action")
                }
            }
            ACTION_ASK -> {
                // L'avviso resta aperto: si chiude quando qualcuno prende il to-do.
                val todoId = targetTodoId
                if (askOthers(context, ep, todoId, familyId)) acted("ask")
            }
            ACTION_DOSE_TAKEN -> {
                val treatmentId = intent.getStringExtra(InsightReceiver.EXTRA_TREATMENT_ID).orEmpty()
                if (recordFirstMissingDose(ep, treatmentId, familyId)) {
                    row = row.mark(InsightState.DONE, now)
                    acted("action")
                }
            }
            ACTION_SNOOZE -> {
                // Il runner vede una consegna futura e riarma l'allarme a quell'ora.
                row = row.copy(notifiedAtMillis = now + SNOOZE_MS)
                AppAnalytics.insightSnoozed(context, typeRaw)
            }
            ACTION_DISMISS -> {
                row = row.mark(InsightState.DISMISSED, now)
                AppAnalytics.insightDismissed(context, typeRaw)
            }
        }
        runner.saveRows(rows.map { if (it.id == insightId) row else it })
    }

    /**
     * Il to-do della scadenza (`<pagamento>@<giorno>`), creato senza nessuno se
     * non c'è. Id prevedibile ([InsightEngine.paymentTodoId], uguale a iOS): due
     * genitori che toccano insieme scrivono lo stesso to-do. Va nella lista
     * modificata più di recente; senza liste non si crea niente.
     */
    private suspend fun ensurePaymentTodo(context: Context, ep: InsightEntryPoint, row: InsightRecord): String? {
        val paymentId = row.entityId.substringBefore("@")
        val day = runCatching { LocalDate.parse(row.entityId.substringAfter("@")) }.getOrNull() ?: return null
        val payment = ep.housePaymentDao().getById(paymentId) ?: return null
        val todoId = InsightEngine.paymentTodoId(paymentId, day)
        if (ep.todoDao().getById(todoId) != null) return todoId
        val list = ep.todoListDao().getByFamily(row.familyId).maxByOrNull { it.updatedAtEpochMillis } ?: return null
        ep.todoRepository().addTodo(
            familyId = row.familyId,
            childId = list.childId,
            listId = list.id,
            title = context.getString(R.string.insight_payment_todo_title, payment.name),
            notes = null,
            // Senza orario: come i to-do «tutto il giorno», alle 9.
            dueAtEpochMillis = day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dueHasTime = false,
            assignedTo = null,
            priorityRaw = 0,
            reminderEnabled = false,
            id = todoId,
        )
        return todoId
    }

    private suspend fun takeTodo(ep: InsightEntryPoint, todoId: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val todo = ep.todoDao().getById(todoId) ?: return false
        // Qualcuno l'ha preso nel frattempo: non si toglie a lui.
        if (!todo.assignedTo.isNullOrBlank() || !todo.assignedExternalName.isNullOrBlank()) return false
        ep.todoRepository().updateTodo(
            todoId = todo.id,
            title = todo.title,
            notes = todo.notes,
            dueAtEpochMillis = todo.dueAtEpochMillis,
            dueHasTime = todo.dueHasTime,
            assignedTo = uid,
            priorityRaw = todo.priorityRaw,
            reminderEnabled = todo.reminderEnabled,
        )
        return true
    }

    private suspend fun askOthers(context: Context, ep: InsightEntryPoint, todoId: String, familyId: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val todo = ep.todoDao().getById(todoId) ?: return false
        if (!todo.assignedTo.isNullOrBlank() || !todo.assignedExternalName.isNullOrBlank()) return false
        val listId = todo.listId?.takeIf { it.isNotBlank() } ?: return false
        val recipients = ep.insightRunner().askRecipients(todo, uid)
        if (recipients.isEmpty()) return false
        FamilyRequestRemoteStore.create(
            context = context,
            familyId = familyId,
            childId = todo.childId,
            listId = listId,
            title = todo.title,
            notes = todo.notes,
            isUrgent = (todo.priorityRaw ?: 0) == 1,
            dueAtMillis = todo.dueAtEpochMillis,
            dueHasTime = todo.dueHasTime,
            draft = FamilyRequestRemoteStore.Draft(recipients = recipients),
            familyName = "",
            inviterName = "",
            sourceTodoId = todo.id,
        )
        return true
    }

    /** La prima dose di oggi non presa fra quelle già passate (altrimenti la prima). */
    private suspend fun recordFirstMissingDose(ep: InsightEntryPoint, treatmentId: String, familyId: String): Boolean {
        val t = ep.treatmentDao().getById(treatmentId) ?: return false
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = Instant.ofEpochMilli(t.startDateEpochMillis).atZone(zone).toLocalDate()
        val offset = ChronoUnit.DAYS.between(start, today).toInt()
        if (offset < 0) return false
        val dayNumber = offset + 1
        val times = t.scheduleTimesData.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val taken = ep.doseLogDao().listByTreatment(treatmentId)
            .filter { it.dayNumber == dayNumber && it.taken && !it.isDeleted }
            .map { it.slotIndex }.toSet()
        val missing = times.indices.filter { it !in taken }
        val nowTime = LocalTime.now(zone)
        val due = missing.filter { slot ->
            runCatching { !LocalTime.parse(times[slot]).isAfter(nowTime) }.getOrDefault(false)
        }
        val slot = due.firstOrNull() ?: missing.firstOrNull() ?: return false
        ep.doseLogRepository().markTaken(
            treatmentId = treatmentId,
            familyId = familyId,
            childId = t.childId,
            dayNumber = dayNumber,
            slotIndex = slot,
            scheduledTime = times[slot],
        )
        return true
    }

    companion object {
        private const val TAG = "InsightAction"
        private const val SNOOZE_MS = 3600_000L
        const val ACTION_TAKE = "it.vittorioscocca.kidbox.INSIGHT_TAKE"
        const val ACTION_ASK = "it.vittorioscocca.kidbox.INSIGHT_ASK"
        const val ACTION_DOSE_TAKEN = "it.vittorioscocca.kidbox.INSIGHT_DOSE_TAKEN"
        const val ACTION_SNOOZE = "it.vittorioscocca.kidbox.INSIGHT_SNOOZE"
        const val ACTION_DISMISS = "it.vittorioscocca.kidbox.INSIGHT_DISMISS"
        private val ALL = setOf(ACTION_TAKE, ACTION_ASK, ACTION_DOSE_TAKEN, ACTION_SNOOZE, ACTION_DISMISS)
    }
}

/** Giro di riserva ogni 3 ore, anche ad app chiusa. */
@HiltWorker
class InsightWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: InsightRunner,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runner.run("background")
        return Result.success()
    }
}
