package it.vittorioscocca.kidbox.domain.model

/** Membro famiglia — allineato a [KBFamilyMember] iOS. */
data class KBFamilyMember(
    val id: String,
    val familyId: String,
    val userId: String,
    val role: String,
    val displayName: String?,
    val email: String?,
    val photoURL: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
