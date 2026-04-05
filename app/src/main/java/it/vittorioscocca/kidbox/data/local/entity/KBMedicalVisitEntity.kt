package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_medical_visits",
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
    indices = [Index("familyId"), Index("childId"), Index("dateEpochMillis")],
)
data class KBMedicalVisitEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val dateEpochMillis: Long,
    val doctorName: String?,
    val doctorSpecializationRaw: String?,
    val travelDetailsJson: String?,
    val reason: String,
    val diagnosis: String?,
    val recommendations: String?,
    val linkedTreatmentIdsJson: String,
    val linkedExamIdsJson: String,
    val asNeededDrugsJson: String?,
    val therapyTypesJson: String,
    val prescribedExamsJson: String?,
    val photoUrlsJson: String,
    val notes: String?,
    val nextVisitDateEpochMillis: Long?,
    val nextVisitReason: String?,
    val visitStatusRaw: String?,
    val reminderOn: Boolean,
    val nextVisitReminderOn: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
