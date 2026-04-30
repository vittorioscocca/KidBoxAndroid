package it.vittorioscocca.kidbox.domain.model

data class KBAIConversation(
    val id: String,
    val familyId: String,
    val childId: String,
    val scopeId: String,
    val summary: String?,
    val summarizedMessageCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
