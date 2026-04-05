package it.vittorioscocca.kidbox.domain.model

/** Visita pediatrica — allineato a [KBMedicalVisit] iOS.
 * Array e struct embedded sono serializzati come JSON nelle proprietà `*Json`.
 */
data class KBMedicalVisit(
    val id: String,
    val familyId: String,
    val childId: String,
    val dateEpochMillis: Long,
    val doctorName: String?,
    val doctorSpecializationRaw: String?,
    val travelDetailsJson: String?,
    val reason: String,
    val diagnosis: String?,
    val recommendations: String?,
    val linkedTreatmentIdsJson: String,
    val linkedExamIdsJson: String,
    val asNeededDrugsJson: String?,
    val therapyTypesJson: String,
    val prescribedExamsJson: String?,
    val photoUrlsJson: String,
    val notes: String?,
    val nextVisitDateEpochMillis: Long?,
    val nextVisitReason: String?,
    val visitStatusRaw: String?,
    val reminderOn: Boolean,
    val nextVisitReminderOn: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
