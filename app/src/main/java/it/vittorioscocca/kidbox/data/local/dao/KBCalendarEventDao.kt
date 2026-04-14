package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBCalendarEventDao {
    @Query("SELECT * FROM kb_calendar_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBCalendarEventEntity?

    @Query("SELECT * FROM kb_calendar_events WHERE familyId = :familyId AND isDeleted = 0 ORDER BY startDateEpochMillis")
    fun observeByFamilyId(familyId: String): Flow<List<KBCalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBCalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBCalendarEventEntity>)

    @Query(
        """
        SELECT * FROM kb_calendar_events
        WHERE familyId = :familyId
          AND syncStateRaw = :syncStateRaw
        ORDER BY updatedAtEpochMillis ASC
        """
    )
    suspend fun getBySyncState(
        familyId: String,
        syncStateRaw: Int,
    ): List<KBCalendarEventEntity>

    @Query("DELETE FROM kb_calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBCalendarEventEntity)
}
