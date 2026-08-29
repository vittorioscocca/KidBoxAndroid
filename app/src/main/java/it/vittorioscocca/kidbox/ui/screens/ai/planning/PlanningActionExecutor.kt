package it.vittorioscocca.kidbox.ui.screens.ai.planning

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PlanningActionExecutor @Inject constructor(
    private val groceryItemDao: KBGroceryItemDao,
    private val todoItemDao: KBTodoItemDao,
    private val todoListDao: KBTodoListDao,
    private val noteDao: KBNoteDao,
    private val calendarEventDao: KBCalendarEventDao,
    private val childDao: KBChildDao,
    private val auth: FirebaseAuth,
    private val reminderService: PlanningReminderService,
) {
    private companion object {
        const val SYNC_PENDING_UPSERT = 1
    }

    suspend fun execute(
        familyId: String,
        actions: List<PlanningExecutableActionDto>,
        pendingGroceryNames: Set<String>,
    ): String? = withContext(Dispatchers.IO) {
        if (actions.isEmpty()) return@withContext null
        val uid = auth.currentUser?.uid ?: "ai-agent"
        val children = childDao.getChildrenByFamilyId(familyId)
        val lines = mutableListOf<String>()
        val grocerySeen = pendingGroceryNames.map { it.trim().lowercase() }.toMutableSet()

        for (action in actions) {
            when (action.type) {
                "grocery_add" -> {
                    val added = addGrocery(familyId, uid, action.items.orEmpty(), action.category, grocerySeen)
                    if (added > 0) lines += "Lista spesa: $added articol${if (added == 1) "o" else "i"} aggiunt${if (added == 1) "o" else "i"}."
                }
                "todo_add" -> {
                    val title = action.title?.trim().orEmpty()
                    if (title.isNotEmpty() && addTodo(familyId, uid, title, action.notes, action.dueAt, action.childId, action.listId, children)) {
                        lines += "To-do aggiunto: \"$title\"."
                    }
                }
                "event_add" -> {
                    val title = action.title?.trim().orEmpty()
                    val start = parseEpoch(action.startAt) ?: parseEpoch(action.dueAt)
                    if (title.isNotEmpty() && start != null &&
                        addEvent(familyId, uid, title, start, parseEpoch(action.endAt), action.isAllDay == true, action.notes, action.childId, children)
                    ) {
                        lines += "Evento aggiunto: \"$title\"."
                    }
                }
                "note_add" -> {
                    val title = action.title?.trim().orEmpty().ifBlank {
                        action.body?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                    }
                    if (title.isNotEmpty() && addNote(familyId, uid, title, action.body ?: title)) {
                        lines += "Nota creata: \"$title\"."
                    }
                }
                "health_reminder" -> {
                    val title = action.title?.trim().orEmpty()
                    if (title.isNotEmpty()) {
                        val due = parseEpoch(action.dueAt) ?: (System.currentTimeMillis() + 86_400_000L)
                        val result = reminderService.scheduleFreeText(
                            familyId = familyId,
                            title = title,
                            dueAtEpochMillis = due,
                            childId = action.childId,
                            listId = action.listId,
                        )
                        lines += result
                    }
                }
            }
        }
        if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    private suspend fun addGrocery(
        familyId: String,
        uid: String,
        items: List<String>,
        category: String?,
        seen: MutableSet<String>,
    ): Int {
        val now = System.currentTimeMillis()
        var added = 0
        for (raw in items) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            val key = name.lowercase()
            if (seen.contains(key)) continue
            seen.add(key)
            groceryItemDao.upsert(
                KBGroceryItemEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = familyId,
                    name = name,
                    category = category,
                    notes = null,
                    quantity = null,
                    isPurchased = false,
                    purchasedAtEpochMillis = null,
                    purchasedBy = null,
                    isDeleted = false,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    updatedBy = uid,
                    createdBy = uid,
                    syncStateRaw = SYNC_PENDING_UPSERT,
                    lastSyncError = null,
                ),
            )
            added++
        }
        return added
    }

    private suspend fun addTodo(
        familyId: String,
        uid: String,
        title: String,
        notes: String?,
        dueAt: String?,
        childId: String?,
        listId: String?,
        children: List<it.vittorioscocca.kidbox.data.local.entity.KBChildEntity>,
    ): Boolean {
        val target = resolveTodoTarget(familyId, childId, listId, children)
        val now = System.currentTimeMillis()
        todoItemDao.upsert(
            KBTodoItemEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                childId = target.childId,
                title = title,
                notes = notes,
                dueAtEpochMillis = parseEpoch(dueAt),
                isDone = false,
                doneAtEpochMillis = null,
                doneBy = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                listId = target.listId,
                reminderEnabled = false,
                reminderId = null,
                syncStateRaw = SYNC_PENDING_UPSERT,
                lastSyncError = null,
                assignedTo = null,
                createdBy = uid,
                priorityRaw = 0,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = "[]",
            ),
        )
        return true
    }

    private suspend fun addEvent(
        familyId: String,
        uid: String,
        title: String,
        start: Long,
        end: Long?,
        isAllDay: Boolean,
        notes: String?,
        childId: String?,
        children: List<it.vittorioscocca.kidbox.data.local.entity.KBChildEntity>,
    ): Boolean {
        val now = System.currentTimeMillis()
        calendarEventDao.upsert(
            KBCalendarEventEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                childId = childId ?: children.firstOrNull()?.id,
                title = title,
                notes = notes,
                location = null,
                startDateEpochMillis = start,
                endDateEpochMillis = end ?: (start + 3_600_000L),
                isAllDay = isAllDay,
                categoryRaw = "family",
                recurrenceRaw = "none",
                reminderMinutes = null,
                linkedHealthItemId = null,
                linkedHealthItemType = null,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = "[]",
                isDeleted = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                createdBy = uid,
                syncStateRaw = SYNC_PENDING_UPSERT,
                lastSyncError = null,
            ),
        )
        return true
    }

    private suspend fun addNote(familyId: String, uid: String, title: String, body: String): Boolean {
        val now = System.currentTimeMillis()
        noteDao.upsert(
            KBNoteEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                title = title,
                body = body,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = "[]",
                createdBy = uid,
                createdByName = "",
                updatedBy = uid,
                updatedByName = "",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                isDeleted = false,
                syncStateRaw = SYNC_PENDING_UPSERT,
                lastSyncError = null,
            ),
        )
        return true
    }

    private data class TodoTarget(val childId: String, val listId: String?)

    private suspend fun resolveTodoTarget(
        familyId: String,
        childId: String?,
        listId: String?,
        children: List<it.vittorioscocca.kidbox.data.local.entity.KBChildEntity>,
    ): TodoTarget {
        val resolvedChild = childId ?: children.firstOrNull()?.id ?: familyId
        if (!listId.isNullOrBlank()) return TodoTarget(resolvedChild, listId)
        val lists = todoListDao.getByFamilyAndChild(familyId, resolvedChild)
        return TodoTarget(resolvedChild, lists.firstOrNull()?.id)
    }

    private fun parseEpoch(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }
}
