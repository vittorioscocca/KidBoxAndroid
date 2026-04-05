package it.vittorioscocca.kidbox.domain.model

/** Evento calendario — allineato a [KBCalendarEvent] iOS. */
data class KBCalendarEvent(
    val id: String,
    val familyId: String,
    val childId: String?,
    val title: String,
    val notes: String?,
    val location: String?,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long,
    val isAllDay: Boolean,
    val categoryRaw: String,
    val recurrenceRaw: String,
    val reminderMinutes: Int?,
    val linkedHealthItemId: String?,
    val linkedHealthItemType: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val createdBy: String,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
