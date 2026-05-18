package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_trip_expenses",
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
data class KBTripExpenseEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val tripId: String,
    val dateString: String,
    val amount: Double,
    val currency: String,
    val categoryRaw: String,
    val description: String?,
    val paidBy: String,
    val updatedAtEpoch: Long,
)
