package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_grocery_items",
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
data class KBGroceryItemEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val category: String?,
    val notes: String?,
    /**
     * Quante confezioni servono. `null` (o 1) significa una sola: la lista non
     * scrive "x 1", che sarebbe rumore su quasi tutte le righe.
     */
    val quantity: Int?,
    val isPurchased: Boolean,
    val purchasedAtEpochMillis: Long?,
    val purchasedBy: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
