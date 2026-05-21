package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_geofences",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId")],
)
data class KBGeofenceEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val emoji: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radius: Double = 200.0,
    val notifyOnArrive: Boolean = true,
    val notifyOnLeave: Boolean = false,
    val notifyMembersJson: String = "[]",
    val monitoredMemberIdsJson: String = "[]",
    val isActive: Boolean = true,
    val createdBy: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
)
