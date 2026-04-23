package it.vittorioscocca.kidbox.data.chat.model

/**
 * Android mirror of iOS ContactPayload.
 */
data class ContactPayload(
    val givenName: String,
    val familyName: String,
    val phoneNumbers: List<LabeledStringValue>,
    val emailAddresses: List<LabeledStringValue>,
    val avatarData: ByteArray?,
) {
    val fullName: String
        get() = "$givenName $familyName".trim().ifBlank { "Contatto" }

    val primaryPhone: String?
        get() = phoneNumbers.firstOrNull()?.value?.trim()?.takeIf { it.isNotEmpty() }
}

data class LabeledStringValue(
    val label: String,
    val value: String,
)
