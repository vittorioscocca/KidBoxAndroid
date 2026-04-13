package it.vittorioscocca.kidbox.ui.screens.todo

import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity

data class TodoMemberUi(
    val uid: String,
    val displayName: String,
)

data class TodoHomeUiState(
    val familyId: String = "",
    val childId: String = "",
    val currentUid: String = "",
    val lists: List<KBTodoListEntity> = emptyList(),
    val todos: List<KBTodoItemEntity> = emptyList(),
    val members: List<TodoMemberUi> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val activeTodos: List<KBTodoItemEntity> get() = todos.filter { !it.isDone }
    val todayCount: Int
        get() {
            val now = System.currentTimeMillis()
            val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = dayStart + 24L * 60L * 60L * 1000L
            return activeTodos.count { due ->
                val d = due.dueAtEpochMillis ?: return@count false
                d in dayStart until dayEnd && d >= now - 24L * 60L * 60L * 1000L
            }
        }
    val allCount: Int get() = activeTodos.size
    val completedCount: Int get() = todos.count { it.isDone }
    val notCompletedCount: Int get() = todos.count { !it.isDone }
}

enum class TodoSmartKind(val raw: String) {
    TODAY("today"),
    ALL("all"),
    ASSIGNED_TO_ME("assigned_to_me"),
    COMPLETED("completed"),
    NOT_ASSIGNED_TO_ME("not_assigned_to_me"),
    NOT_COMPLETED("not_completed"),
    ;

    companion object {
        fun fromRaw(raw: String): TodoSmartKind = entries.firstOrNull { it.raw == raw } ?: ALL
    }
}
