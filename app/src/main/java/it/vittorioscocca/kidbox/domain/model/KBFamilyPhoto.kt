package it.vittorioscocca.kidbox.domain.model

/** Foto/video famiglia crittografato — allineato a [KBFamilyPhoto] iOS. */
data class KBFamilyPhoto(
    val id: String,
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
