package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_treatments",
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
data class KBTreatmentEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val drugName: String,
    val activeIngredient: String?,
    val dosageValue: Double,
    val dosageUnit: String,
    val isLongTerm: Boolean,
    val durationDays: Int,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long?,
    val dailyFrequency: Int,
    val scheduleTimesData: String,
    val isActive: Boolean,
    val notes: String?,
    val reminderEnabled: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStatus: Int,
    val lastSyncError: String?,
    val syncStateRaw: Int,
)
