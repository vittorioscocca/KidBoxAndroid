package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTripExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTripExpenseDao {
    @Query("SELECT * FROM kb_trip_expenses WHERE tripId = :tripId ORDER BY dateString DESC")
    fun observeByTrip(tripId: String): Flow<List<KBTripExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTripExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBTripExpenseEntity>)

    @Query("DELETE FROM kb_trip_expenses WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: String)
}
