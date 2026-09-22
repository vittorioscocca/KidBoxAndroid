package it.vittorioscocca.kidbox.domain.model

/** Voce todo — allineato a [KBTodoItem] iOS. */
data class KBTodoItem(
    val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val notes: String?,
    val dueAtEpochMillis: Long?,
    /** `false` = scadenza senza orario: l'avviso suona alle 9:00. */
    val dueHasTime: Boolean = true,
    val isDone: Boolean,
    val doneAtEpochMillis: Long?,
    val doneBy: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
    val listId: String?,
    val reminderEnabled: Boolean,
    val reminderId: String?,
    val syncStateRaw: Int?,
    val lastSyncError: String?,
    val assignedTo: String?,
    val createdBy: String?,
    val priorityRaw: Int?,
)
