package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_photo_albums",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBFamilyPhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["coverPhotoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId"), Index("coverPhotoId")],
)
data class KBPhotoAlbumEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    val coverPhotoId: String?,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val createdBy: String,
    val updatedBy: String,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
