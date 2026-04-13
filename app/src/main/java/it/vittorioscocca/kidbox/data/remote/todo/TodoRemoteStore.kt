package it.vittorioscocca.kidbox.data.remote.todo

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class TodoListRemoteDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
)

data class TodoItemRemoteDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val listId: String?,
    val isDone: Boolean,
    val isDeleted: Boolean,
    val notes: String?,
    val dueAtEpochMillis: Long?,
    val doneAtEpochMillis: Long?,
    val doneBy: String?,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val assignedTo: String?,
    val createdBy: String?,
    val priorityRaw: Int?,
)

sealed interface TodoListRemoteChange {
    data class Upsert(val dto: TodoListRemoteDto) : TodoListRemoteChange
    data class Remove(val id: String) : TodoListRemoteChange
}

sealed interface TodoItemRemoteChange {
    data class Upsert(val dto: TodoItemRemoteDto) : TodoItemRemoteChange
    data class Remove(val id: String) : TodoItemRemoteChange
}

@Singleton
class TodoRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    fun listenTodoLists(
        familyId: String,
        childId: String,
        onChange: (List<TodoListRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families").document(familyId).collection("todoLists")
            .whereEqualTo("childId", childId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener(
                MetadataChanges.EXCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                    } else if (snap != null) {
                        val changes = snap.documentChanges.mapNotNull { diff ->
                            val doc = diff.document
                            val d = doc.data
                            val name = (d["name"] as? String)?.trim().orEmpty()
                            val cid = (d["childId"] as? String)?.trim().orEmpty()
                            if (name.isEmpty() || cid.isEmpty()) {
                                null
                            } else {
                                val dto = TodoListRemoteDto(
                                    id = doc.id,
                                    familyId = familyId,
                                    childId = cid,
                                    name = name,
                                    isDeleted = d["isDeleted"] as? Boolean ?: false,
                                    updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                )
                                when (diff.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED,
                                    -> TodoListRemoteChange.Upsert(dto)

                                    DocumentChange.Type.REMOVED -> TodoListRemoteChange.Remove(doc.id)
                                }
                            }
                        }
                        if (changes.isNotEmpty()) onChange(changes)
                    }
                },
            )
    }

    fun listenTodos(
        familyId: String,
        childId: String,
        onChange: (List<TodoItemRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families").document(familyId).collection("todos")
            .whereEqualTo("childId", childId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                    } else if (snap != null) {
                        val changes = snap.documentChanges.mapNotNull { diff ->
                            val doc = diff.document
                            val d = doc.data
                            val title = (d["title"] as? String)?.trim().orEmpty()
                            val cid = (d["childId"] as? String)?.trim().orEmpty()
                            if (title.isEmpty() || cid.isEmpty()) {
                                null
                            } else {
                                val dto = TodoItemRemoteDto(
                                    id = doc.id,
                                    familyId = familyId,
                                    childId = cid,
                                    title = title,
                                    listId = (d["listId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    isDone = d["isDone"] as? Boolean ?: false,
                                    isDeleted = d["isDeleted"] as? Boolean ?: false,
                                    notes = (d["notes"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    dueAtEpochMillis = (d["dueAt"] as? Timestamp)?.toDate()?.time,
                                    doneAtEpochMillis = (d["doneAt"] as? Timestamp)?.toDate()?.time,
                                    doneBy = d["doneBy"] as? String,
                                    updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                    updatedBy = d["updatedBy"] as? String,
                                    assignedTo = d["assignedTo"] as? String,
                                    createdBy = d["createdBy"] as? String,
                                    priorityRaw = (d["priority"] as? Number)?.toInt(),
                                )
                                when (diff.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED,
                                    -> TodoItemRemoteChange.Upsert(dto)

                                    DocumentChange.Type.REMOVED -> TodoItemRemoteChange.Remove(doc.id)
                                }
                            }
                        }
                        if (changes.isNotEmpty()) onChange(changes)
                    }
                },
            )
    }

    suspend fun upsertList(list: KBTodoListEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families").document(list.familyId).collection("todoLists").document(list.id)
            .set(
                mapOf(
                    "childId" to list.childId,
                    "name" to list.name,
                    "isDeleted" to false,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
    }

    suspend fun softDeleteList(familyId: String, listId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families").document(familyId).collection("todoLists").document(listId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
    }

    suspend fun upsertTodo(todo: KBTodoItemEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val payload = mutableMapOf<String, Any?>(
            "childId" to todo.childId,
            "title" to todo.title,
            "listId" to (todo.listId ?: ""),
            "isDone" to todo.isDone,
            "isDeleted" to false,
            "notes" to todo.notes,
            "doneBy" to todo.doneBy,
            "assignedTo" to todo.assignedTo,
            "priority" to (todo.priorityRaw ?: 0),
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        payload["dueAt"] = todo.dueAtEpochMillis?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
        payload["doneAt"] = todo.doneAtEpochMillis?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
        if (!todo.createdBy.isNullOrBlank()) payload["createdBy"] = todo.createdBy
        db.collection("families").document(todo.familyId).collection("todos").document(todo.id)
            .set(payload, SetOptions.merge())
            .await()
    }

    suspend fun softDeleteTodo(familyId: String, todoId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families").document(familyId).collection("todos").document(todoId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
    }
}
