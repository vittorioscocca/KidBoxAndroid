package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBExpenseDao {
    @Query("SELECT * FROM kb_expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBExpenseEntity?

    @Query("SELECT * FROM kb_expenses WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dateEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBExpenseEntity)

    @Delete
    suspend fun delete(entity: KBExpenseEntity)
}
