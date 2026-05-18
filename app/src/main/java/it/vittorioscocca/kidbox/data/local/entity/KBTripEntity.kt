package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_trips",
    indices = [Index("familyId"), Index("startDateEpoch")],
)
data class KBTripEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val startDateEpoch: Long,
    val endDateEpoch: Long,
    val participantIdsJson: String,
    val budgetTotal: Double,
    val currency: String,
    val statusRaw: String,
    val aiProposalJson: String?,
    val photoAlbumId: String? = null,
    val notesNoteId: String? = null,
    val todoListId: String? = null,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
    val createdBy: String,
    val updatedBy: String,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
