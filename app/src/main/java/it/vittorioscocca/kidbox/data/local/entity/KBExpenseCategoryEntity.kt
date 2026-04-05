package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_expense_categories",
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
data class KBExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isDefault: Boolean,
    val sortIndex: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
)
