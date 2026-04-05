package it.vittorioscocca.kidbox.domain.model

/** Terapia farmacologica — allineato a [KBTreatment] iOS. */
data class KBTreatment(
    val id: String,
    val familyId: String,
    val childId: String,
    val drugName: String,
    val activeIngredient: String?,
    val dosageValue: Double,
    val dosageUnit: String,
    val isLongTerm: Boolean,
    val durationDays: Int,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long?,
    val dailyFrequency: Int,
    val scheduleTimesData: String,
    val isActive: Boolean,
    val notes: String?,
    val reminderEnabled: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStatus: Int,
    val lastSyncError: String?,
    val syncStateRaw: Int,
)
