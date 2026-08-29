package it.vittorioscocca.kidbox.data.pets

/** Tag in `KBDocumentEntity.notes` — parity iOS `petEvent:{id}`. */
/**
 * Allegati della scheda animale: libretto sanitario, pedigree, microchip.
 * Vivono nella stessa cartella degli allegati degli eventi — per chi guarda
 * Documenti sono la stessa cosa, roba dell'animale. Parity iOS `pet:{id}`.
 */
object PetAttachmentTag {
    fun make(petId: String): String = "pet:$petId"
    fun matches(notes: String?, petId: String): Boolean = matchesTag(notes, "pet", petId)
}

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
