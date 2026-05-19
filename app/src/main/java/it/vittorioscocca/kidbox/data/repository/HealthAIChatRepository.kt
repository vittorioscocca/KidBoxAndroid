package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.data.local.dao.KBAIConversationDao
import it.vittorioscocca.kidbox.data.health.ai.HealthContextCompaction
import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.local.dao.KBAIMessageDao
import it.vittorioscocca.kidbox.data.local.entity.KBAIConversationEntity
import it.vittorioscocca.kidbox.data.local.entity.KBAIMessageEntity
import it.vittorioscocca.kidbox.data.remote.ai.AiReply
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.ui.screens.ai.planning.KidBoxAIActionPipeline
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HealthAISendResult(
    val assistantMessage: KBAIMessage,
    val reply: AiReply,
    val executionSummary: String?,
    val didAutoExecute: Boolean,
)

data class CompactPayloadEstimate(
    val askUnits: Int,
    val setupUnits: Int,
)

@Singleton
class HealthAIChatRepository @Inject constructor(
    private val conversationDao: KBAIConversationDao,
    private val messageDao: KBAIMessageDao,
    private val aiRepository: AiRepository,
    private val actionPipeline: KidBoxAIActionPipeline,
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
        resolvedSystemPrompt: String? = null,
    ): Result<HealthAISendResult> = runCatching {
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
        val base = resolvedSystemPrompt ?: buildFinalSystemPrompt(conv, systemPrompt)

        val reply = aiRepository.askAI(conv.familyId, base, payloadMessages)
            .getOrElse { err ->
                messageDao.deleteById(userMsg.id)
                throw err
            }

        val defaultChildId = conv.childId.takeIf {
            it.isNotBlank() && it != "health_visit" && !it.startsWith("health_")
        }
        val outcome = actionPipeline.processReply(
            reply = reply.reply,
            familyId = conv.familyId,
            defaultChildId = defaultChildId,
        )

        val assistantMsg = KBAIMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversation.id,
            roleRaw = "assistant",
            content = outcome.displayText,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        messageDao.upsert(assistantMsg)

        conversationDao.upsert(
            conv.toEntity().copy(updatedAtEpochMillis = System.currentTimeMillis()),
        )

        HealthAISendResult(
            assistantMessage = assistantMsg.toDomain(),
            reply = reply,
            executionSummary = outcome.executionSummary,
            didAutoExecute = outcome.didAutoExecute,
        )
    }

    private suspend fun summarizeIfNeeded(conversation: KBAIConversation) {
        // Legacy no-op.
    }

    suspend fun compactConversation(conversation: KBAIConversation): Boolean {
        val allMessages = messageDao.getAllByConversationId(conversation.id)
        if (allMessages.isEmpty()) return false
        val fullPayload = allMessages.map { it.toDomain() }
        val result = aiRepository.askAI(
            conversation.familyId,
            COMPACTION_SYSTEM_PROMPT,
            fullPayload,
        ).getOrNull() ?: return false

        messageDao.deleteByConversationId(conversation.id)
        messageDao.upsert(
            KBAIMessageEntity(
                id = "summary-${conversation.id}",
                conversationId = conversation.id,
                roleRaw = "assistant",
                content = result.reply,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        conversationDao.upsert(
            conversation.toEntity().copy(
                summary = result.reply,
                summarizedMessageCount = 0,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return true
    }

    fun observeMessages(conversationId: String): Flow<List<KBAIMessage>> =
        messageDao.observeByConversationId(conversationId).map { list -> list.map { it.toDomain() } }

    fun estimatePayloadCost(
        conversation: KBAIConversation,
        baseSystemPrompt: String,
        allMessages: List<KBAIMessage>,
        pendingUserText: String = "",
    ): Int {
        val payload = buildPayloadMessages(conversation, allMessages)
        val prompt = buildFinalSystemPrompt(conversation, baseSystemPrompt)
        val total = AIAskAIPayload.totalChars(prompt, payload, pendingUserText)
        return AIAskAIPayload.messageUnits(total)
    }

    fun estimateCompactPayloadCost(
        conversation: KBAIConversation,
        baseSystemPrompt: String,
        allMessages: List<KBAIMessage>,
        pendingUserText: String,
        subjectName: String,
        compactHealthSummary: String?,
        healthContextFingerprint: Int,
        cachedFingerprint: Int?,
    ): CompactPayloadEstimate {
        val payload = buildPayloadMessages(conversation, allMessages)
        val hasCache = compactHealthSummary != null && cachedFingerprint == healthContextFingerprint
        val summaryText = if (hasCache) {
            compactHealthSummary!!
        } else {
            val estimatedLen = minOf(12_000, maxOf(4_000, baseSystemPrompt.length / 6))
            "·".repeat(estimatedLen)
        }
        val prompt = compactSystemPrompt(summaryText, subjectName, conversation)
        val askUnits = AIAskAIPayload.messageUnits(
            AIAskAIPayload.totalChars(prompt, payload, pendingUserText),
        )
        val setupUnits = if (hasCache) {
            0
        } else {
            AIAskAIPayload.messageUnits(
                baseSystemPrompt.length +
                    HealthContextCompaction.SUMMARIZATION_SYSTEM_PROMPT.length +
                    256,
            )
        }
        return CompactPayloadEstimate(askUnits = askUnits, setupUnits = setupUnits)
    }

    suspend fun summarizeHealthContext(
        familyId: String,
        fullSystemPrompt: String,
    ): Result<String> = runCatching {
        val messages = listOf(
            KBAIMessage(
                id = "health-context-input",
                conversationId = "",
                roleRaw = "user",
                content = "Comprimi il seguente contesto sanitario:\n\n$fullSystemPrompt",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        val reply = aiRepository.askAI(
            familyId,
            HealthContextCompaction.SUMMARIZATION_SYSTEM_PROMPT,
            messages,
        ).getOrThrow()
        reply.reply.trim().takeIf { it.isNotEmpty() }
            ?: error("Impossibile riassumere il contesto sanitario.")
    }

    fun compactSystemPrompt(
        healthSummary: String,
        subjectName: String,
        conversation: KBAIConversation,
    ): String {
        var prompt = HealthContextCompaction.buildCompactSystemPrompt(healthSummary, subjectName)
        conversation.summary?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            prompt += "\n\nRIASSUNTO CONVERSAZIONE PRECEDENTE\n$s"
        }
        return prompt
    }

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
    ): List<KBAIMessage> {
        val summary = conversation.summary?.takeIf { it.isNotBlank() }
        val summaryMessage = summary?.let {
            KBAIMessage(
                id = "summary-${conversation.id}",
                conversationId = conversation.id,
                roleRaw = "assistant",
                content = it,
                createdAtEpochMillis = System.currentTimeMillis(),
                isSummary = true,
            )
        }
        val recent = allMessages
            .filterNot { msg -> summary != null && msg.roleRaw == "assistant" && msg.content == summary }
            .takeLast(6)
        return (listOfNotNull(summaryMessage) + recent).take(7)
    }

    private fun buildFinalSystemPrompt(
        conversation: KBAIConversation,
        baseSystemPrompt: String,
    ): String {
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
    isSummary = id.startsWith("summary-"),
)

private const val COMPACTION_SYSTEM_PROMPT =
    "Riassumi in modo conciso ma completo la conversazione seguente, mantenendo i punti chiave, le decisioni prese e il contesto importante. Il riassunto sarà usato come contesto per continuare la conversazione."
