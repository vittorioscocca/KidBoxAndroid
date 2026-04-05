package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBChildDao {
    @Query("SELECT * FROM kb_children WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBChildEntity?

    @Query("SELECT * FROM kb_children WHERE familyId = :familyId ORDER BY name COLLATE NOCASE")
    fun observeByFamilyId(familyId: String): Flow<List<KBChildEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBChildEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBChildEntity>)

    @Delete
    suspend fun delete(entity: KBChildEntity)

    @Query("DELETE FROM kb_children WHERE id = :id")
    suspend fun deleteById(id: String)
}
