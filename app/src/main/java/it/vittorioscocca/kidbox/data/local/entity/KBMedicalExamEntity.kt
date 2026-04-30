package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_medical_exams",
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
            entity = KBMedicalVisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescribingVisitId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId"), Index("childId"), Index("prescribingVisitId")],
)
data class KBMedicalExamEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val isUrgent: Boolean,
    val deadlineEpochMillis: Long?,
    val preparation: String?,
    val notes: String?,
    val location: String?,
    val statusRaw: String,
    val resultText: String?,
    val resultDateEpochMillis: Long?,
    val prescribingVisitId: String?,
    val reminderOn: Boolean = false,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val createdBy: String,
)
