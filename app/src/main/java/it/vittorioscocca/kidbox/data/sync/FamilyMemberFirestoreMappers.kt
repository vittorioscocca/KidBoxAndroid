package it.vittorioscocca.kidbox.data.sync

/**
 * Reads the first non-blank string among [keys] from a Firestore document map.
 * Handles [String] and other [CharSequence] values.
 */
internal fun Map<String, Any>.firstNonBlankString(vararg keys: String): String? {
    for (key in keys) {
        when (val v = this[key]) {
            is String -> v.trim().takeIf { it.isNotEmpty() }?.let { return it }
            is CharSequence -> v.toString().trim().takeIf { it.isNotEmpty() }?.let { return it }
            else -> continue
        }
    }
    return null
}

/** Display label from a Firestore `users/{uid}` document (aligns with common iOS profile fields). */
internal fun Map<String, Any>.userProfileDisplayName(): String? {
    firstNonBlankString("displayName", "name", "fullName", "email")?.let { return it }
    val composed = listOfNotNull(
        firstNonBlankString("firstName"),
        firstNonBlankString("lastName"),
    ).joinToString(" ").trim()
    return composed.takeIf { it.isNotEmpty() }
}
