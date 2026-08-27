package it.vittorioscocca.kidbox.domain.model

import androidx.annotation.StringRes
import it.vittorioscocca.kidbox.R

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

    /** Passwords supportano anche `members` (allineato a iOS). */
    fun normalizedPassword(scope: String?): String {
        return normalized(scope)
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

    /**
     * Id della stringa da mostrare nella pill: le tre voci sono le stesse del menu di
     * scelta, quindi riusa le risorse già tradotte invece di duplicarle in italiano.
     * Risolvilo con [it.vittorioscocca.kidbox.ui.util.visibilityChipLabel] nelle composable.
     */
    @StringRes
    fun chipLabelRes(scope: String): Int = when (normalized(scope)) {
        MEMBERS -> R.string.notes_visibility_option_members
        ONLY_CREATOR -> R.string.notes_visibility_option_only_me
        else -> R.string.notes_visibility_option_family
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
            MEMBERS -> createdBy == uid || memberIds.contains(uid)
            ONLY_CREATOR -> createdBy?.isNotBlank() == true && createdBy == uid
            else -> true
        }
    }
}
