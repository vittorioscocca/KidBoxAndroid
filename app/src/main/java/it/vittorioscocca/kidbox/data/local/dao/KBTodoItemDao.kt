package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTodoItemDao {
    @Query("SELECT * FROM kb_todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBTodoItemEntity?

    @Query("SELECT * FROM kb_todo_items WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY dueAtEpochMillis")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBTodoItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTodoItemEntity)

    @Delete
    suspend fun delete(entity: KBTodoItemEntity)
}
