package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pets WHERE familyId = :familyId AND isDeleted = 0 ORDER BY name ASC")
    fun observeByFamily(familyId: String): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets WHERE id = :id")
    fun observeById(id: String): Flow<PetEntity?>

    @Query("SELECT * FROM pets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PetEntity?

    @Query("SELECT * FROM pets WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun getAllByFamily(familyId: String): List<PetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PetEntity>)

    @Query("DELETE FROM pets WHERE familyId = :familyId")
    suspend fun deleteAllByFamily(familyId: String)

    @Query("SELECT COUNT(*) FROM pets WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun countByFamilyId(familyId: String): Int
}
