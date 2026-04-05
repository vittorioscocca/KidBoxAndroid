package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBMedicalVisitDao {
    @Query("SELECT * FROM kb_medical_visits WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBMedicalVisitEntity?

    @Query("SELECT * FROM kb_medical_visits WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY dateEpochMillis DESC")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBMedicalVisitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBMedicalVisitEntity)

    @Delete
    suspend fun delete(entity: KBMedicalVisitEntity)
}
