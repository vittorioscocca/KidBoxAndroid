package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.data.local.dao.KBAIConversationDao
import it.vittorioscocca.kidbox.data.local.dao.KBAIMessageDao
import it.vittorioscocca.kidbox.data.local.entity.KBAIConversationEntity
import it.vittorioscocca.kidbox.data.local.entity.KBAIMessageEntity
import it.vittorioscocca.kidbox.domain.model.KBAIConversation
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class KBAIRepository @Inject constructor(
    private val conversationDao: KBAIConversationDao,
    private val messageDao: KBAIMessageDao,
) {
    suspend fun getOrCreateConversation(scopeId: String, familyId: String): KBAIConversation {
        val existing = conversationDao.observeByScope(scopeId).first()
        if (existing != null) return existing.toDomain()
        val now = System.currentTimeMillis()
        val entity = KBAIConversationEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            childId = scopeId,
            scopeId = scopeId,
            summary = null,
            summarizedMessageCount = 0,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        conversationDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun addMessage(conversationId: String, role: AIMessageRole, content: String): KBAIMessage {
        val entity = KBAIMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            roleRaw = role.value,
            content = content,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        messageDao.upsert(entity)
        return entity.toDomain()
    }

    fun observeMessages(conversationId: String): Flow<List<KBAIMessage>> =
        messageDao.observeByConversationId(conversationId).map { rows -> rows.map { it.toDomain() } }

    suspend fun updateConversationSummary(conversationId: String, summary: String, count: Int) {
        val existing = conversationDao.getById(conversationId) ?: return
        conversationDao.upsert(
            existing.copy(
                summary = summary,
                summarizedMessageCount = count,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearAndSeedSummary(conversationId: String, summary: String) {
        val existing = conversationDao.getById(conversationId) ?: return
        messageDao.deleteByConversationId(conversationId)
        val summaryMessage = KBAIMessageEntity(
            id = "summary-$conversationId",
            conversationId = conversationId,
            roleRaw = AIMessageRole.ASSISTANT.value,
            content = summary,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        messageDao.upsert(summaryMessage)
        conversationDao.upsert(
            existing.copy(
                summary = summary,
                summarizedMessageCount = 0,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearConversation(conversationId: String) {
        val existing = conversationDao.getById(conversationId) ?: return
        messageDao.deleteByConversationId(conversationId)
        conversationDao.upsert(
            existing.copy(
                summary = null,
                summarizedMessageCount = 0,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}

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

private fun KBAIMessageEntity.toDomain() = KBAIMessage(
    id = id,
    conversationId = conversationId,
    roleRaw = roleRaw,
    content = content,
    createdAtEpochMillis = createdAtEpochMillis,
    isSummary = id.startsWith("summary-"),
)
