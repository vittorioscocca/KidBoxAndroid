package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_expenses",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = KBDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachedDocumentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId"), Index("categoryId"), Index("attachedDocumentId"), Index("dateEpochMillis")],
)
data class KBExpenseEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    val amount: Double,
    val dateEpochMillis: Long,
    val categoryId: String?,
    val notes: String?,
    val attachedDocumentId: String?,
    val receiptThumbnailData: ByteArray?,
    val createdByUid: String?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
