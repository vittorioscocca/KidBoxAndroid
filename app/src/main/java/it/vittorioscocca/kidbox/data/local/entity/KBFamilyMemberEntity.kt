package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_family_members",
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
        Index("userId"),
    ],
)
data class KBFamilyMemberEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val userId: String,
    val role: String,
    val displayName: String?,
    val email: String?,
    val photoURL: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
