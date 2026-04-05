package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_vaccines",
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
data class KBVaccineEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val vaccineTypeRaw: String,
    val statusRaw: String,
    val commercialName: String?,
    val doseNumber: Int,
    val totalDoses: Int,
    val administeredDateEpochMillis: Long?,
    val scheduledDateEpochMillis: Long?,
    val lotNumber: String?,
    val administeredBy: String?,
    val administrationSiteRaw: String?,
    val notes: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
