package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pet_events",
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
        Index(value = ["familyId", "petId"]),
    ],
)
data class PetEventEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val petId: String,
    val title: String,
    val eventType: String,
    val date: Long,
    val nextDueDate: Long? = null,
    val notes: String? = null,
    val vetName: String? = null,
    val cost: Double? = null,
    val reminderEnabled: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = "",
    val syncState: Int = 0,
)
