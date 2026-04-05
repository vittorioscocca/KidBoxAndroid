package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kb_custom_drugs")
data class KBCustomDrugEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeIngredient: String,
    val category: String,
    val form: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
)
