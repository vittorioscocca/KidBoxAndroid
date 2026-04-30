package it.vittorioscocca.kidbox.domain.model

private val SLOT_LABELS = listOf("Mattina", "Pranzo", "Sera", "Notte")

fun slotLabelFor(index: Int): String =
    if (index < SLOT_LABELS.size) SLOT_LABELS[index] else "Dose ${index + 1}"

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
