package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HousePaymentDao {
    @Query(
        "SELECT * FROM house_payments WHERE familyId = :familyId AND isDeleted = 0 ORDER BY name ASC",
    )
    fun observeByFamily(familyId: String): Flow<List<HousePaymentEntity>>

    @Query("SELECT * FROM house_payments WHERE id = :id")
    fun observeById(id: String): Flow<HousePaymentEntity?>

    @Query("SELECT * FROM house_payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HousePaymentEntity?

    @Query("SELECT * FROM house_payments WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun listActiveByFamily(familyId: String): List<HousePaymentEntity>

    @Query("SELECT id FROM house_payments WHERE familyId = :familyId")
    suspend fun listIdsByFamily(familyId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HousePaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HousePaymentEntity>)

    @Query("DELETE FROM house_payments WHERE familyId = :familyId")
    suspend fun deleteAllByFamily(familyId: String)
}
