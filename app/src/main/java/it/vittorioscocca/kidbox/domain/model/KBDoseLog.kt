package it.vittorioscocca.kidbox.domain.model

/** Log dose terapia — allineato a [KBDoseLog] iOS. */
data class KBDoseLog(
    val id: String,
    val familyId: String,
    val childId: String,
    val treatmentId: String,
    val dayNumber: Int,
    val slotIndex: Int,
    val scheduledTime: String,
    val takenAtEpochMillis: Long?,
    val taken: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val syncStatus: Int,
    val lastSyncError: String?,
)
