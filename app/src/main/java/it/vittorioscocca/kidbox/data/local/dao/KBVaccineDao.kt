package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBVaccineDao {
    @Query("SELECT * FROM kb_vaccines WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBVaccineEntity?

    @Query(
        "SELECT * FROM kb_vaccines WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 " +
            "ORDER BY scheduledDateEpochMillis ASC",
    )
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBVaccineEntity>>

    /** Tutti i vaccini della famiglia: la Dashboard riassume la famiglia, non il singolo bambino. */
    @Query(
        "SELECT * FROM kb_vaccines WHERE familyId = :familyId AND isDeleted = 0 " +
            "ORDER BY scheduledDateEpochMillis ASC",
    )
    fun observeByFamilyId(familyId: String): Flow<List<KBVaccineEntity>>

    @Query(
        "SELECT * FROM kb_vaccines WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 " +
            "ORDER BY scheduledDateEpochMillis ASC",
    )
    suspend fun listByFamilyAndChild(familyId: String, childId: String): List<KBVaccineEntity>

    @Query("SELECT COUNT(*) FROM kb_vaccines WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun countByFamilyId(familyId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBVaccineEntity)

    @Delete
    suspend fun delete(entity: KBVaccineEntity)
}
