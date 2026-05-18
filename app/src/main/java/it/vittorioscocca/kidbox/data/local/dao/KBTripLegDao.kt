package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTripLegDao {
    @Query("SELECT * FROM kb_trip_legs WHERE tripId = :tripId ORDER BY `order` ASC")
    fun observeByTrip(tripId: String): Flow<List<KBTripLegEntity>>

    @Query("SELECT * FROM kb_trip_legs WHERE familyId = :familyId ORDER BY `order` ASC")
    fun observeByFamily(familyId: String): Flow<List<KBTripLegEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBTripLegEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBTripLegEntity>)

    @Query("DELETE FROM kb_trip_legs WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: String)
}
