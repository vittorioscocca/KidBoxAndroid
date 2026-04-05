package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_todo_items",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBTodoListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId"), Index("childId"), Index("listId")],
)
data class KBTodoItemEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val notes: String?,
    val dueAtEpochMillis: Long?,
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
