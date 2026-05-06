package it.vittorioscocca.kidbox.domain.model

enum class WalletTicketKind(
    val raw: String,
    val displayName: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long,
) {
    FLIGHT("flight", "Volo", 0xFF1C7EF2, 0xFF0A4FA8),
    TRAIN("train", "Treno", 0xFF34A853, 0xFF1B6E2E),
    FERRY("ferry", "Traghetto", 0xFF00ACC1, 0xFF006978),
    BUS("bus", "Autobus", 0xFFFB8C00, 0xFFE65100),
    CONCERT("concert", "Concerto", 0xFF9C27B0, 0xFF5E0D82),
    CINEMA("cinema", "Cinema", 0xFFE53935, 0xFF8B0000),
    PARKING("parking", "Parcheggio", 0xFF546E7A, 0xFF2C3E50),
    MUSEUM("museum", "Museo", 0xFF8D6E63, 0xFF4E342E),
    OTHER("other", "Biglietto", 0xFF607D8B, 0xFF37474F);

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}
