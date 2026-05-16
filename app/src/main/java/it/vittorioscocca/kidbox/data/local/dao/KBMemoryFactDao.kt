package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBMemoryFactEntity

@Dao
interface KBMemoryFactDao {
    @Query(
        """
        SELECT * FROM kb_memory_facts
        WHERE familyId = :familyId
        ORDER BY updatedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentForFamily(familyId: String, limit: Int): List<KBMemoryFactEntity>

    @Query(
        """
        SELECT * FROM kb_memory_facts
        WHERE familyId = :familyId
        ORDER BY createdAtEpochMillis ASC
        """,
    )
    suspend fun getAllForFamilyAscending(familyId: String): List<KBMemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(facts: List<KBMemoryFactEntity>)

    @Query("DELETE FROM kb_memory_facts WHERE id = :id")
    suspend fun deleteById(id: String)
}
