package it.vittorioscocca.kidbox.data.home

/** Tag in `KBDocumentEntity.notes` — parity iOS `homeItem:{id}`. */
object HomeItemAttachmentTag {
    fun make(homeItemId: String): String = "homeItem:$homeItemId"
    fun matches(notes: String?, homeItemId: String): Boolean = matchesTag(notes, "homeItem", homeItemId)
}

/** Scadenze & pagamenti — parity iOS `housePayment:{id}`. */
object HousePaymentAttachmentTag {
    fun make(paymentId: String): String = "housePayment:$paymentId"
    fun matches(notes: String?, paymentId: String): Boolean = matchesTag(notes, "housePayment", paymentId)
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
