package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBAIMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBAIMessageDao {
    @Query("SELECT * FROM kb_ai_messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    fun observeByConversationId(conversationId: String): Flow<List<KBAIMessageEntity>>

    @Query("SELECT * FROM kb_ai_messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    suspend fun getAllByConversationId(conversationId: String): List<KBAIMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBAIMessageEntity)

    @Query("DELETE FROM kb_ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query("DELETE FROM kb_ai_messages WHERE id = :id")
    suspend fun deleteById(id: String)
}
