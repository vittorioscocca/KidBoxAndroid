package it.vittorioscocca.kidbox.domain.model

/**
 * Stored on Firestore / Room as raw strings `"family"` | `"members"` | `"private"` (solo creatore).
 * Allineato a [KBVisibilityScope] iOS.
 */
object KBVisibilityScope {
    const val FAMILY = "family"
    const val MEMBERS = "members"
    /** Stored as `"private"` (creator-only). */
    const val ONLY_CREATOR = "private"

    fun normalized(scope: String?): String = when (scope) {
        MEMBERS -> MEMBERS
        ONLY_CREATOR -> ONLY_CREATOR
        else -> FAMILY
    }

    /** Wallet: default creator-only; unknown/blank → [ONLY_CREATOR]. */
    fun normalizedWallet(scope: String?): String {
        val s = scope?.trim().orEmpty()
        if (s.isEmpty()) return ONLY_CREATOR
        return when (s) {
            FAMILY, MEMBERS, ONLY_CREATOR -> s
            else -> ONLY_CREATOR
        }
    }

    fun chipLabel(scope: String): String = when (normalized(scope)) {
        MEMBERS -> "👥 Membri selezionati"
        ONLY_CREATOR -> "🔒 Solo io"
        else -> "👨‍👩‍👧 Tutta la famiglia"
    }

    /** `createdBy` is KBNote.createdBy / KBTodoItemEntity.createdBy. */
    fun isVisible(
        scope: String,
        memberIds: List<String>,
        createdBy: String?,
        currentUid: String?,
    ): Boolean {
        val uid = currentUid?.takeIf { it.isNotBlank() } ?: return false
        return when (normalized(scope)) {
            FAMILY -> true
            MEMBERS -> memberIds.contains(uid)
            ONLY_CREATOR -> createdBy?.isNotBlank() == true && createdBy == uid
            else -> true
        }
    }
}
