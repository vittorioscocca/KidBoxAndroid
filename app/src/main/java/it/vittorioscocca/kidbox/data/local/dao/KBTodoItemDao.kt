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


    /**
     * I to-do sono di FAMIGLIA: nessun filtro sul bambino, come già faceva
     * `observeOpenByFamilyId` qui sotto per la Dashboard. Il campo `childId`
     * resta sull'entity e continua a essere scritto, ma non è mai stato uno
     * scoping affidabile — veniva da `children.first()` su un elenco senza
     * ordinamento garantito. Vedi il commento esteso in TodoHomeView.swift.
     */
    @Query("SELECT * FROM kb_todo_items WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dueAtEpochMillis")
    fun observeByFamily(familyId: String): Flow<List<KBTodoItemEntity>>

    @Query("SELECT * FROM kb_todo_items WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dueAtEpochMillis")
    suspend fun getByFamily(familyId: String): List<KBTodoItemEntity>

    /**
     * Tutti i to-do aperti della famiglia, senza filtro sul bambino: la Dashboard
     * riassume la famiglia, e una famiglia senza bambini associati ha comunque
     * i suoi to-do (stessa scelta fatta su iOS).
     */
    @Query("SELECT * FROM kb_todo_items WHERE familyId = :familyId AND isDeleted = 0 AND isDone = 0 ORDER BY dueAtEpochMillis")
    fun observeOpenByFamilyId(familyId: String): Flow<List<KBTodoItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTodoItemEntity)

    @Query("DELETE FROM kb_todo_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBTodoItemEntity)

    @Query("SELECT COUNT(*) FROM kb_todo_items WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun countByFamilyId(familyId: String): Int
}
