package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBEventDao {
    @Query("SELECT * FROM kb_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBEventEntity?

    @Query("SELECT * FROM kb_events WHERE familyId = :familyId AND isDeleted = 0 ORDER BY startAtEpochMillis")
    fun observeByFamilyId(familyId: String): Flow<List<KBEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBEventEntity>)

    @Delete
    suspend fun delete(entity: KBEventEntity)
}
