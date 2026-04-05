package it.vittorioscocca.kidbox.domain.model

/**
 * Famiglia (workspace) — allineato a [KBFamily] iOS.
 * I figli sono legati tramite [KBChild.familyId] / relazione Room.
 */
data class KBFamily(
    val id: String,
    val name: String,
    val heroPhotoURL: String?,
    val heroPhotoUpdatedAtEpochMillis: Long?,
    val heroPhotoScale: Double?,
    val heroPhotoOffsetX: Double?,
    val heroPhotoOffsetY: Double?,
    val createdBy: String,
    val updatedBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastSyncAtEpochMillis: Long?,
    val lastSyncError: String?,
)
