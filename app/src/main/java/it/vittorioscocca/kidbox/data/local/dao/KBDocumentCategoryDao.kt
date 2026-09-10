package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBDocumentCategoryDao {
    @Query("SELECT * FROM kb_document_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBDocumentCategoryEntity?

    @Query("SELECT * FROM kb_document_categories WHERE familyId = :familyId AND isDeleted = 0 ORDER BY sortOrder, title")
    fun observeByFamilyId(familyId: String): Flow<List<KBDocumentCategoryEntity>>

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
    suspend fun upsert(entity: KBDocumentCategoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<KBDocumentCategoryEntity>)

    @Query("SELECT * FROM kb_document_categories WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(
        familyId: String,
        syncStateRaw: Int,
    ): List<KBDocumentCategoryEntity>

    @Query("SELECT * FROM kb_document_categories WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBDocumentCategoryEntity>

    @Query(
        """
        SELECT c.* FROM kb_document_categories c
        LEFT JOIN kb_document_categories p
            ON c.parentId = p.id
            AND p.familyId = :familyId
            AND p.isDeleted = 0
        WHERE c.familyId = :familyId
          AND c.isDeleted = 0
          AND c.id LIKE 'exp-cat-%'
          AND (c.parentId IS NULL OR p.id IS NULL)
        """,
    )
    suspend fun getOrphanedExpenseCategories(familyId: String): List<KBDocumentCategoryEntity>

    @Query("DELETE FROM kb_document_categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
