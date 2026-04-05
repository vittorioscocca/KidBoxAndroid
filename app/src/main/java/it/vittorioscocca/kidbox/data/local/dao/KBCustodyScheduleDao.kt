package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBCustodyScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBCustodyScheduleDao {
    @Query("SELECT * FROM kb_custody_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBCustodyScheduleEntity?

    @Query("SELECT * FROM kb_custody_schedules WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBCustodyScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBCustodyScheduleEntity)

    @Delete
    suspend fun delete(entity: KBCustodyScheduleEntity)
}
