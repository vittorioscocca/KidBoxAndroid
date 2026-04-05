package it.vittorioscocca.kidbox.domain.model

/** Contatto emergenza serializzato in [KBPediatricProfile.emergencyContactsJson]. */
data class KBEmergencyContact(
    val id: String,
    val name: String,
    val relation: String,
    val phone: String,
)
