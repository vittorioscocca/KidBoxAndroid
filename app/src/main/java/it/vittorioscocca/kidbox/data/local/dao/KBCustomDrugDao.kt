package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBCustomDrugEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBCustomDrugDao {
    @Query("SELECT * FROM kb_custom_drugs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBCustomDrugEntity?

    @Query("SELECT * FROM kb_custom_drugs WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAllActive(): Flow<List<KBCustomDrugEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBCustomDrugEntity)

    @Delete
    suspend fun delete(entity: KBCustomDrugEntity)
}
