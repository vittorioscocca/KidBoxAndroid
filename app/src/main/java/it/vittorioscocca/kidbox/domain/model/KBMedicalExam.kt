package it.vittorioscocca.kidbox.domain.model

/** Esame medico — allineato a [KBMedicalExam] iOS. */
data class KBMedicalExam(
    val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val isUrgent: Boolean,
    val deadlineEpochMillis: Long?,
    val preparation: String?,
    val notes: String?,
    val location: String?,
    val statusRaw: String,
    val resultText: String?,
    val resultDateEpochMillis: Long?,
    val prescribingVisitId: String?,
    val reminderOn: Boolean,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val createdBy: String,
)
