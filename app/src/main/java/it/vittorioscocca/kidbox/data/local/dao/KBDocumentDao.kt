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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBDocumentEntity>)

    @Query("SELECT * FROM kb_documents WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(
        familyId: String,
        syncStateRaw: Int,
    ): List<KBDocumentEntity>

    @Query("DELETE FROM kb_documents WHERE id = :id")
    suspend fun deleteById(id: String)
}
