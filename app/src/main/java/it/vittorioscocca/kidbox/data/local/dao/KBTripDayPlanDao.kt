package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTripDayPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTripDayPlanDao {
    @Query("SELECT * FROM kb_trip_day_plans WHERE tripId = :tripId ORDER BY dateString ASC")
    fun observeByTrip(tripId: String): Flow<List<KBTripDayPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTripDayPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBTripDayPlanEntity>)

    @Query("DELETE FROM kb_trip_day_plans WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: String)
}
