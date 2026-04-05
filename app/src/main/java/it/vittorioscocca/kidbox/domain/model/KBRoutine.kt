package it.vittorioscocca.kidbox.domain.model

/** Routine giornaliera — allineato a [KBRoutine] iOS. */
data class KBRoutine(
    val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
