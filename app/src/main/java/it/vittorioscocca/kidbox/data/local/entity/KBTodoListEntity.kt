package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_todo_lists",
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
    ],
    indices = [Index("familyId"), Index("childId")],
)
data class KBTodoListEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
    /**
     * Chi ha creato la lista. Nullable perché le liste esistenti prima di questo
     * campo non lo hanno: in quel caso [TodoListExposure] le tratta come
     * pubbliche, per non farle sparire a chi le usa già.
     */
    val createdBy: String? = null,
)
