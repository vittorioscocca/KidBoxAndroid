package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBRoutineDao {
    @Query("SELECT * FROM kb_routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBRoutineEntity?

    @Query("SELECT * FROM kb_routines WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY sortOrder, title")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBRoutineEntity>>

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
    suspend fun upsert(entity: KBRoutineEntity)

    @Delete
    suspend fun delete(entity: KBRoutineEntity)
}
