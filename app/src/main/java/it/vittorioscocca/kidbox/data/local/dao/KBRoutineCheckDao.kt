package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBRoutineCheckDao {
    @Query("SELECT * FROM kb_routine_checks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBRoutineCheckEntity?

    @Query(
        "SELECT * FROM kb_routine_checks WHERE familyId = :familyId AND routineId = :routineId AND isDeleted = 0 ORDER BY dayKey DESC",
    )
    fun observeByRoutine(familyId: String, routineId: String): Flow<List<KBRoutineCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBRoutineCheckEntity)

    @Delete
    suspend fun delete(entity: KBRoutineCheckEntity)
}
