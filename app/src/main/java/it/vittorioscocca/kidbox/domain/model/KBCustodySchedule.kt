package it.vittorioscocca.kidbox.domain.model

/** Template custodia settimanale — allineato a [KBCustodySchedule] iOS. */
data class KBCustodySchedule(
    val id: String,
    val familyId: String,
    val childId: String,
    val pattern: String,
    val weekTemplateJSON: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
