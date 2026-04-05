package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBDoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBDoseLogDao {
    @Query("SELECT * FROM kb_dose_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBDoseLogEntity?

    @Query("SELECT * FROM kb_dose_logs WHERE treatmentId = :treatmentId AND isDeleted = 0 ORDER BY dayNumber, slotIndex")
    fun observeByTreatment(treatmentId: String): Flow<List<KBDoseLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBDoseLogEntity)

    @Delete
    suspend fun delete(entity: KBDoseLogEntity)
}
