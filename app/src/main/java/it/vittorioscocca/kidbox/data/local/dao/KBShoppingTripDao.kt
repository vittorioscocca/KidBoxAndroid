package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBShoppingTripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBShoppingTripDao {
    @Query("SELECT * FROM kb_shopping_trips WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBShoppingTripEntity?

    @Query(
        "SELECT * FROM kb_shopping_trips WHERE familyId = :familyId AND isDeleted = 0 " +
            "ORDER BY dateEpochMillis DESC",
    )
    fun observeByFamilyId(familyId: String): Flow<List<KBShoppingTripEntity>>

    @Query("SELECT * FROM kb_shopping_trips WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun listByFamilyId(familyId: String): List<KBShoppingTripEntity>

    @Query("SELECT * FROM kb_shopping_trips WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(familyId: String, syncStateRaw: Int): List<KBShoppingTripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBShoppingTripEntity)

    @Query("DELETE FROM kb_shopping_trips WHERE id = :id")
    suspend fun deleteById(id: String)
}
