package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import it.vittorioscocca.kidbox.data.notification.TodoReminderScheduler
import it.vittorioscocca.kidbox.data.remote.todo.TodoItemRemoteChange
import it.vittorioscocca.kidbox.data.remote.todo.TodoListRemoteChange
import it.vittorioscocca.kidbox.data.remote.todo.TodoRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TodoRepository @Inject constructor(
    private val listDao: KBTodoListDao,
    private val itemDao: KBTodoItemDao,
    private val remoteStore: TodoRemoteStore,
    private val auth: FirebaseAuth,
    private val reminderScheduler: TodoReminderScheduler,
    private val familyDao: KBFamilyDao,
    private val childDao: KBChildDao,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listListener: ListenerRegistration? = null
    private var todoListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null
    private var listeningChildId: String? = null

    fun observeLists(familyId: String, childId: String): Flow<List<KBTodoListEntity>> =
        listDao.observeByFamilyAndChild(familyId, childId)

    fun observeTodos(familyId: String, childId: String): Flow<List<KBTodoItemEntity>> =
        itemDao.observeByFamilyAndChild(familyId, childId)

    fun startRealtime(
        familyId: String,
        childId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (
                    listeningFamilyId == familyId &&
                    listeningChildId == childId &&
                    listListener != null &&
                    todoListener != null
                ) {
                    KBLog.sync.debug(
                        "startRealtime SKIP already bound familyId=$familyId childId=$childId",
                        tag = "todo",
                    )
                    return@withLock
                }
                stopRealtimeLocked()
                listeningFamilyId = familyId
                listeningChildId = childId
                KBLog.sync.info("startRealtime ATTACH familyId=$familyId childId=$childId", tag = "todo")

                listListener = remoteStore.listenTodoLists(
                    familyId = familyId,
                    childId = childId,
                    onChange = { changes ->
                        KBLog.sync.debug("listenTodoLists onChange count=${changes.size}", tag = "todo")
                        scope.launch { applyListInbound(changes) }
                    },
                    onError = { err ->
                        KBLog.sync.error(
                            "listenTodoLists onError familyId=$familyId childId=$childId",
                            tag = "todo",
                            throwable = err,
                        )
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )

                todoListener = remoteStore.listenTodos(
                    familyId = familyId,
                    childId = childId,
                    onChange = { changes ->
                        KBLog.sync.debug("listenTodos onChange count=${changes.size}", tag = "todo")
                        scope.launch { applyTodoInbound(changes) }
                    },
                    onError = { err ->
                        KBLog.sync.error(
                            "listenTodos onError familyId=$familyId childId=$childId",
                            tag = "todo",
                            throwable = err,
                        )
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch { realtimeMutex.withLock { stopRealtimeLocked() } }
    }

    suspend fun addList(familyId: String, childId: String, name: String): String {
        KBLog.sync.info("addList START familyId=$familyId childId=$childId", tag = "todo")
        if (childId.isBlank()) ensureNoChildPlaceholder()
        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        val local = KBTodoListEntity(
            id = id,
            familyId = familyId,
            childId = childId,
            name = name,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            isDeleted = false,
        )
        try {
            listDao.upsert(local)
        } catch (e: Exception) {
            KBLog.sync.error("addList Room upsert FAILED listId=$id familyId=$familyId childId=$childId", tag = "todo", throwable = e)
            throw e
        }
        try {
            remoteStore.upsertList(local)
        } catch (e: Exception) {
            KBLog.sync.error("addList remote upsert FAILED listId=$id", tag = "todo", throwable = e)
            throw e
        }
        KBLog.sync.info("addList OK listId=$id", tag = "todo")
        return id
    }

    suspend fun updateListName(listId: String, name: String) {
        val existing = listDao.getById(listId) ?: return
        val local = existing.copy(
            name = name,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        listDao.upsert(local)
        remoteStore.upsertList(local)
    }

    suspend fun deleteList(listId: String) {
        val list = listDao.getById(listId) ?: return
        val todos = itemDao.getByFamilyAndChild(list.familyId, list.childId).filter { it.listId == listId }
        todos.forEach { todo ->
            reminderScheduler.cancel(todo.reminderId)
            remoteStore.softDeleteTodo(todo.familyId, todo.id)
            itemDao.deleteById(todo.id)
        }
        remoteStore.softDeleteList(list.familyId, listId)
        listDao.deleteById(listId)
    }

    suspend fun addTodo(
        familyId: String,
        childId: String,
        listId: String,
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        assignedTo: String?,
        priorityRaw: Int?,
        reminderEnabled: Boolean,
        visibilityScope: String = KBVisibilityScope.FAMILY,
        visibilityMemberIds: List<String> = emptyList(),
    ) {
        KBLog.sync.info("addTodo START familyId=$familyId childId=$childId listId=$listId", tag = "todo")
        if (childId.isBlank()) ensureNoChildPlaceholder()
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val isFirstUse = itemDao.countByFamilyId(familyId) == 0
        val normalizedScope = KBVisibilityScope.normalized(visibilityScope)
        val memberIdsJson = encodeStringList(
            if (normalizedScope == KBVisibilityScope.MEMBERS) visibilityMemberIds else emptyList(),
        )
        val todo = KBTodoItemEntity(
            id = java.util.UUID.randomUUID().toString(),
            familyId = familyId,
            childId = childId,
            title = title,
            notes = notes,
            dueAtEpochMillis = dueAtEpochMillis,
            isDone = false,
            doneAtEpochMillis = null,
            doneBy = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            isDeleted = false,
            listId = listId,
            reminderEnabled = reminderEnabled && dueAtEpochMillis != null,
            reminderId = null,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
            assignedTo = assignedTo,
            createdBy = uid,
            priorityRaw = priorityRaw ?: 0,
            visibilityScope = normalizedScope,
            visibilityMemberIdsJson = memberIdsJson,
        )
        val reminderId = if (todo.reminderEnabled && dueAtEpochMillis != null) {
            reminderScheduler.schedule(
                todoId = todo.id,
                title = title,
                dueAtEpochMillis = dueAtEpochMillis,
                familyId = familyId,
                childId = childId,
                listId = listId,
            )
        } else {
            null
        }
        val persisted = todo.copy(reminderId = reminderId)
        try {
            itemDao.upsert(persisted)
        } catch (e: Exception) {
            KBLog.sync.error(
                "addTodo Room upsert FAILED todoId=${persisted.id} familyId=$familyId childId=$childId",
                tag = "todo",
                throwable = e,
            )
            throw e
        }
        try {
            remoteStore.upsertTodo(persisted)
        } catch (e: Exception) {
            KBLog.sync.error("addTodo remote upsert FAILED todoId=${persisted.id}", tag = "todo", throwable = e)
            throw e
        }
        itemDao.upsert(persisted.copy(syncStateRaw = KBSyncState.SYNCED.rawValue))
        KBLog.sync.info("addTodo OK todoId=${persisted.id}", tag = "todo")
        AppAnalytics.contentCreated(appContext, "todo")
        if (isFirstUse) {
            AppAnalytics.featureFirstUse(appContext, feature = "todo")
        }
    }

    suspend fun updateTodo(
        todoId: String,
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        assignedTo: String?,
        priorityRaw: Int?,
        reminderEnabled: Boolean,
        /** Se null, mantiene scope e membri già salvati sul todo (evita regressioni nei flussi tipo promemoria). */
        visibilityScope: String? = null,
        visibilityMemberIds: List<String>? = null,
    ) {
        val existing = itemDao.getById(todoId) ?: return
        if (!existing.reminderId.isNullOrBlank() && (!reminderEnabled || dueAtEpochMillis == null)) {
            reminderScheduler.cancel(existing.reminderId)
        }
        val normalizedScope = visibilityScope?.let { KBVisibilityScope.normalized(it) }
            ?: existing.visibilityScope
        val memberIdsJson = if (visibilityMemberIds != null) {
            encodeStringList(
                if (normalizedScope == KBVisibilityScope.MEMBERS) visibilityMemberIds else emptyList(),
            )
        } else {
            existing.visibilityMemberIdsJson
        }
        val local = existing.copy(
            title = title,
            notes = notes,
            dueAtEpochMillis = dueAtEpochMillis,
            assignedTo = assignedTo,
            priorityRaw = priorityRaw ?: 0,
            reminderEnabled = reminderEnabled && dueAtEpochMillis != null,
            updatedAtEpochMillis = System.currentTimeMillis(),
            updatedBy = auth.currentUser?.uid ?: existing.updatedBy,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
            visibilityScope = normalizedScope,
            visibilityMemberIdsJson = memberIdsJson,
        )
        val reminderId = if (local.reminderEnabled && dueAtEpochMillis != null) {
            reminderScheduler.schedule(
                todoId = local.id,
                title = title,
                dueAtEpochMillis = dueAtEpochMillis,
                familyId = local.familyId,
                childId = local.childId,
                listId = local.listId,
            )
        } else {
            null
        }
        val persisted = local.copy(reminderId = reminderId)
        itemDao.upsert(persisted)
        remoteStore.upsertTodo(persisted)
        itemDao.upsert(persisted.copy(syncStateRaw = KBSyncState.SYNCED.rawValue))
    }

    suspend fun toggleTodoDone(todoId: String) {
        val existing = itemDao.getById(todoId) ?: return
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val done = !existing.isDone
        if (done) reminderScheduler.cancel(existing.reminderId)
        val local = existing.copy(
            isDone = done,
            doneAtEpochMillis = if (done) now else null,
            doneBy = if (done) uid else null,
            reminderEnabled = if (done) false else existing.reminderEnabled,
            reminderId = if (done) null else existing.reminderId,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        itemDao.upsert(local)
        remoteStore.upsertTodo(local)
        itemDao.upsert(local.copy(syncStateRaw = KBSyncState.SYNCED.rawValue))
    }

    suspend fun deleteTodo(todoId: String) {
        val existing = itemDao.getById(todoId) ?: return
        reminderScheduler.cancel(existing.reminderId)
        remoteStore.softDeleteTodo(existing.familyId, todoId)
        itemDao.deleteById(todoId)
    }

    private suspend fun applyListInbound(changes: List<TodoListRemoteChange>) {
        KBLog.sync.debug("applyListInbound START changes=${changes.size}", tag = "todo")
        changes.forEach { change ->
            try {
                when (change) {
                    is TodoListRemoteChange.Remove -> {
                        KBLog.sync.debug("applyListInbound REMOVE id=${change.id}", tag = "todo")
                        listDao.deleteById(change.id)
                    }
                    is TodoListRemoteChange.Upsert -> {
                        val dto = change.dto
                        KBLog.sync.debug(
                            "applyListInbound UPSERT id=${dto.id} familyId=${dto.familyId} childId=${dto.childId} isDeleted=${dto.isDeleted}",
                            tag = "todo",
                        )
                        if (dto.isDeleted) {
                            listDao.deleteById(dto.id)
                            return@forEach
                        }
                        ensureFamilyExists(dto.familyId)
                        // childId può essere "" quando la famiglia non ha ancora un bambino:
                        // non è un riferimento rotto, è lo stesso scoping valido usato da iOS/web.
                        // Il controllo serve solo a scartare un childId reale ma non ancora
                        // sincronizzato localmente — non a bloccare il caso "nessun bambino".
                        if (dto.childId.isBlank()) {
                            ensureNoChildPlaceholder()
                        } else if (childDao.getById(dto.childId) == null) {
                            KBLog.sync.debug(
                                "applyListInbound SKIP unknown local child childId=${dto.childId}",
                                tag = "todo",
                            )
                            return@forEach
                        }
                        val local = listDao.getById(dto.id)
                        if (local != null && (dto.updatedAtEpochMillis ?: 0L) < local.updatedAtEpochMillis) {
                            return@forEach
                        }
                        val now = System.currentTimeMillis()
                        listDao.upsert(
                            KBTodoListEntity(
                                id = dto.id,
                                familyId = dto.familyId,
                                childId = dto.childId,
                                name = dto.name,
                                createdAtEpochMillis = local?.createdAtEpochMillis ?: (dto.updatedAtEpochMillis ?: now),
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                                isDeleted = false,
                            ),
                        )
                        KBLog.sync.debug("applyListInbound SAVED id=${dto.id}", tag = "todo")
                    }
                }
            } catch (e: Exception) {
                KBLog.sync.error("applyListInbound FAILED change=$change", tag = "todo", throwable = e)
            }
        }
    }

    private suspend fun applyTodoInbound(changes: List<TodoItemRemoteChange>) {
        KBLog.sync.debug("applyTodoInbound START changes=${changes.size}", tag = "todo")
        changes.forEach { change ->
            try {
            when (change) {
                is TodoItemRemoteChange.Remove -> {
                    KBLog.sync.debug("applyTodoInbound REMOVE id=${change.id}", tag = "todo")
                    itemDao.deleteById(change.id)
                }
                is TodoItemRemoteChange.Upsert -> {
                    val dto = change.dto
                    KBLog.sync.debug(
                        "applyTodoInbound UPSERT id=${dto.id} familyId=${dto.familyId} childId=${dto.childId} isDeleted=${dto.isDeleted} isDone=${dto.isDone}",
                        tag = "todo",
                    )
                    if (dto.isDeleted) {
                        itemDao.deleteById(dto.id)
                        return@forEach
                    }
                    val local = itemDao.getById(dto.id)
                    if (
                        local != null &&
                        (local.isDeleted || KBSyncState.fromRaw(local.syncStateRaw ?: 0) == KBSyncState.PENDING_DELETE)
                    ) {
                        return@forEach
                    }
                    // LWW sul timestamp solo se questo device ha ancora modifiche da inviare: altrimenti
                    // lo skew tra millis locali e serverTimestamp Firestore blocca gli aggiornamenti remoti
                    // (es. iOS cambia visibilità → Android resta sulla copia locale fino al re-entry).
                    val localSync = local?.syncStateRaw?.let(KBSyncState::fromRaw) ?: KBSyncState.SYNCED
                    val localHasPendingOutbound =
                        local != null &&
                            (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.ERROR)
                    if (
                        localHasPendingOutbound &&
                        (dto.updatedAtEpochMillis ?: 0L) < local.updatedAtEpochMillis
                    ) {
                        return@forEach
                    }
                    ensureFamilyExists(dto.familyId)
                    if (dto.childId.isBlank()) {
                        ensureNoChildPlaceholder()
                    } else if (childDao.getById(dto.childId) == null) {
                        KBLog.sync.debug(
                            "applyTodoInbound SKIP unknown local child childId=${dto.childId} todoId=${dto.id}",
                            tag = "todo",
                        )
                        return@forEach
                    }
                    val now = System.currentTimeMillis()
                    val remoteScope = KBVisibilityScope.normalized(dto.visibilityScope)
                    val remoteMemberIds = dto.visibilityMemberIds
                    val safeListId = resolveReferencedListId(dto.listId)
                    itemDao.upsert(
                        KBTodoItemEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            childId = dto.childId,
                            title = dto.title,
                            notes = dto.notes,
                            dueAtEpochMillis = dto.dueAtEpochMillis,
                            isDone = dto.isDone,
                            doneAtEpochMillis = dto.doneAtEpochMillis,
                            doneBy = dto.doneBy,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: (dto.updatedAtEpochMillis ?: now),
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                            isDeleted = false,
                            listId = safeListId,
                            reminderEnabled = local?.reminderEnabled ?: false,
                            reminderId = local?.reminderId,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                            assignedTo = dto.assignedTo,
                            createdBy = local?.createdBy ?: dto.createdBy,
                            priorityRaw = dto.priorityRaw ?: 0,
                            visibilityScope = remoteScope,
                            visibilityMemberIdsJson = encodeStringList(remoteMemberIds),
                        ),
                    )
                    KBLog.sync.debug("applyTodoInbound SAVED id=${dto.id}", tag = "todo")
                }
            }
            } catch (e: Exception) {
                KBLog.sync.error("applyTodoInbound FAILED change=$change", tag = "todo", throwable = e)
            }
        }
    }

    /** Evita SQLITE_CONSTRAINT_FOREIGNKEY: liste possono arrivare dopo i todo nello snapshot. */
    private suspend fun resolveReferencedListId(listId: String?): String? {
        val id = listId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return if (listDao.getById(id) != null) id else null
    }

    private suspend fun ensureFamilyExists(familyId: String) {
        if (familyId.isBlank()) return
        if (familyDao.getById(familyId) != null) return
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = "Famiglia",
                heroPhotoURL = null,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = uid,
                updatedBy = uid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            ),
        )
    }

    /**
     * `childId` può essere "" quando la famiglia non ha ancora un bambino (coerente con
     * iOS/web). Ma `kb_todo_items.childId` e `kb_todo_lists.childId` hanno una foreign key
     * reale su `kb_children.id`: senza una riga con id="" l'insert fallisce con
     * SQLiteConstraintException e il to-do/lista non viene mai salvato, né in locale né
     * verso remoto. Questo crea/garantisce quella riga segnaposto (nessun familyId
     * specifico: la FK è a singola colonna, va bene per qualunque famiglia).
     */
    private suspend fun ensureNoChildPlaceholder() {
        if (childDao.getById("") != null) {
            KBLog.sync.debug("ensureNoChildPlaceholder already present", tag = "todo")
            return
        }
        val now = System.currentTimeMillis()
        try {
            childDao.upsert(
                KBChildEntity(
                    id = "",
                    familyId = null,
                    name = "",
                    birthDateEpochMillis = null,
                    weightKg = null,
                    heightCm = null,
                    createdBy = "local",
                    createdAtEpochMillis = now,
                    updatedBy = null,
                    updatedAtEpochMillis = null,
                ),
            )
            KBLog.sync.info("ensureNoChildPlaceholder CREATED", tag = "todo")
        } catch (e: Exception) {
            KBLog.sync.error("ensureNoChildPlaceholder FAILED", tag = "todo", throwable = e)
            throw e
        }
    }

    private fun stopRealtimeLocked() {
        listListener?.remove()
        todoListener?.remove()
        listListener = null
        todoListener = null
        listeningFamilyId = null
        listeningChildId = null
    }
}
