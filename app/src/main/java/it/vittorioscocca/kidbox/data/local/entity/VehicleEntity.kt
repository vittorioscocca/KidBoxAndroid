package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles",
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
data class VehicleEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val licensePlate: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val fuelType: String? = null,
    val color: String? = null,
    val vin: String? = null,
    val insuranceExpiryDate: Long? = null,
    val revisionExpiryDate: Long? = null,
    val taxExpiryDate: Long? = null,
    val lastServiceDate: Long? = null,
    val nextServiceDate: Long? = null,
    val currentKm: Int? = null,
    val notes: String? = null,
    val photoURL: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderOffsetsJson: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = "",
    val syncState: Int = 0,
)
