package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBMedicalExamDao {
    @Query("SELECT * FROM kb_medical_exams WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBMedicalExamEntity?

    @Query("SELECT * FROM kb_medical_exams WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY deadlineEpochMillis")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBMedicalExamEntity>>

    @Query("SELECT * FROM kb_medical_exams WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY deadlineEpochMillis")
    suspend fun listByFamilyAndChild(familyId: String, childId: String): List<KBMedicalExamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBMedicalExamEntity)

    @Delete
    suspend fun delete(entity: KBMedicalExamEntity)
}
