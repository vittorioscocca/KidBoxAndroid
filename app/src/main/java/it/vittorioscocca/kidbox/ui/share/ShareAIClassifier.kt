package it.vittorioscocca.kidbox.ui.share

import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole
import kotlinx.coroutines.withTimeoutOrNull

object ShareAIClassifier {
    private const val SYSTEM_PROMPT =
        "Sei un classificatore. Dato il testo seguente, rispondi SOLO con una delle seguenti destinazioni: CHAT, NOTE, TODO, SHOPPING, EVENT. Nessun altro testo."

    suspend fun classifyText(
        text: String,
        familyId: String,
        aiService: AIService,
    ): ShareDestination? {
        if (text.isBlank() || familyId.isBlank()) return null
        val response = withTimeoutOrNull(3_000) {
            aiService.sendMessage(
                messages = listOf(
                    KBAIMessage(
                        id = "share-ai-${System.currentTimeMillis()}",
                        conversationId = "share-ai",
                        roleRaw = AIMessageRole.USER.value,
                        content = text,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                ),
                systemPrompt = SYSTEM_PROMPT,
                familyId = familyId,
            ).getOrNull()
        } ?: return null

        val token = response.reply.trim().uppercase()
        return when {
            token.contains("CHAT") -> ShareDestination.CHAT
            token.contains("NOTE") -> ShareDestination.NOTE
            token.contains("TODO") -> ShareDestination.TODO
            token.contains("SHOPPING") -> ShareDestination.SHOPPING
            token.contains("EVENT") -> ShareDestination.EVENT
            else -> null
        }
    }
}

