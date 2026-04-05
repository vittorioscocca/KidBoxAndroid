package it.vittorioscocca.kidbox.domain.model

/** Completamento routine per giorno — allineato a [KBRoutineCheck] iOS. */
data class KBRoutineCheck(
    val id: String,
    val familyId: String,
    val childId: String,
    val routineId: String,
    val dayKey: String,
    val checkedAtEpochMillis: Long,
    val checkedBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
