package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "house_payments",
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
data class HousePaymentEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val typeRaw: String,
    val subtypeRaw: String? = null,
    val importo: Double? = null,
    val giornoDiScadenzaMensile: Int? = null,
    val dataScadenza: Long? = null,
    val dataScadenzaContratto: Long? = null,
    val fornitore: String? = null,
    val note: String? = null,
    val reminderOn: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = "",
    val syncState: Int = 0,
)
