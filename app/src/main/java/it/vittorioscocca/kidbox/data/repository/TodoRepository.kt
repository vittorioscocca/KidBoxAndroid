package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import it.vittorioscocca.kidbox.data.notification.TodoReminderScheduler
import it.vittorioscocca.kidbox.data.remote.todo.TodoItemRemoteChange
import it.vittorioscocca.kidbox.data.remote.todo.TodoListRemoteChange
import it.vittorioscocca.kidbox.data.remote.todo.TodoRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBSyncState
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
                    return@withLock
                }
                stopRealtimeLocked()
                listeningFamilyId = familyId
                listeningChildId = childId

                listListener = remoteStore.listenTodoLists(
                    familyId = familyId,
                    childId = childId,
                    onChange = { changes -> scope.launch { applyListInbound(changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )

                todoListener = remoteStore.listenTodos(
                    familyId = familyId,
                    childId = childId,
                    onChange = { changes -> scope.launch { applyTodoInbound(changes) } },
                    onError = { err ->
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
        listDao.upsert(local)
        remoteStore.upsertList(local)
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
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
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
        itemDao.upsert(persisted)
        remoteStore.upsertTodo(persisted)
        itemDao.upsert(persisted.copy(syncStateRaw = KBSyncState.SYNCED.rawValue))
    }

    suspend fun updateTodo(
        todoId: String,
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        assignedTo: String?,
        priorityRaw: Int?,
        reminderEnabled: Boolean,
    ) {
        val existing = itemDao.getById(todoId) ?: return
        if (!existing.reminderId.isNullOrBlank() && (!reminderEnabled || dueAtEpochMillis == null)) {
            reminderScheduler.cancel(existing.reminderId)
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
        changes.forEach { change ->
            when (change) {
                is TodoListRemoteChange.Remove -> listDao.deleteById(change.id)
                is TodoListRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        listDao.deleteById(dto.id)
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
                }
            }
        }
    }

    private suspend fun applyTodoInbound(changes: List<TodoItemRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is TodoItemRemoteChange.Remove -> itemDao.deleteById(change.id)
                is TodoItemRemoteChange.Upsert -> {
                    val dto = change.dto
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
                    if (local != null && (dto.updatedAtEpochMillis ?: 0L) < local.updatedAtEpochMillis) {
                        return@forEach
                    }
                    val now = System.currentTimeMillis()
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
                            listId = dto.listId,
                            reminderEnabled = local?.reminderEnabled ?: false,
                            reminderId = local?.reminderId,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                            assignedTo = dto.assignedTo,
                            createdBy = local?.createdBy ?: dto.createdBy,
                            priorityRaw = dto.priorityRaw ?: 0,
                        ),
                    )
                }
            }
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
