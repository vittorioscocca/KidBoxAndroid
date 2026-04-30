package it.vittorioscocca.kidbox.domain.model

data class KBAIMessage(
    val id: String,
    val conversationId: String,
    val roleRaw: String,
    val content: String,
    val createdAtEpochMillis: Long,
) {
    val isUser: Boolean get() = roleRaw == "user"
    val isAssistant: Boolean get() = roleRaw == "assistant"
}
