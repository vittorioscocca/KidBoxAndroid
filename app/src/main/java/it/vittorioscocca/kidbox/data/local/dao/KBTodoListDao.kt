package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTodoListDao {
    @Query("SELECT * FROM kb_todo_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBTodoListEntity?

    @Query("SELECT * FROM kb_todo_lists WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBTodoListEntity>>

    @Query("SELECT * FROM kb_todo_lists WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getByFamilyAndChild(familyId: String, childId: String): List<KBTodoListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTodoListEntity)

    @Query("DELETE FROM kb_todo_lists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBTodoListEntity)
}
