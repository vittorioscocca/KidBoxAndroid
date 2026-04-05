package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_routine_checks",
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
            entity = KBRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index("childId"), Index("routineId"), Index("dayKey")],
)
data class KBRoutineCheckEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val routineId: String,
    val dayKey: String,
    val checkedAtEpochMillis: Long,
    val checkedBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
