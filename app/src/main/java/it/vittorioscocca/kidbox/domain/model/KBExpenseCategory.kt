package it.vittorioscocca.kidbox.domain.model

/** Categoria spesa — allineato a [KBExpenseCategory] iOS. */
data class KBExpenseCategory(
    val id: String,
    val familyId: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isDefault: Boolean,
    val sortIndex: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
)
