package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBSharedLocationDao {
    @Query(
        """
        SELECT * FROM kb_shared_locations
        WHERE familyId = :familyId
          AND isSharing = 1
        ORDER BY lastUpdateAtEpochMillis DESC
        """,
    )
    fun observeActiveByFamilyId(familyId: String): Flow<List<KBSharedLocationEntity>>

    @Query("SELECT * FROM kb_shared_locations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBSharedLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBSharedLocationEntity)

    @Query("DELETE FROM kb_shared_locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM kb_shared_locations WHERE familyId = :familyId")
    suspend fun deleteByFamilyId(familyId: String)
}
