package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.data.health.ai.HealthAiSummaryPolicy
import it.vittorioscocca.kidbox.data.local.dao.KBAIConversationDao
import it.vittorioscocca.kidbox.data.local.dao.KBAIMessageDao
import it.vittorioscocca.kidbox.data.local.entity.KBAIConversationEntity
import it.vittorioscocca.kidbox.data.local.entity.KBAIMessageEntity
import it.vittorioscocca.kidbox.data.remote.ai.AiReply
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HealthAIChatRepository @Inject constructor(
    private val conversationDao: KBAIConversationDao,
    private val messageDao: KBAIMessageDao,
    private val aiRepository: AiRepository,
) {

    suspend fun getOrCreateConversation(
        familyId: String,
        childId: String,
        scopeId: String,
    ): KBAIConversation {
        val existing = conversationDao.getByScope(scopeId)
        if (existing != null) return existing.toDomain()
        val now = System.currentTimeMillis()
        val entity = KBAIConversationEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            childId = childId,
            scopeId = scopeId,
            summary = null,
            summarizedMessageCount = 0,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        conversationDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun sendMessage(
        conversation: KBAIConversation,
        userText: String,
        systemPrompt: String,
    ): Result<Pair<KBAIMessage, AiReply>> = runCatching {
        val now = System.currentTimeMillis()
        val userMsg = KBAIMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversation.id,
            roleRaw = "user",
            content = userText,
            createdAtEpochMillis = now,
        )
        messageDao.upsert(userMsg)

        var conv = conversationDao.getById(conversation.id)?.toDomain() ?: conversation
        summarizeIfNeeded(conv)
        conv = conversationDao.getById(conversation.id)?.toDomain() ?: conv

        val allMessages = messageDao.getAllByConversationId(conv.id).map { it.toDomain() }
        val payloadMessages = buildPayloadMessages(conv, allMessages)
        val finalSystemPrompt = buildFinalSystemPrompt(conv, systemPrompt)

        val reply = aiRepository.askAI(conv.familyId, finalSystemPrompt, payloadMessages)
            .getOrElse { err ->
                messageDao.deleteById(userMsg.id)
                throw err
            }

        val assistantMsg = KBAIMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversation.id,
            roleRaw = "assistant",
            content = reply.reply,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        messageDao.upsert(assistantMsg)

        conversationDao.upsert(
            conv.toEntity().copy(updatedAtEpochMillis = System.currentTimeMillis()),
        )

        assistantMsg.toDomain() to reply
    }

    private suspend fun summarizeIfNeeded(conversation: KBAIConversation) {
        val allMessages = messageDao.getAllByConversationId(conversation.id)
        val total = allMessages.size
        val unsummarized = total - conversation.summarizedMessageCount
        if (unsummarized <= HealthAiSummaryPolicy.SUMMARY_THRESHOLD ||
            total <= HealthAiSummaryPolicy.RECENT_MESSAGES_TO_KEEP
        ) {
            return
        }

        val toSummarize = allMessages.take(total - HealthAiSummaryPolicy.RECENT_MESSAGES_TO_KEEP)
        val summaryInput = toSummarize.joinToString("\n") { msg ->
            "${if (msg.roleRaw == "user") "Utente" else "Assistente"}: ${msg.content}"
        }
        val summaryMessages = listOf(
            KBAIMessage(
                id = "summary-input",
                conversationId = conversation.id,
                roleRaw = "user",
                content = summaryInput,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )

        val result = aiRepository.askAI(
            conversation.familyId,
            HealthAiSummaryPolicy.summarizeSystemPromptHealthOverview,
            summaryMessages,
        ).getOrNull() ?: return

        conversationDao.upsert(
            conversation.toEntity().copy(
                summary = result.reply,
                summarizedMessageCount = toSummarize.size,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun observeMessages(conversationId: String): Flow<List<KBAIMessage>> =
        messageDao.observeByConversationId(conversationId).map { list -> list.map { it.toDomain() } }

    suspend fun clearConversation(conversation: KBAIConversation) {
        messageDao.deleteByConversationId(conversation.id)
        conversationDao.upsert(
            conversation.toEntity().copy(
                summary = null,
                summarizedMessageCount = 0,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun buildPayloadMessages(
        conversation: KBAIConversation,
        allMessages: List<KBAIMessage>,
    ): List<KBAIMessage> = allMessages.drop(conversation.summarizedMessageCount)

    private fun buildFinalSystemPrompt(conversation: KBAIConversation, baseSystemPrompt: String): String {
        val summary = conversation.summary?.takeIf { it.isNotBlank() } ?: return baseSystemPrompt
        return "$baseSystemPrompt\n\nRIASSUNTO CONVERSAZIONE PRECEDENTE\n$summary"
    }
}

// ── Entity ↔ Domain mappers ─────────────────────────────────────────────────

private fun KBAIConversationEntity.toDomain() = KBAIConversation(
    id = id,
    familyId = familyId,
    childId = childId,
    scopeId = scopeId,
    summary = summary,
    summarizedMessageCount = summarizedMessageCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun KBAIConversation.toEntity() = KBAIConversationEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    scopeId = scopeId,
    summary = summary,
    summarizedMessageCount = summarizedMessageCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun KBAIMessageEntity.toDomain() = KBAIMessage(
    id = id,
    conversationId = conversationId,
    roleRaw = roleRaw,
    content = content,
    createdAtEpochMillis = createdAtEpochMillis,
)
