package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBDocumentDao {
    @Query("SELECT * FROM kb_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBDocumentEntity?

    @Query("SELECT * FROM kb_documents WHERE familyId = :familyId AND isDeleted = 0 ORDER BY updatedAtEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBDocumentEntity>>

    @Query(
        """
        SELECT * FROM kb_documents
        WHERE familyId = :familyId
          AND isDeleted = 0
          AND categoryId IS NULL
          AND id NOT LIKE 'exp-%'
          AND (notes IS NULL OR notes NOT LIKE 'expense:%')
          AND lower(fileName) NOT LIKE 'document:%'
          AND lower(title) NOT LIKE 'document:%'
          AND instr(lower(fileName), '%3a') = 0
          AND instr(lower(title), '%3a') = 0
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    fun observeRootVisibleByFamilyId(familyId: String): Flow<List<KBDocumentEntity>>

    @Query(
        """
        SELECT * FROM kb_documents
        WHERE familyId = :familyId
          AND isDeleted = 0
          AND categoryId IS NULL
          AND (id LIKE 'exp-%' OR notes LIKE 'expense:%')
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    fun observeRootHiddenExpenseOrphansByFamilyId(familyId: String): Flow<List<KBDocumentEntity>>

    @Query(
        """
        SELECT * FROM kb_documents
        WHERE familyId = :familyId
          AND isDeleted = 0
          AND categoryId IS NULL
          AND (
              lower(fileName) LIKE 'document:%'
              OR lower(title) LIKE 'document:%'
              OR instr(lower(fileName), '%3a') > 0
              OR instr(lower(title), '%3a') > 0
          )
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    fun observeRootHiddenSystemEncodedByFamilyId(familyId: String): Flow<List<KBDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBDocumentEntity>)

    @Query("SELECT * FROM kb_documents WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(
        familyId: String,
        syncStateRaw: Int,
    ): List<KBDocumentEntity>

    @Query("SELECT * FROM kb_documents WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBDocumentEntity>

    @Query(
        """
        SELECT d.* FROM kb_documents d
        LEFT JOIN kb_document_categories c
            ON d.categoryId = c.id
            AND c.familyId = :familyId
            AND c.isDeleted = 0
        WHERE d.familyId = :familyId
          AND d.isDeleted = 0
          AND (d.id LIKE 'exp-%' OR d.notes LIKE 'expense:%')
          AND d.categoryId IS NOT NULL
          AND c.id IS NULL
        """,
    )
    suspend fun getOrphanedExpenseDocuments(familyId: String): List<KBDocumentEntity>

    @Query("DELETE FROM kb_documents WHERE id = :id")
    suspend fun deleteById(id: String)
}
