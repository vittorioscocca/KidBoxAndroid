package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_children",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId")],
)
data class KBChildEntity(
    @PrimaryKey val id: String,
    val familyId: String?,
    val name: String,
    val birthDateEpochMillis: Long?,
    val weightKg: Double?,
    val heightCm: Double?,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedBy: String?,
    val updatedAtEpochMillis: Long?,
)
