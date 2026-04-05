package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBRoutineDao {
    @Query("SELECT * FROM kb_routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBRoutineEntity?

    @Query("SELECT * FROM kb_routines WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY sortOrder, title")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBRoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBRoutineEntity)

    @Delete
    suspend fun delete(entity: KBRoutineEntity)
}
