package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBChatMessageDao {
    @Query("SELECT * FROM kb_chat_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBChatMessageEntity?

    @Query("SELECT * FROM kb_chat_messages WHERE familyId = :familyId ORDER BY createdAtEpochMillis ASC")
    fun observeByFamilyId(familyId: String): Flow<List<KBChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBChatMessageEntity>)

    @Query("DELETE FROM kb_chat_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM kb_chat_messages WHERE familyId = :familyId")
    suspend fun deleteAllByFamilyId(familyId: String)

    @Query("SELECT * FROM kb_chat_messages WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBChatMessageEntity>

    @Query("SELECT * FROM kb_chat_messages WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(familyId: String, syncStateRaw: Int): List<KBChatMessageEntity>

    @Query("SELECT mediaLocalPath FROM kb_chat_messages WHERE familyId = :familyId AND mediaLocalPath IS NOT NULL")
    suspend fun getMediaLocalPathsByFamilyId(familyId: String): List<String>

    @Delete
    suspend fun delete(entity: KBChatMessageEntity)
}
