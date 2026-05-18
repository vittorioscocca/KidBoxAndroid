package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_trip_day_plans",
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
data class KBTripDayPlanEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val tripId: String,
    val dateString: String,
    val location: String,
    val morningPlan: String,
    val afternoonPlan: String,
    val eveningPlan: String,
    val accommodationName: String?,
    val accommodationType: String?,
    val accommodationCostPerNight: Double?,
    val weatherBackupPlan: String?,
    val estimatedDailyCost: Double?,
    val updatedAtEpoch: Long,
)
