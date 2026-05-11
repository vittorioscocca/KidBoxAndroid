package it.vittorioscocca.kidbox.data.vehicles

/** Tag in [it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity.notes] — parity iOS `vehicle:{id}`. */
object VehicleAttachmentTag {
    fun make(vehicleId: String): String = "vehicle:$vehicleId"
    fun matches(notes: String?, vehicleId: String): Boolean = matchesTag(notes, "vehicle", vehicleId)
}

/** Tag intervento — parity iOS `vehicleEvent:{eventId}`. */
object VehicleEventAttachmentTag {
    fun make(eventId: String): String = "vehicleEvent:$eventId"
    fun matches(notes: String?, eventId: String): Boolean = matchesTag(notes, "vehicleEvent", eventId)
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
