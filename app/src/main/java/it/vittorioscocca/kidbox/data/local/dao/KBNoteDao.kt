package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBNoteDao {
    @Query("SELECT * FROM kb_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBNoteEntity?

    @Query("SELECT * FROM kb_notes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<KBNoteEntity?>

    @Query("SELECT * FROM kb_notes WHERE familyId = :familyId AND isDeleted = 0 ORDER BY updatedAtEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBNoteEntity>>

    @Query("SELECT * FROM kb_notes WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(familyId: String, syncStateRaw: Int): List<KBNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBNoteEntity)

    @Query("DELETE FROM kb_notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBNoteEntity)
}
