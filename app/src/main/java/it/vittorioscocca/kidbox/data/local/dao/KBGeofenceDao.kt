package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBGeofenceDao {
    @Query("SELECT * FROM kb_geofences WHERE familyId = :familyId AND isDeleted = 0 ORDER BY name ASC")
    fun observeByFamily(familyId: String): Flow<List<KBGeofenceEntity>>

    @Query("SELECT * FROM kb_geofences WHERE familyId = :familyId AND isDeleted = 0 ORDER BY name ASC")
    suspend fun listByFamily(familyId: String): List<KBGeofenceEntity>

    @Query("SELECT * FROM kb_geofences WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBGeofenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBGeofenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBGeofenceEntity>)

    @Query("DELETE FROM kb_geofences WHERE familyId = :familyId")
    suspend fun deleteAllByFamily(familyId: String)

    @Query("SELECT COUNT(*) FROM kb_geofences WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun countByFamilyId(familyId: String): Int
}
