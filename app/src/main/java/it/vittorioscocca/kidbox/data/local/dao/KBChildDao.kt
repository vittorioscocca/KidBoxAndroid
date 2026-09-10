package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBChildDao {
    @Query("SELECT * FROM kb_children WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBChildEntity?

    @Query("SELECT * FROM kb_children WHERE familyId = :familyId ORDER BY name COLLATE NOCASE")
    fun observeByFamilyId(familyId: String): Flow<List<KBChildEntity>>

    @Query("SELECT * FROM kb_children WHERE familyId = :familyId ORDER BY name COLLATE NOCASE")
    suspend fun getChildrenByFamilyId(familyId: String): List<KBChildEntity>

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
    suspend fun upsert(entity: KBChildEntity)

    @Upsert
    suspend fun upsertAll(entities: List<KBChildEntity>)

    @Delete
    suspend fun delete(entity: KBChildEntity)

    @Query("DELETE FROM kb_children WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM kb_children WHERE familyId = :familyId")
    suspend fun deleteByFamilyId(familyId: String): Int

    @Query("DELETE FROM kb_children")
    suspend fun deleteAll()
}
