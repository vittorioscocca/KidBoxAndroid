package it.vittorioscocca.kidbox.domain.model

/** Voce lista spesa — allineato a [KBGroceryItem] iOS. */
data class KBGroceryItem(
    val id: String,
    val familyId: String,
    val name: String,
    val category: String?,
    val notes: String?,
    val isPurchased: Boolean,
    val purchasedAtEpochMillis: Long?,
    val purchasedBy: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
