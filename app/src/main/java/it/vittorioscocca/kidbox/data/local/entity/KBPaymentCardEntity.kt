package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope

/**
 * Carta di pagamento (credito, debito, prepagata) del Wallet, sezione
 * «Pagamento». Mirror di `KBPaymentCard` (iOS).
 *
 * I campi `*Enc` sono lo stesso base64(AES-GCM) che sta su Firestore e
 * restano cifrati anche qui in Room: si decifrano solo per mostrarli
 * ([it.vittorioscocca.kidbox.data.repository.PaymentCardPlain]). Inserimento
 * solo manuale, niente AI, niente CVV. Il PIN c'è (richiesta dell'utente,
 * 03/10/2026): mai sulla carta disegnata, visibile solo dopo biometria.
 * Visibilità di default `"private"`.
 */
@Entity(
    tableName = "kb_payment_cards",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index(value = ["familyId", "isDeleted"])],
)
data class KBPaymentCardEntity(
    @PrimaryKey val id: String,
    val familyId: String,

    val labelEnc: String? = null,
    val cardNumberEnc: String? = null,
    val holderNameEnc: String? = null,
    val ibanEnc: String? = null,
    /** Scadenza `MM/AA`, cifrata. */
    val expiryEnc: String? = null,
    val notesEnc: String? = null,
    /** PIN, solo cifre, cifrato. */
    val pinEnc: String? = null,

    /** Colore della carta, in chiaro (palette fissa uguale su tutti i client). */
    val colorHex: String,

    /** Foto fronte/retro: il blob su Storage è cifrato, qui solo i puntatori. */
    val frontPhotoStorageURL: String? = null,
    val frontPhotoStoragePath: String? = null,
    val backPhotoStorageURL: String? = null,
    val backPhotoStoragePath: String? = null,

    val createdBy: String,
    val createdByName: String,
    val updatedBy: String,
    val updatedByName: String,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,

    val isDeleted: Boolean,

    /** `"family"` | `"members"` | `"private"`. Default `"private"`. */
    val visibilityScope: String = KBVisibilityScope.ONLY_CREATOR,
    val visibilityMemberIdsJson: String = "[]",

    val syncStateRaw: Int = 0,
)
