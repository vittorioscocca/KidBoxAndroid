package it.vittorioscocca.kidbox.domain.model

/** Bambino — allineato a [KBChild] iOS. */
data class KBChild(
    val id: String,
    val familyId: String?,
    val name: String,
    val birthDateEpochMillis: Long?,
    val weightKg: Double?,
    val heightCm: Double?,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedBy: String?,
    val updatedAtEpochMillis: Long?,
)
