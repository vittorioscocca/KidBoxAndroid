package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_ai_conversations",
    indices = [Index("familyId"), Index("childId"), Index("scopeId", unique = true)],
)
data class KBAIConversationEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val scopeId: String,
    val summary: String?,
    val summarizedMessageCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
