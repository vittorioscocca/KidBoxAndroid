package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBDocumentCategoryDao {
    @Query("SELECT * FROM kb_document_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBDocumentCategoryEntity?

    @Query("SELECT * FROM kb_document_categories WHERE familyId = :familyId AND isDeleted = 0 ORDER BY sortOrder, title")
    fun observeByFamilyId(familyId: String): Flow<List<KBDocumentCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBDocumentCategoryEntity)

    @Delete
    suspend fun delete(entity: KBDocumentCategoryEntity)
}
