package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_document_categories",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KBDocumentCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("familyId"), Index("parentId")],
)
data class KBDocumentCategoryEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String,
    val isDeleted: Boolean,
    val parentId: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
