package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_packing_items",
    foreignKeys = [
        ForeignKey(
            entity = KBTripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("familyId")],
)
data class KBPackingItemEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val tripId: String,
    val label: String,
    val categoryRaw: String,
    val isChecked: Boolean,
    val isAIGenerated: Boolean,
    val fromMedicalProfile: Boolean,
    val updatedAtEpoch: Long,
)
