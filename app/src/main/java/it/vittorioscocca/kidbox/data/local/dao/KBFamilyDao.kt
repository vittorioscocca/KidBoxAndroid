package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBFamilyDao {
    @Query("SELECT * FROM kb_families WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBFamilyEntity?

    @Query("SELECT * FROM kb_families ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<KBFamilyEntity>>

    @Query("SELECT * FROM kb_families WHERE id = :familyId LIMIT 1")
    fun observeById(familyId: String): Flow<KBFamilyEntity?>
    
    @Query("SELECT EXISTS(SELECT 1 FROM kb_families LIMIT 1)")
    suspend fun hasAnyFamily(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBFamilyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBFamilyEntity>)

    @Delete
    suspend fun delete(entity: KBFamilyEntity)

    @Query("DELETE FROM kb_families WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM kb_families WHERE id = :familyId")
    suspend fun deleteByFamilyId(familyId: String): Int

    @Query("DELETE FROM kb_families")
    suspend fun deleteAll()

    @Query("SELECT id FROM kb_families LIMIT 1")
    suspend fun peekAnyFamilyId(): String?
}
