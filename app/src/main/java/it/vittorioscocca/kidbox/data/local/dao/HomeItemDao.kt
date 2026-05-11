package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    @Query(
        "SELECT * FROM home_items WHERE familyId = :familyId AND isDeleted = 0 ORDER BY category ASC, name ASC",
    )
    fun observeByFamily(familyId: String): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items WHERE id = :id")
    fun observeById(id: String): Flow<HomeItemEntity?>

    @Query("SELECT * FROM home_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HomeItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HomeItemEntity>)

    @Query("DELETE FROM home_items WHERE familyId = :familyId")
    suspend fun deleteAllByFamily(familyId: String)
}
