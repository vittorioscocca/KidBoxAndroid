package it.vittorioscocca.kidbox.domain.model

/** Nota famiglia — allineato a [KBNote] iOS. */
data class KBNote(
    val id: String,
    val familyId: String,
    val title: String,
    val body: String,
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    val visibilityMemberIds: List<String> = emptyList(),
    val createdBy: String,
    val createdByName: String,
    val updatedBy: String,
    val updatedByName: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
) {
    fun isVisibleTo(currentUid: String?): Boolean =
        KBVisibilityScope.isVisible(
            scope = KBVisibilityScope.normalized(visibilityScope),
            memberIds = visibilityMemberIds,
            createdBy = createdBy.takeIf { it.isNotBlank() },
            currentUid = currentUid,
        )
}
