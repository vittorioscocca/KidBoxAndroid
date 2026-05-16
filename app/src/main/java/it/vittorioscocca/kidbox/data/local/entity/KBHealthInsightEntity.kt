package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_health_insights",
    indices = [Index("familyId")],
)
data class KBHealthInsightEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val fullText: String,
    val monthKey: String,
    val createdAtEpochMillis: Long,
    val isRead: Boolean = false,
)
