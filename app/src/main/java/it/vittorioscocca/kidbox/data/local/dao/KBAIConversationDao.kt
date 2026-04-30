package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBAIConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBAIConversationDao {
    @Query("SELECT * FROM kb_ai_conversations WHERE scopeId = :scopeId LIMIT 1")
    suspend fun getByScope(scopeId: String): KBAIConversationEntity?

    @Query("SELECT * FROM kb_ai_conversations WHERE scopeId = :scopeId LIMIT 1")
    fun observeByScope(scopeId: String): Flow<KBAIConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBAIConversationEntity)

    @Query("DELETE FROM kb_ai_conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}
