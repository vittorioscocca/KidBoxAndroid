package it.vittorioscocca.kidbox.data.health

object VisitAttachmentTag {
    fun make(visitId: String): String = "visit:$visitId"
    fun matches(notes: String?, visitId: String): Boolean = matchesTag(notes, "visit", visitId)
}

object ExamAttachmentTag {
    fun make(examId: String): String = "exam:$examId"
    fun matches(notes: String?, examId: String): Boolean = matchesTag(notes, "exam", examId)
}

object TreatmentAttachmentTag {
    fun make(treatmentId: String): String = "treatment:$treatmentId"
    fun matches(notes: String?, treatmentId: String): Boolean = matchesTag(notes, "treatment", treatmentId)
}

private fun matchesTag(notes: String?, prefix: String, id: String): Boolean {
    if (notes.isNullOrBlank() || id.isBlank()) return false
    val n = notes.trim()
    val expected = "$prefix:$id"
    if (n == expected) return true

    // Compatibility with legacy/payload formats (URL-encoded tag, embedded tag, JSON-like payloads).
    val lower = n.lowercase()
    val encoded = "${prefix.lowercase()}%3a${id.lowercase()}"
    return lower.contains(expected.lowercase()) ||
        lower.contains(encoded) ||
        lower.contains("\"${prefix}id\":\"${id.lowercase()}\"") ||
        lower.contains("${prefix.lowercase()}id=${id.lowercase()}")
}
