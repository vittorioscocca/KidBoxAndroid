package it.vittorioscocca.kidbox.domain.model

/** Album foto — allineato a [KBPhotoAlbum] iOS. */
data class KBPhotoAlbum(
    val id: String,
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
