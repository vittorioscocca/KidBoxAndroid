package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_ai_messages",
    indices = [Index("conversationId"), Index("createdAtEpochMillis")],
)
data class KBAIMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val roleRaw: String,
    val content: String,
    val createdAtEpochMillis: Long,
)
