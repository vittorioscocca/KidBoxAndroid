package it.vittorioscocca.kidbox.domain.model

/** Scheda pediatrica (id == childId) — allineato a [KBPediatricProfile] iOS. */
data class KBPediatricProfile(
    val id: String,
    val familyId: String,
    val childId: String,
    val emergencyContactsJson: String?,
    val bloodGroup: String?,
    val allergies: String?,
    val medicalNotes: String?,
    val doctorName: String?,
    val doctorPhone: String?,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
