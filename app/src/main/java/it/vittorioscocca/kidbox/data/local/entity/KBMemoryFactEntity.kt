package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_memory_facts",
    indices = [Index("familyId")],
)
data class KBMemoryFactEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val content: String,
    val categoryRaw: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sourceConversationId: String?,
)

enum class MemoryFactCategory(val raw: String) {
    SALUTE("salute"),
    ABITUDINI("abitudini"),
    PREFERENZE("preferenze"),
    SCUOLA("scuola"),
    RELAZIONI("relazioni"),
    CASA("casa"),
    WALLET("wallet"),
    ANIMALI("animali"),
    ALTRO("altro"),
    ;

    companion object {
        fun fromRaw(value: String): MemoryFactCategory =
            entries.firstOrNull { it.raw == value.lowercase() } ?: ALTRO
    }
}
