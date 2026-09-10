package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTodoListDao {
    @Query("SELECT * FROM kb_todo_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBTodoListEntity?

    /** Come i to-do: le liste sono di famiglia, non del singolo bambino. */
    @Query("SELECT * FROM kb_todo_lists WHERE familyId = :familyId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeByFamily(familyId: String): Flow<List<KBTodoListEntity>>

    @Query("SELECT * FROM kb_todo_lists WHERE familyId = :familyId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getByFamily(familyId: String): List<KBTodoListEntity>

    // `@Upsert` e NON `@Insert(onConflict = REPLACE)`.
    //
    // REPLACE in SQLite è un DELETE + INSERT, e il DELETE fa scattare le azioni
    // delle foreign key che puntano a questa tabella: i figli con CASCADE
    // spariscono, quelli con SET NULL restano scollegati. Riscrivere una riga
    // identica a se stessa cancellava così i suoi figli — è il motivo per cui
    // dopo un force refresh una lista to-do appariva vuota, e la stessa cosa era
    // già stata corretta su KBFamilyDao per i membri.
    // `@Upsert` fa INSERT e, in conflitto, UPDATE: nessun DELETE, nessuna
    // cascata.
    @Upsert
    suspend fun upsert(entity: KBTodoListEntity)

    @Query("DELETE FROM kb_todo_lists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBTodoListEntity)
}
