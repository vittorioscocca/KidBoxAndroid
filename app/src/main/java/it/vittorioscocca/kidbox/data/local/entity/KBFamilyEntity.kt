package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_families",
    indices = [Index(value = ["updatedAtEpochMillis"])],
)
data class KBFamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val heroPhotoURL: String?,
    val heroPhotoLocalPath: String? = null,
    val heroPhotoUpdatedAtEpochMillis: Long?,
    val heroPhotoScale: Double?,
    val heroPhotoOffsetX: Double?,
    val heroPhotoOffsetY: Double?,
    val createdBy: String,
    val updatedBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastSyncAtEpochMillis: Long?,
    val lastSyncError: String?,
)
