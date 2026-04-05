package it.vittorioscocca.kidbox.domain.model

/** Evento figlio — allineato a [KBEvent] iOS. */
data class KBEvent(
    val id: String,
    val familyId: String,
    val childId: String,
    val type: String,
    val title: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
