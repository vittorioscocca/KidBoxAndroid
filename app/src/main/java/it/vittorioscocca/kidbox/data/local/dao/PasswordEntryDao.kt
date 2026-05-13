package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordEntryDao {
    @Query("SELECT * FROM password_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PasswordEntryEntity?

    @Query(
        """
        SELECT * FROM password_entries
        WHERE familyId = :familyId
          AND (
            visibility = 'family'
            OR (LENGTH(:currentUid) > 0 AND visibility = 'members' AND visibilityMemberIdsJson LIKE '%"' || :currentUid || '"%')
            OR (LENGTH(:currentUid) > 0 AND visibility = 'private' AND createdBy = :currentUid)
          )
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    fun observeVisibleByFamily(familyId: String, currentUid: String): Flow<List<PasswordEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PasswordEntryEntity)

    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        SELECT * FROM password_entries
        WHERE familyId = :familyId
          AND deletedAtEpochMillis IS NULL
          AND (
            visibility = 'family'
            OR (LENGTH(:currentUid) > 0 AND visibility = 'members' AND visibilityMemberIdsJson LIKE '%"' || :currentUid || '"%')
            OR (LENGTH(:currentUid) > 0 AND visibility = 'private' AND createdBy = :currentUid)
          )
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    suspend fun listVisibleForAutofill(familyId: String, currentUid: String): List<PasswordEntryEntity>
}
