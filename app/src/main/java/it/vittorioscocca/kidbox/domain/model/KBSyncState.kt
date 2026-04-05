package it.vittorioscocca.kidbox.domain.model

/**
 * Stato di sincronizzazione locale (allineato a [KBSyncState] su iOS).
 */
enum class KBSyncState(val rawValue: Int) {
    SYNCED(0),
    PENDING_UPSERT(1),
    PENDING_DELETE(2),
    ERROR(3),
    ;

    companion object {
        fun fromRaw(value: Int): KBSyncState =
            entries.find { it.rawValue == value } ?: SYNCED
    }
}
