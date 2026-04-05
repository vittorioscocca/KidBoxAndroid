package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_family_photos",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index("takenAtEpochMillis")],
)
data class KBFamilyPhotoEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val storagePath: String,
    val downloadURL: String?,
    val localPath: String?,
    val thumbnailBase64: String?,
    val caption: String?,
    val videoDurationSeconds: Double?,
    val takenAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val createdBy: String,
    val updatedBy: String,
    val albumIdsRaw: String,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
