package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_pediatric_profiles",
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
data class KBPediatricProfileEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val emergencyContactsJson: String?,
    val bloodGroup: String?,
    val allergies: String?,
    val medicalNotes: String?,
    val doctorName: String?,
    val doctorPhone: String?,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
