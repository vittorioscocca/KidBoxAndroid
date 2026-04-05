package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBGroceryItemDao {
    @Query("SELECT * FROM kb_grocery_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBGroceryItemEntity?

    @Query("SELECT * FROM kb_grocery_items WHERE familyId = :familyId AND isDeleted = 0 ORDER BY updatedAtEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBGroceryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBGroceryItemEntity)

    @Delete
    suspend fun delete(entity: KBGroceryItemEntity)
}
