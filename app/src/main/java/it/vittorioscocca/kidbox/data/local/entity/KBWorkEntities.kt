package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope

/**
 * La scheda «Lavoro» di un adulto. Mirror di `KBWorkProfile` (iOS): `ownerUid`
 * è l'uid del proprietario (su Firestore è anche l'id del documento) e
 * `visibilityMemberIdsJson` l'elenco autorevole degli adulti con cui la
 * condivide (vuoto = solo lui). Le rules (`workProfiles`) fanno rispettare la
 * visibilità: in Room arriva solo ciò che il server ha consegnato.
 *
 * Chiave composta: lo stesso uid può avere una scheda in più famiglie.
 */
@Entity(
    tableName = "kb_work_profiles",
    primaryKeys = ["familyId", "ownerUid"],
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId")],
)
data class KBWorkProfileEntity(
    val familyId: String,
    val ownerUid: String,
    /** Datore di lavoro, cifrato con la chiave di famiglia. */
    val employerEnc: String? = null,
    /** `"private"` | `"members"`. Mai `"family"`: le rules lo rifiutano. */
    val visibilityScope: String = KBVisibilityScope.ONLY_CREATOR,
    val visibilityMemberIdsJson: String = "[]",
    val createdBy: String,
    val createdByName: String,
    val updatedBy: String,
    val updatedByName: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean = false,
    val syncStateRaw: Int = 0,
)

/**
 * Una busta paga, un contratto o un foglio ferie già letti. Mirror di
 * `KBWorkRecord` (iOS): i valori estratti stanno in `fieldsEnc` (JSON di
 * [it.vittorioscocca.kidbox.domain.work.WorkFields] cifrato con la chiave di
 * famiglia), in chiaro solo tipo, data di riferimento e puntatore al file.
 */
@Entity(
    tableName = "kb_work_records",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index(value = ["familyId", "ownerUid"])],
)
data class KBWorkRecordEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val ownerUid: String,
    /** `"payslip"` | `"contract"` | `"leavePlan"`. */
    val kind: String,
    val referenceDateEpochMillis: Long,
    val fieldsEnc: String? = null,
    /** `KBDocument` col file originale (visibilità della scheda, tag `work:{uid}`). */
    val attachmentDocumentId: String? = null,
    val createdBy: String,
    val createdByName: String,
    val updatedBy: String,
    val updatedByName: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean = false,
    val syncStateRaw: Int = 0,
)
