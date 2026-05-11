package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleEventDao {
    @Query(
        "SELECT * FROM vehicle_events WHERE familyId = :familyId AND vehicleId = :vehicleId AND isDeleted = 0 ORDER BY date DESC",
    )
    fun observeByVehicle(familyId: String, vehicleId: String): Flow<List<VehicleEventEntity>>

    @Query("SELECT * FROM vehicle_events WHERE id = :id")
    fun observeById(id: String): Flow<VehicleEventEntity?>

    @Query("SELECT * FROM vehicle_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VehicleEventEntity?

    @Query(
        "SELECT * FROM vehicle_events WHERE familyId = :familyId AND vehicleId = :vehicleId AND isDeleted = 0",
    )
    suspend fun listActiveByVehicle(familyId: String, vehicleId: String): List<VehicleEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VehicleEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VehicleEventEntity>)

    @Query("DELETE FROM vehicle_events WHERE familyId = :familyId")
    suspend fun deleteAllByFamily(familyId: String)
}
