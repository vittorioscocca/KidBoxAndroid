package it.vittorioscocca.kidbox.domain.model

/**
 * Categoria dei documenti d'identità acquisiti dalla sezione "Documenti" del
 * Wallet (Tessera Sanitaria, Carta d'identità/CIE, Patente, Passaporto,
 * Codice Fiscale). Porting 1:1 di `KBWalletDocumentKind` (iOS).
 *
 * Non introduce una nuova entity Room: i documenti restano `KBDocumentEntity`
 * come nel resto dell'app. Il kind è codificato in [KBDocumentEntity.notes]
 * tramite [WalletDocumentMetadata].
 */
enum class DocumentKind(val raw: String) {
    TESSERA_SANITARIA("tesseraSanitaria"),
    CARTA_IDENTITA("cartaIdentita"),
    CIE("cie"),
    PASSAPORTO("passaporto"),
    CODICE_FISCALE("codiceFiscale"),
    PATENTE("patente"),
    ALTRO("altro"), ;

    val displayName: String get() = when (this) {
        TESSERA_SANITARIA -> "Tessera Sanitaria"
        CARTA_IDENTITA -> "Carta d'identità (cartacea)"
        CIE -> "CIE (Carta d'identità elettronica)"
        PASSAPORTO -> "Passaporto"
        CODICE_FISCALE -> "Codice Fiscale"
        PATENTE -> "Patente"
        ALTRO -> "Documento"
    }

    /** Colore gradiente in alto (stessi valori RGB di iOS, incluso il rosa patente). */
    val gradientStartHex: Long get() = when (this) {
        TESSERA_SANITARIA -> 0xFF218C73 // verde sanità
        CARTA_IDENTITA -> 0xFF2959A0
        CIE -> 0xFF3373B3
        PASSAPORTO -> 0xFF4D3399
        CODICE_FISCALE -> 0xFF8C6B26
        PATENTE -> 0xFFDB73A6 // rosa patente
        ALTRO -> 0xFF59595E
    }

    /** Colore gradiente in basso (versione scurita). */
    val gradientEndHex: Long get() = when (this) {
        TESSERA_SANITARIA -> 0xFF105242
        CARTA_IDENTITA -> 0xFF14305C
        CIE -> 0xFF173D6B
        PASSAPORTO -> 0xFF261752
        CODICE_FISCALE -> 0xFF4D3810
        PATENTE -> 0xFF9E3D6B // rosa scuro
        ALTRO -> 0xFF2E2E38
    }

    companion object {
        fun from(raw: String?): DocumentKind = entries.firstOrNull { it.raw == raw } ?: ALTRO
    }
}
