package it.vittorioscocca.kidbox.domain.model

/** Documento — allineato a [KBDocument] iOS. */
data class KBDocument(
    val id: String,
    val familyId: String,
    val childId: String?,
    val categoryId: String?,
    val localPath: String?,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val storagePath: String,
    val downloadURL: String?,
    val notes: String?,
    val extractedText: String?,
    val extractedTextUpdatedAtEpochMillis: Long?,
    val extractionStatusRaw: Int,
    val extractionError: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
