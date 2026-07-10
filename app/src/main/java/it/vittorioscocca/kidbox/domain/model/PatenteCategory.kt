package it.vittorioscocca.kidbox.domain.model

import java.time.LocalDate

/**
 * Categoria di patente (A, B, C, ...) con le sue date di rilascio e scadenza:
 * sulla patente italiana ogni categoria ha date proprie (colonne 10/11 sul
 * retro). Porting 1:1 di `KBPatenteCategory` (iOS).
 */
data class PatenteCategory(
    val code: String,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
)
