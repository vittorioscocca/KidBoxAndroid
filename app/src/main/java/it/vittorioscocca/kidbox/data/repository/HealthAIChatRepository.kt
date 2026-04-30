package it.vittorioscocca.kidbox.data.repository

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

private const val SUMMARY_THRESHOLD = 8
private const val RECENT_TO_KEEP = 4

private val SUMMARIZE_SYSTEM_PROMPT = """
Riassumi in modo fedele e compatto la conversazione seguente.
Mantieni:
- richieste principali dell'utente
- dati sanitari discussi (cure, vaccini, visite, esami)
- risultati o referti menzionati
- eventuali dubbi ancora aperti
Non aggiungere nulla di nuovo.
""".trimIndent()

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

        val allMessages = messageDao.getAllByConversationId(conversation.id)
        val payloadMessages = buildPayloadMessages(conversation, allMessages.map { it.toDomain() })
        val finalSystemPrompt = buildFinalSystemPrompt(conversation, systemPrompt)

        val reply = aiRepository.askAI(conversation.familyId, finalSystemPrompt, payloadMessages)
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
            conversation.toEntity().copy(updatedAtEpochMillis = System.currentTimeMillis()),
        )

        assistantMsg.toDomain() to reply
    }

    suspend fun summarizeIfNeeded(conversation: KBAIConversation, systemPrompt: String) {
        val allMessages = messageDao.getAllByConversationId(conversation.id)
        val total = allMessages.size
        val unsummarized = total - conversation.summarizedMessageCount
        if (unsummarized <= SUMMARY_THRESHOLD || total <= RECENT_TO_KEEP) return

        val toSummarize = allMessages.take(total - RECENT_TO_KEEP)
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

        val result = aiRepository.askAI(conversation.familyId, SUMMARIZE_SYSTEM_PROMPT, summaryMessages)
            .getOrNull() ?: return

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
