package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBWorkProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWorkRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Schede e documenti di Lavoro. Tabelle foglia (nessuna FK punta qui): REPLACE
 * non porta via figli. La visibilità non si filtra in SQL: Room contiene solo
 * ciò che le query vincolate di Firestore hanno consegnato.
 */
@Dao
interface WorkDao {

    // ── Schede ──────────────────────────────────────────────────────────
    @Query("SELECT * FROM kb_work_profiles WHERE familyId = :familyId AND isDeleted = 0")
    fun observeProfiles(familyId: String): Flow<List<KBWorkProfileEntity>>

    @Query("SELECT * FROM kb_work_profiles WHERE familyId = :familyId AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getProfile(familyId: String, ownerUid: String): KBWorkProfileEntity?

    @Query("SELECT * FROM kb_work_profiles WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun listProfiles(familyId: String): List<KBWorkProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: KBWorkProfileEntity)

    @Query("DELETE FROM kb_work_profiles WHERE familyId = :familyId AND ownerUid = :ownerUid")
    suspend fun deleteProfile(familyId: String, ownerUid: String)

    // ── Documenti ───────────────────────────────────────────────────────
    @Query(
        """
        SELECT * FROM kb_work_records
        WHERE familyId = :familyId AND isDeleted = 0
        ORDER BY referenceDateEpochMillis DESC
        """,
    )
    fun observeRecords(familyId: String): Flow<List<KBWorkRecordEntity>>

    @Query("SELECT * FROM kb_work_records WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun listRecords(familyId: String): List<KBWorkRecordEntity>

    @Query("SELECT * FROM kb_work_records WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: String): KBWorkRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(entity: KBWorkRecordEntity)

    @Query("DELETE FROM kb_work_records WHERE id = :id")
    suspend fun deleteRecord(id: String)

    @Query("DELETE FROM kb_work_records WHERE familyId = :familyId AND ownerUid = :ownerUid")
    suspend fun deleteRecordsByOwner(familyId: String, ownerUid: String)

    @Query("DELETE FROM kb_work_profiles WHERE familyId = :familyId")
    suspend fun deleteProfilesByFamily(familyId: String)

    @Query("DELETE FROM kb_work_records WHERE familyId = :familyId")
    suspend fun deleteRecordsByFamily(familyId: String)
}
