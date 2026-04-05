package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTreatmentDao {
    @Query("SELECT * FROM kb_treatments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBTreatmentEntity?

    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY startDateEpochMillis DESC")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBTreatmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTreatmentEntity)

    @Delete
    suspend fun delete(entity: KBTreatmentEntity)
}
