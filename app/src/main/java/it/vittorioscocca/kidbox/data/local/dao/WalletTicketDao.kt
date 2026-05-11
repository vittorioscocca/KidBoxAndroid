package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletTicketDao {
    @Query("SELECT * FROM kb_wallet_tickets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBWalletTicketEntity?

    @Query(
        """
        SELECT * FROM kb_wallet_tickets
        WHERE familyId = :familyId AND isDeleted = 0
          AND (
            visibilityScope = 'family'
            OR (LENGTH(:currentUid) > 0 AND visibilityScope = 'members'
                AND visibilityMemberIdsJson LIKE '%' || :currentUid || '%')
            OR (LENGTH(:currentUid) > 0 AND visibilityScope = 'private' AND createdBy = :currentUid)
          )
        ORDER BY
            CASE WHEN eventDateEpochMillis IS NULL THEN 1 ELSE 0 END,
            eventDateEpochMillis ASC,
            updatedAtEpochMillis DESC
        """,
    )
    fun observeActiveByFamilyId(familyId: String, currentUid: String): Flow<List<KBWalletTicketEntity>>

    @Query(
        """
        SELECT * FROM kb_wallet_tickets
        WHERE familyId = :familyId AND isDeleted = 0
          AND (
            visibilityScope = 'family'
            OR (LENGTH(:currentUid) > 0 AND visibilityScope = 'members'
                AND visibilityMemberIdsJson LIKE '%' || :currentUid || '%')
            OR (LENGTH(:currentUid) > 0 AND visibilityScope = 'private' AND createdBy = :currentUid)
          )
        """,
    )
    suspend fun getActiveByFamilyId(familyId: String, currentUid: String): List<KBWalletTicketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBWalletTicketEntity)

    @Query("DELETE FROM kb_wallet_tickets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE kb_wallet_tickets SET isDeleted = 1, syncStateRaw = 2, updatedAtEpochMillis = :now WHERE id = :id",
    )
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM kb_wallet_tickets WHERE syncStateRaw != 0")
    suspend fun getPending(): List<KBWalletTicketEntity>
}
