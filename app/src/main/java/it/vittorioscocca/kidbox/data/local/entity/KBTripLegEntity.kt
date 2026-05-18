package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_trip_legs",
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
data class KBTripLegEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val tripId: String,
    val order: Int,
    val fromLocation: String,
    val toLocation: String,
    val transportModeRaw: String,
    val departureAtEpoch: Long?,
    val arrivalAtEpoch: Long?,
    val notes: String?,
    val updatedAtEpoch: Long,
)
