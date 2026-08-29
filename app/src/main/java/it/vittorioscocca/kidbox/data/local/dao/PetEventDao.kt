package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PetEventDao {
    @Query(
        "SELECT * FROM pet_events WHERE familyId = :familyId AND petId = :petId AND isDeleted = 0 ORDER BY date DESC",
    )
    fun observeByPet(familyId: String, petId: String): Flow<List<PetEventEntity>>

    /** Gli eventi di tutti gli animali: la Dashboard non sa quale animale mostrare. */
    @Query("SELECT * FROM pet_events WHERE familyId = :familyId AND isDeleted = 0 ORDER BY date ASC")
    fun observeByFamily(familyId: String): Flow<List<PetEventEntity>>

    @Query("SELECT * FROM pet_events WHERE id = :id")
    fun observeById(id: String): Flow<PetEventEntity?>

    @Query("SELECT * FROM pet_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PetEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PetEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PetEventEntity>)

    @Query("DELETE FROM pet_events WHERE familyId = :familyId")
    suspend fun deleteAllByFamily(familyId: String)
}
