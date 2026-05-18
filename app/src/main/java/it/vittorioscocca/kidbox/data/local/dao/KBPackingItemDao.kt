package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBPackingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBPackingItemDao {
    @Query("SELECT * FROM kb_packing_items WHERE tripId = :tripId ORDER BY categoryRaw ASC, label ASC")
    fun observeByTrip(tripId: String): Flow<List<KBPackingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBPackingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBPackingItemEntity>)

    @Query("UPDATE kb_packing_items SET isChecked = :checked, updatedAtEpoch = :now WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM kb_packing_items WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: String)
}
