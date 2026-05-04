package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBTreatmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["treatmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index("childId"), Index("treatmentId")],
)
data class KBDoseLogEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val treatmentId: String,
    val dayNumber: Int,
    val slotIndex: Int,
    val scheduledTime: String,
    val takenAtEpochMillis: Long?,
    val taken: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val syncStatus: Int,
    val lastSyncError: String?,
)
