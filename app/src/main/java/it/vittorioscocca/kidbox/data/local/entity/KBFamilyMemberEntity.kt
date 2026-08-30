package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Riga membro di UNA famiglia.
 *
 * La chiave primaria è **composita** `(familyId, id)` e non il solo `id`: `id` è
 * l'id del documento Firestore, che in `families/{familyId}/members/{uid}` è
 * l'**uid** del membro — non è quindi unico fra famiglie diverse. Con la sola
 * `id` come chiave, chi appartiene a due famiglie aveva una riga sola: l'ultima
 * scrittura vinceva (`OnConflictStrategy.REPLACE`) e si portava dietro il
 * proprio `familyId`, spostando l'utente da una famiglia all'altra. La famiglia
 * aperta perdeva un membro a ogni avvio (verificato sui log del 30/08/2026).
 *
 * Conseguenza per chi scrive query: una riga si identifica con **famiglia +
 * id**, mai con il solo id. Per il nome di una persona a prescindere dalla
 * famiglia c'è `getAnyById`.
 */
@Entity(
    tableName = "kb_family_members",
    primaryKeys = ["familyId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("userId"),
    ],
)
data class KBFamilyMemberEntity(
    val id: String,
    val familyId: String,
    val userId: String,
    val role: String,
    val displayName: String?,
    val email: String?,
    val photoURL: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
)
