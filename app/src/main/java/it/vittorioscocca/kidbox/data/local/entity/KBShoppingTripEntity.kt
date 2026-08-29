package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una spesa fatta: cosa è finito nel carrello, dove, quanto è costata.
 *
 * Nasce dal filtro "Presi" della lista: quello che si spunta al supermercato è
 * già l'elenco dello scontrino. Il record resta qui come storico; i soldi vivono
 * nella spesa collegata ([linkedExpenseId]), che è l'unica voce contata nella
 * sezione Spese. Allineato a `KBShoppingTrip` iOS.
 */
@Entity(
    tableName = "kb_shopping_trips",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index("dateEpochMillis")],
)
data class KBShoppingTripEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val storeName: String?,
    val total: Double,
    val dateEpochMillis: Long,
    /**
     * Le righe dello scontrino serializzate in JSON: un solo campo da
     * sincronizzare, e nessuna relazione da tenere allineata quando i prodotti
     * spariscono dalla lista.
     */
    val linesJson: String?,
    val notes: String?,
    /** La spesa creata nella sezione Spese, se c'è. */
    val linkedExpenseId: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
