package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_shared_locations",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("familyId", "isSharing"),
        Index("familyId", "lastUpdateAtEpochMillis"),
    ],
)
data class KBSharedLocationEntity(
    @PrimaryKey val id: String, // uid
    val familyId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val isSharing: Boolean,
    val modeRaw: String, // realtime | temporary
    val startedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val lastUpdateAtEpochMillis: Long?,
    val avatarUrl: String?,
)
