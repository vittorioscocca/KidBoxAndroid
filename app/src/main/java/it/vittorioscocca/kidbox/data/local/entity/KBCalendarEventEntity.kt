package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId"), Index("childId"), Index("startDateEpochMillis")],
)
data class KBCalendarEventEntity(
    @PrimaryKey val id: String,
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
