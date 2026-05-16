package it.vittorioscocca.kidbox.ui.screens.ai.planning

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanningReminderService @Inject constructor(
    private val todoItemDao: KBTodoItemDao,
    private val todoListDao: KBTodoListDao,
    private val childDao: KBChildDao,
    private val auth: FirebaseAuth,
) {
    suspend fun schedule(context: PlanningReminderContext): String = when (context) {
        is PlanningReminderContext.FreeText -> scheduleFreeText(
            familyId = context.familyId,
            title = context.title,
            dueAtEpochMillis = context.dueAtEpochMillis,
            childId = context.childId,
            listId = context.listId,
        )
        is PlanningReminderContext.ExistingTodo -> {
            "Promemoria registrato per \"${context.title}\"."
        }
    }

    suspend fun scheduleFreeText(
        familyId: String,
        title: String,
        dueAtEpochMillis: Long,
        childId: String? = null,
        listId: String? = null,
    ): String {
        val uid = auth.currentUser?.uid ?: "ai-agent"
        val children = childDao.getChildrenByFamilyId(familyId)
        val resolvedChild = childId ?: children.firstOrNull()?.id ?: familyId
        val resolvedList = listId?.takeIf { it.isNotBlank() }
            ?: todoListDao.getByFamilyAndChild(familyId, resolvedChild).firstOrNull()?.id
        val now = System.currentTimeMillis()
        todoItemDao.upsert(
            KBTodoItemEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                childId = resolvedChild,
                title = title,
                notes = null,
                dueAtEpochMillis = dueAtEpochMillis,
                isDone = false,
                doneAtEpochMillis = null,
                doneBy = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                listId = resolvedList,
                reminderEnabled = true,
                reminderId = null,
                syncStateRaw = 1,
                lastSyncError = null,
                assignedTo = null,
                createdBy = uid,
                priorityRaw = 0,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = "[]",
            ),
        )
        return "Promemoria creato: \"$title\"."
    }
}
