package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoyaltyCardDao {
    @Query("SELECT * FROM kb_loyalty_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBLoyaltyCardEntity?

    @Query(
        """
        SELECT * FROM kb_loyalty_cards
        WHERE familyId = :familyId AND isDeleted = 0
          AND (
            visibilityScope = 'family'
            OR (LENGTH(:currentUid) > 0 AND visibilityScope = 'members'
                AND visibilityMemberIdsJson LIKE '%' || :currentUid || '%')
            OR (LENGTH(:currentUid) > 0 AND visibilityScope = 'private' AND createdBy = :currentUid)
          )
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    fun observeActiveByFamilyId(familyId: String, currentUid: String): Flow<List<KBLoyaltyCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBLoyaltyCardEntity)

    @Query("DELETE FROM kb_loyalty_cards WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE kb_loyalty_cards SET isDeleted = 1, syncStateRaw = 2, updatedAtEpochMillis = :now WHERE id = :id",
    )
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM kb_loyalty_cards WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun countByFamilyId(familyId: String): Int
}
