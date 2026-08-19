package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTripDao {
    @Query("SELECT * FROM kb_trips WHERE familyId = :familyId ORDER BY startDateEpoch DESC")
    fun observeAll(familyId: String): Flow<List<KBTripEntity>>

    @Query("SELECT * FROM kb_trips WHERE id = :id")
    fun observeById(id: String): Flow<KBTripEntity?>

    @Query("SELECT * FROM kb_trips WHERE id = :id")
    suspend fun getById(id: String): KBTripEntity?

    @Query("SELECT * FROM kb_trips WHERE familyId = :familyId")
    suspend fun getAllOnce(familyId: String): List<KBTripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTripEntity)

    @Query("DELETE FROM kb_trips WHERE familyId = :familyId AND id NOT IN (:keepIds)")
    suspend fun deleteAllExcept(familyId: String, keepIds: List<String>)

    @Query("DELETE FROM kb_trips WHERE familyId = :familyId")
    suspend fun deleteAllForFamily(familyId: String)

    @Delete
    suspend fun delete(entity: KBTripEntity)

    @Query("DELETE FROM kb_trips WHERE id = :tripId")
    suspend fun deleteById(tripId: String)

    @Query("SELECT COUNT(*) FROM kb_trips WHERE familyId = :familyId")
    suspend fun countByFamilyId(familyId: String): Int
}
