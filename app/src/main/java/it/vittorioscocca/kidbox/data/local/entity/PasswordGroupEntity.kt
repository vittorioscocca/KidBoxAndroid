package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "password_groups",
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
data class PasswordGroupEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val nameCipher: ByteArray,
    val icon: String,
    val color: String,
    val visibility: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    val syncStateRaw: Int = 0,
    val lastSyncError: String?,
)
