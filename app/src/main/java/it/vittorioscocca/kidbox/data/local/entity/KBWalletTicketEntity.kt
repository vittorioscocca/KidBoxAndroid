package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_wallet_tickets",
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
data class KBWalletTicketEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    val kindRaw: String,
    val eventDateEpochMillis: Long?,
    val eventEndDateEpochMillis: Long?,
    val location: String?,
    val seat: String?,
    val bookingCode: String?,
    val notes: String?,
    val emitter: String?,
    val pdfStorageURL: String?,
    val pdfStorageBytes: Long,
    val pdfFileName: String?,
    val pdfThumbnailBase64: String?,
    val addToAppleWalletURL: String?,
    val barcodeText: String?,
    val barcodeFormat: String?,
    val createdBy: String,
    val createdByName: String,
    val updatedBy: String,
    val updatedByName: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
    val syncStateRaw: Int = 0,
)
