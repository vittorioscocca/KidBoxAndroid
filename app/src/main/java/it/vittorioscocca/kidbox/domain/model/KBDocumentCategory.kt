package it.vittorioscocca.kidbox.domain.model

/** Categoria documenti — allineato a [KBDocumentCategory] iOS. */
data class KBDocumentCategory(
    val id: String,
    val familyId: String,
    val title: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
    val parentId: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
