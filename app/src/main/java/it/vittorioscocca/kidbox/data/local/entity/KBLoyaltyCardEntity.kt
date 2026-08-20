package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope

/**
 * Carta fedeltà custodita nel "Wallet" (sezione "Carte"). Mirror Android di
 * `KBLoyaltyCard` (iOS), pattern speculare a [KBWalletTicketEntity] ma:
 * - visibilità di default `"family"` (condivisa con tutta la famiglia), non
 *   `"private"` come i biglietti — richiesta esplicita dell'utente;
 * - niente cifratura del numero carta / nome brand: stesso trattamento del
 *   campo `emitter` sui biglietti (campo a bassa sensibilità);
 * - niente PDF/reminder: solo testo (numero carta + nota opzionale).
 */
@Entity(
    tableName = "kb_loyalty_cards",
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
data class KBLoyaltyCardEntity(
    @PrimaryKey val id: String,
    val familyId: String,

    /** Id del brand nel catalogo ([it.vittorioscocca.kidbox.domain.model.LoyaltyCardCatalog]), `null` se "carta non in elenco". */
    val brandId: String?,
    /** Nome del negozio/brand mostrato sulla card. Sempre valorizzato. */
    val brandName: String,
    /** Numero/codice della carta fedeltà, in chiaro (non cifrato). */
    val cardNumber: String,
    /** Raw value del formato barcode (es. "ean13", "code128", "qr"). */
    val barcodeFormat: String,
    /** Nota libera opzionale. */
    val note: String? = null,
    /** Colore primario esadecimale (es. "#E30613"). */
    val primaryColorHex: String,
    /** Colore secondario esadecimale, usato per il gradient della card. */
    val secondaryColorHex: String,
    /**
     * URL del logo ufficiale del brand, copiato dal catalogo alla creazione.
     * `null` per le carte "non in elenco" o per i brand senza logo noto: in
     * quel caso la card mostra il wordmark testuale.
     */
    val logoURL: String? = null,

    // MARK: - Foto fronte/retro della tessera fisica (opzionali)
    //
    // Acquisite con lo scanner ML Kit (`rememberWalletDocumentScannerLauncher`,
    // lo stesso dei documenti d'identità) e caricate **cifrate** su Firebase
    // Storage sotto `families/{familyId}/loyaltyCards/{cardId}/…`. A differenza
    // del numero carta — che resta in chiaro per scelta deliberata — la foto
    // della tessera può mostrare il nome dell'intestatario, quindi passa da
    // `DocumentCryptoManager` come i documenti d'identità.
    // Nomi speculari a `KBLoyaltyCard` (iOS) per la lettura cross-piattaforma.

    /** URL di download della foto del FRONTE (blob cifrato su Storage). */
    val frontPhotoStorageURL: String? = null,
    /** Path Storage della foto del fronte: identificatore stabile per download e cleanup. */
    val frontPhotoStoragePath: String? = null,
    /** URL di download della foto del RETRO (blob cifrato su Storage). */
    val backPhotoStorageURL: String? = null,
    /** Path Storage della foto del retro. Vedi [frontPhotoStoragePath]. */
    val backPhotoStoragePath: String? = null,

    val createdBy: String,
    val createdByName: String,
    val updatedBy: String,
    val updatedByName: String,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,

    val isDeleted: Boolean,

    /** `"family"` | `"members"` | `"private"`. Default `"family"` (a differenza dei Biglietti). */
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    /** JSON array uid ([it.vittorioscocca.kidbox.data.local.mapper.encodeStringList]) se [visibilityScope] == `"members"`. */
    val visibilityMemberIdsJson: String = "[]",

    val syncStateRaw: Int = 0,
)
