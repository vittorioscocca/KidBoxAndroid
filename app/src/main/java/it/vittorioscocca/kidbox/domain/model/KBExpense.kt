package it.vittorioscocca.kidbox.domain.model

/** Spesa — allineato a [KBExpense] iOS. */
data class KBExpense(
    val id: String,
    val familyId: String,
    val title: String,
    val amount: Double,
    val dateEpochMillis: Long,
    val categoryId: String?,
    val notes: String?,
    val attachedDocumentId: String?,
    val receiptThumbnailData: ByteArray?,
    val createdByUid: String?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
