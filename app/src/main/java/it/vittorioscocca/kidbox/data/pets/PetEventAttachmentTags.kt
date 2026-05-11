package it.vittorioscocca.kidbox.data.pets

/** Tag in `KBDocumentEntity.notes` — parity iOS `petEvent:{id}`. */
object PetEventAttachmentTag {
    fun make(eventId: String): String = "petEvent:$eventId"
    fun matches(notes: String?, eventId: String): Boolean = matchesTag(notes, "petEvent", eventId)
}

private fun matchesTag(notes: String?, prefix: String, id: String): Boolean {
    if (notes.isNullOrBlank() || id.isBlank()) return false
    val n = notes.trim()
    val expected = "$prefix:$id"
    if (n == expected) return true
    val lower = n.lowercase()
    val encoded = "${prefix.lowercase()}%3a${id.lowercase()}"
    return lower.contains(expected.lowercase()) ||
        lower.contains(encoded) ||
        lower.contains("\"${prefix}id\":\"${id.lowercase()}\"")
}
