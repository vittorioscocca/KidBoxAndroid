package it.vittorioscocca.kidbox.domain.model

/** Farmaco personalizzato (catalogo locale) — allineato a [KBCustomDrug] iOS. */
data class KBCustomDrug(
    val id: String,
    val name: String,
    val activeIngredient: String,
    val category: String,
    val form: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
)
