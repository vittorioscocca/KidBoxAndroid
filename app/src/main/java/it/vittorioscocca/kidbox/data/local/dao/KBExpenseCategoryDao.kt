package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBExpenseCategoryDao {
    @Query("SELECT * FROM kb_expense_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBExpenseCategoryEntity?

    @Query("SELECT * FROM kb_expense_categories WHERE familyId = :familyId AND isDeleted = 0 ORDER BY sortIndex, name")
    fun observeByFamilyId(familyId: String): Flow<List<KBExpenseCategoryEntity>>

    @Query("SELECT * FROM kb_expense_categories WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBExpenseCategoryEntity>

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
    suspend fun upsert(entity: KBExpenseCategoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<KBExpenseCategoryEntity>)

    @Query("DELETE FROM kb_expense_categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
