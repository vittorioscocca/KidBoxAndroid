package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBPediatricProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBPediatricProfileDao {
    @Query("SELECT * FROM kb_pediatric_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBPediatricProfileEntity?

    @Query("SELECT * FROM kb_pediatric_profiles WHERE childId = :childId LIMIT 1")
    suspend fun getByChildId(childId: String): KBPediatricProfileEntity?

    @Query("SELECT * FROM kb_pediatric_profiles WHERE familyId = :familyId")
    fun observeByFamilyId(familyId: String): Flow<List<KBPediatricProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBPediatricProfileEntity)

    @Delete
    suspend fun delete(entity: KBPediatricProfileEntity)
}
