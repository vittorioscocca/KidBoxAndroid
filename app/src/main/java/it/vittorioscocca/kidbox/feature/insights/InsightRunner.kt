package it.vittorioscocca.kidbox.feature.insights

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.room.InvalidationTracker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.PetDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.notifications.ExactAlarmScheduler
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import it.vittorioscocca.kidbox.util.decodeStringList
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Avvisi proattivi su Android — gemello di `KBInsightRunner.swift`.
 *
 * Gira a ogni modifica locale di to-do, cure, dosi e membri (Room), al
 * passaggio in primo piano, al riavvio del telefono e ogni 3 ore con
 * WorkManager. Un giro: fotografia → regole → riconciliazione → allarmi.
 * Tutto locale, salvo la lettura del piano (al più una all'ora).
 *
 * Gli avvisi vivono in SharedPreferences e non in Room: sono pochi e solo di
 * questo telefono, e una tabella nuova vorrebbe una migrazione su un database
 * che senza migrazione si azzera (`fallbackToDestructiveMigration`).
 *
 * È una funzione Pro/Max come il resto della Mente proattiva.
 */
@Singleton
class InsightRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: KidBoxDatabase,
    private val todoDao: KBTodoItemDao,
    private val treatmentDao: KBTreatmentDao,
    private val doseLogDao: KBDoseLogDao,
    private val memberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val petDao: PetDao,
    private val subscriptionRepository: SubscriptionRepository,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var scheduled: Job? = null
    private var started = false
    /** Le preferenze sono cambiate: al prossimo giro le consegne future si ricalcolano. */
    @Volatile private var needsDeliveryReset = false

    private val local get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _prefs = MutableStateFlow(loadLocalPrefs())
    val prefs: StateFlow<InsightPreferences> = _prefs.asStateFlow()

    // ── Avvio ────────────────────────────────────────────────────────────────

    fun start() {
        if (started) return
        started = true
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(
                arrayOf("kb_todo_items", "kb_treatments", "kb_dose_logs", "kb_family_members"),
            ) {
                override fun onInvalidated(tables: Set<String>) = requestRun("data")
            },
        )
        // Il piano arriva quando la Home lo carica: un passaggio a Pro o Max fa
        // ripartire il giro, come su iOS.
        scope.launch {
            CurrentPlanStore.plan.collect { plan -> if (plan != KBPlan.FREE) requestRun("plan") }
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InsightWorker>(3, TimeUnit.HOURS).build(),
        )
    }

    /** Le richieste che arrivano entro [delayMs] dalla precedente la sostituiscono. */
    fun requestRun(reason: String, delayMs: Long = 2_000) {
        scheduled?.cancel()
        scheduled = scope.launch {
            if (delayMs > 0) delay(delayMs)
            run(reason)
        }
    }

    // ── Preferenze ───────────────────────────────────────────────────────────

    /** Dall'utente: copia locale subito, Firestore in merge. */
    fun updatePrefs(change: (InsightPreferences) -> InsightPreferences) {
        val next = change(_prefs.value)
        if (next == _prefs.value) return
        applyPrefs(next)
        AppAnalytics.insightPrefsChanged(context, next.delivery.raw, next.mutedTypes.size, next.delegateAsk)
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(mapOf(REMOTE_FIELD to next.toFirestore()), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }.onFailure { KBLog.app.warning("Insight: preferenze non salvate ${it.message}", TAG) }
        }
    }

    private fun applyPrefs(next: InsightPreferences) {
        _prefs.value = next
        local.edit().putString(KEY_PREFS, org.json.JSONObject(next.toFirestore()).toString()).apply()
        needsDeliveryReset = true
        requestRun("prefs", 0)
    }

    private fun loadLocalPrefs(): InsightPreferences = runCatching {
        val raw = local.getString(KEY_PREFS, null) ?: return InsightPreferences()
        val o = org.json.JSONObject(raw)
        InsightPreferences.fromFirestore(
            mapOf(
                "delivery" to o.optString("delivery"),
                "quietStart" to o.optInt("quietStart", 21 * 60 + 30),
                "quietEnd" to o.optInt("quietEnd", 8 * 60),
                "mutedTypes" to (0 until (o.optJSONArray("mutedTypes")?.length() ?: 0)).map { o.getJSONArray("mutedTypes").getString(it) },
                "delegateAsk" to o.optBoolean("delegateAsk", false),
            ),
        )
    }.getOrDefault(InsightPreferences())

    /** Un altro dispositivo può averle cambiate: al più una lettura ogni 10 minuti. */
    private suspend fun refreshRemotePrefsIfNeeded(uid: String) {
        val last = local.getLong(KEY_PREFS_READ_AT, 0)
        if (System.currentTimeMillis() - last < 10 * 60_000) return
        local.edit().putLong(KEY_PREFS_READ_AT, System.currentTimeMillis()).apply()
        val map = runCatching {
            FirebaseFirestore.getInstance().collection("users").document(uid).get().await().get(REMOTE_FIELD) as? Map<*, *>
        }.getOrNull() ?: return
        val remote = InsightPreferences.fromFirestore(map)
        if (remote != _prefs.value) applyPrefs(remote)
    }

    // ── Un giro ──────────────────────────────────────────────────────────────

    suspend fun run(reason: String) = mutex.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock
        val familyId = activeFamilyId()
        if (familyId.isBlank()) return@withLock
        refreshRemotePrefsIfNeeded(uid)

        val prefs = _prefs.value
        val paid = prefs.delivery != InsightPreferences.Delivery.OFF && isPaid(uid, familyId)
        if (!paid) {
            KBLog.app.debug("Insight[$reason]: fermo (consegna=${prefs.delivery.raw}, piano non Pro/Max)", TAG)
            cancelAllAlarms()
            return@withLock
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            KBLog.app.debug("Insight[$reason]: notifiche dell'app spente", TAG)
            return@withLock
        }

        runCatching {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val candidates = InsightEngine.evaluate(snapshot(familyId, uid), now, zone)
                .filter { !prefs.isMuted(it.type) }
            val (rows, changes) = InsightReconciler.reconcile(loadRows(), candidates, familyId, uid, now)
            val reported = reportActed(rows, changes.resolved, prefs, now)
            val reset = needsDeliveryReset
            needsDeliveryReset = false
            val plan = InsightDelivery.plan(reported, familyId, prefs, now, zone, resetFuture = reset)
            val shown = reportShown(plan.rows, now, prefs)
            saveRows(shown)
            arm(plan, shown, familyId)
            KBLog.app.debug(
                "Insight[$reason]: candidati=${candidates.size} aperti=${shown.count { it.state.isOpen }} allarmi=${plan.notifications.size}",
                TAG,
            )
            if (!changes.isEmpty) {
                KBLog.app.info(
                    "Insight[$reason]: aperti=${changes.opened.size} spostati=${changes.rescheduled.size} risolti=${changes.resolved.size}",
                    TAG,
                )
            }
        }.onFailure { KBLog.app.warning("Insight[$reason]: ${it.message}", TAG) }
    }

    /**
     * Pro o Max? Prima il piano già caricato dalla Home, poi Firestore con una
     * cache. `loadPlan` restituisce Free anche quando la lettura fallisce (per
     * esempio subito dopo l'accesso), quindi un Free si tiene solo 10 minuti:
     * con un'ora intera un errore spegneva gli avvisi per un'ora.
     */
    private suspend fun isPaid(uid: String, familyId: String): Boolean {
        if (CurrentPlanStore.plan.value != KBPlan.FREE) return true
        val cachedAt = local.getLong(KEY_PLAN_AT, 0)
        val cached = local.getString(KEY_PLAN, null)
        val sameFamily = local.getString(KEY_PLAN_FAMILY, null) == familyId
        val ttl = if (cached == KBPlan.FREE.rawValue) 10 * 60_000L else 3600_000L
        val plan = if (cached != null && sameFamily && System.currentTimeMillis() - cachedAt < ttl) {
            cached
        } else {
            val fresh = subscriptionRepository.loadPlan(familyId, uid).rawValue
            local.edit().putString(KEY_PLAN, fresh).putString(KEY_PLAN_FAMILY, familyId)
                .putLong(KEY_PLAN_AT, System.currentTimeMillis()).apply()
            fresh
        }
        return plan != KBPlan.FREE.rawValue
    }

    private suspend fun snapshot(familyId: String, uid: String): InsightSnapshot {
        val todos = todoDao.getByFamily(familyId).filter { !it.isDone }
        val treatments = treatmentDao.observeByFamilyId(familyId).first().filter { it.isActive && !it.isDeleted }
        val logs = treatments.flatMap { doseLogDao.listByTreatment(it.id) }.filter { it.taken && !it.isDeleted }
        return InsightSnapshot(
            todos = todos.map {
                InsightSnapshot.Todo(
                    id = it.id,
                    dueAtMillis = it.dueAtEpochMillis,
                    hasDueTime = it.dueHasTime,
                    isDone = it.isDone,
                    isDeleted = it.isDeleted,
                    isTaken = !it.assignedTo.isNullOrBlank() || !it.assignedExternalName.isNullOrBlank(),
                    isVisibleToUser = KBVisibilityScope.isVisible(
                        it.visibilityScope, decodeStringList(it.visibilityMemberIdsJson), it.createdBy, uid,
                    ),
                )
            },
            treatments = treatments.map {
                InsightSnapshot.Treatment(
                    id = it.id, isActive = it.isActive, isDeleted = it.isDeleted, isLongTerm = it.isLongTerm,
                    durationDays = it.durationDays, startDateMillis = it.startDateEpochMillis,
                    endDateMillis = it.endDateEpochMillis, intervalBetweenDosesDays = it.intervalBetweenDosesDays,
                    scheduleTimes = it.scheduleTimesData.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() },
                )
            },
            doseLogs = logs.map { InsightSnapshot.DoseLog(it.treatmentId, it.dayNumber, it.slotIndex, it.taken, it.isDeleted) },
        )
    }

    /** Chi riceve «Chiedi agli altri»: i membri che vedono il to-do, tranne chi chiede. */
    suspend fun askRecipients(todo: KBTodoItemEntity, uid: String): List<String> {
        val members = memberDao.observeActiveByFamilyId(todo.familyId).first().map { it.userId }
        return askRecipients(todo.visibilityScope, decodeStringList(todo.visibilityMemberIdsJson), members, uid)
    }

    // ── Righe salvate ────────────────────────────────────────────────────────

    fun loadRows(): List<InsightRecord> = InsightRecord.listFromJson(local.getString(KEY_ROWS, null))

    fun saveRows(rows: List<InsightRecord>) {
        // Le righe chiuse da più di 30 giorni non servono più a nessuno.
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600_000
        val kept = rows.filter { it.state.isOpen || (it.stateChangedAtMillis ?: it.updatedAtMillis) > cutoff }
        local.edit().putString(KEY_ROWS, InsightRecord.listToJson(kept)).apply()
    }

    // ── Allarmi ──────────────────────────────────────────────────────────────

    private suspend fun arm(plan: InsightDelivery.Plan, rows: List<InsightRecord>, familyId: String) {
        val byId = rows.associateBy { it.id }
        val wanted = mutableMapOf<String, Intent>()
        for (p in plan.notifications) {
            val row = byId[p.insightId] ?: continue
            val intent = deliveryIntent(row) ?: continue
            wanted[p.insightId] = intent.putExtra(InsightReceiver.EXTRA_AT, p.atMillis)
        }
        if (plan.digestAtMillis != null) {
            wanted[digestId(familyId)] = Intent(context, InsightReceiver::class.java).apply {
                action = InsightReceiver.ACTION_SHOW
                putExtra(InsightReceiver.EXTRA_INSIGHT_ID, digestId(familyId))
                putExtra(InsightReceiver.EXTRA_TYPE, InsightReceiver.TYPE_DIGEST)
                putExtra(InsightReceiver.EXTRA_FAMILY_ID, familyId)
                putExtra(InsightReceiver.EXTRA_COUNT, plan.digestIds.size)
                putExtra(InsightReceiver.EXTRA_AT, plan.digestAtMillis)
            }
        }

        val armed = local.getStringSet(KEY_ARMED, emptySet()).orEmpty()
        for (id in armed - wanted.keys) cancelAlarm(id)
        for ((id, intent) in wanted) {
            val pending = PendingIntent.getBroadcast(
                context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ExactAlarmScheduler.scheduleRtcWakeupAllowWhileIdle(context, intent.getLongExtra(InsightReceiver.EXTRA_AT, 0), pending)
        }
        local.edit().putStringSet(KEY_ARMED, wanted.keys).apply()
        // Risolti o scartati: anche il banner già mostrato non serve più.
        val nm = NotificationManagerCompat.from(context)
        plan.finishedIds.forEach { runCatching { nm.cancel(it.hashCode()) } }
    }

    /** Il testo si compone alla consegna, nella lingua di quel momento: qui solo i dati. */
    private suspend fun deliveryIntent(row: InsightRecord): Intent? {
        val type = row.type ?: return null
        val intent = Intent(context, InsightReceiver::class.java).apply {
            action = InsightReceiver.ACTION_SHOW
            putExtra(InsightReceiver.EXTRA_INSIGHT_ID, row.id)
            putExtra(InsightReceiver.EXTRA_TYPE, type.raw)
            putExtra(InsightReceiver.EXTRA_FAMILY_ID, row.familyId)
            putExtra(InsightReceiver.EXTRA_HIGH, row.priority == InsightPriority.HIGH)
        }
        when (type) {
            InsightType.TODO_UNASSIGNED_DUE_SOON -> {
                val todo = todoDao.getById(row.entityId) ?: return null
                val due = todo.dueAtEpochMillis ?: return null
                val uid = auth.currentUser?.uid.orEmpty()
                intent.putExtra(InsightReceiver.EXTRA_TODO_ID, todo.id)
                    .putExtra(InsightReceiver.EXTRA_CHILD_ID, todo.childId)
                    .putExtra(InsightReceiver.EXTRA_LIST_ID, todo.listId.orEmpty())
                    .putExtra(InsightReceiver.EXTRA_TITLE, todo.title)
                    .putExtra(InsightReceiver.EXTRA_DUE_AT, due)
                    .putExtra(InsightReceiver.EXTRA_HAS_TIME, todo.dueHasTime)
                    .putExtra(InsightReceiver.EXTRA_CAN_ASK, askRecipients(todo, uid).isNotEmpty())
            }
            InsightType.TREATMENT_DOSE_MISSING -> {
                val treatmentId = row.entityId.substringBefore("@")
                val t = treatmentDao.getById(treatmentId) ?: return null
                val name = if (t.petId.isNotBlank()) petDao.getById(t.petId)?.name else childDao.getById(t.childId)?.name
                intent.putExtra(InsightReceiver.EXTRA_TREATMENT_ID, t.id)
                    .putExtra(InsightReceiver.EXTRA_CHILD_ID, t.childId)
                    .putExtra(InsightReceiver.EXTRA_SUBJECT, name.orEmpty())
            }
            else -> return null
        }
        return intent
    }

    private fun cancelAlarm(id: String) {
        val intent = Intent(context, InsightReceiver::class.java).apply { action = InsightReceiver.ACTION_SHOW }
        val pending = PendingIntent.getBroadcast(
            context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }

    /** Tutto spento: avvisi disattivati, piano Free, logout. */
    fun cancelAllAlarms() {
        val armed = local.getStringSet(KEY_ARMED, emptySet()).orEmpty()
        armed.forEach { cancelAlarm(it) }
        val nm = NotificationManagerCompat.from(context)
        armed.forEach { runCatching { nm.cancel(it.hashCode()) } }
        local.edit().putStringSet(KEY_ARMED, emptySet()).apply()
    }

    // ── Misura ───────────────────────────────────────────────────────────────

    /** Un avviso con la consegna già passata è arrivato: si conta una volta. */
    private fun reportShown(rows: List<InsightRecord>, now: Long, prefs: InsightPreferences): List<InsightRecord> =
        rows.map { row ->
            val n = row.notifiedAtMillis
            val type = row.type
            if (row.shownReportedAtMillis != null || n == null || n > now || type == null) return@map row
            val digest = prefs.delivery == InsightPreferences.Delivery.EVENING_DIGEST && InsightDelivery.goesInDigest(type)
            AppAnalytics.insightShown(context, type.raw, if (digest) "digest" else "notification")
            row.copy(shownReportedAtMillis = n)
        }

    /** Avvisi già arrivati e chiusi perché qualcuno ha agito dall'app. Un tipo spento non conta. */
    private fun reportActed(rows: List<InsightRecord>, resolved: List<String>, prefs: InsightPreferences, now: Long): List<InsightRecord> {
        if (resolved.isEmpty()) return rows
        val ids = resolved.toSet()
        return rows.map { row ->
            val type = row.type
            if (row.id !in ids || row.shownReportedAtMillis == null || row.actedReportedAtMillis != null ||
                type == null || prefs.isMuted(type)
            ) return@map row
            AppAnalytics.insightActed(context, type.raw, "app", minutesSince(row.shownReportedAtMillis, now))
            row.copy(actedReportedAtMillis = now)
        }
    }

    private fun activeFamilyId(): String =
        context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE).getString("active_family_id", null).orEmpty()

    companion object {
        private const val TAG = "Insight"
        private const val PREFS = "kb_insights"
        private const val KEY_ROWS = "rows"
        private const val KEY_ARMED = "armed"
        private const val KEY_PREFS = "prefs"
        private const val KEY_PREFS_READ_AT = "prefs_read_at"
        private const val KEY_PLAN = "plan"
        private const val KEY_PLAN_FAMILY = "plan_family"
        private const val KEY_PLAN_AT = "plan_at"
        private const val REMOTE_FIELD = "insightPrefs"
        private const val WORK_NAME = "kb_insights_refresh"

        fun digestId(familyId: String) = "digest-$familyId"

        fun minutesSince(start: Long?, now: Long): Int =
            if (start == null) 0 else ((now - start) / 60_000).toInt().coerceAtLeast(0)

        /** Stesso criterio di iOS (`KBInsightActionHandler.askRecipients`) e del server. */
        fun askRecipients(scope: String, memberIds: List<String>, activeMembers: List<String>, uid: String): List<String> {
            val visible = when (KBVisibilityScope.normalized(scope)) {
                KBVisibilityScope.MEMBERS -> activeMembers.filter { it in memberIds.toSet() }
                KBVisibilityScope.ONLY_CREATOR -> emptyList()
                else -> activeMembers
            }
            return visible.filter { it.isNotBlank() && it != uid }.distinct().sorted()
        }
    }
}
