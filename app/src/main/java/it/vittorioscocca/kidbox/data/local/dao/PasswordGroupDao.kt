package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordGroupDao {
    @Query("SELECT * FROM password_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PasswordGroupEntity?

    @Query(
        """
        SELECT * FROM password_groups
        WHERE familyId = :familyId
          AND (
            visibility = 'family'
            OR (LENGTH(:currentUid) > 0 AND visibility = 'private' AND createdBy = :currentUid)
          )
        ORDER BY updatedAtEpochMillis DESC
        """,
    )
    fun observeVisibleByFamily(familyId: String, currentUid: String): Flow<List<PasswordGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PasswordGroupEntity)

    @Query("DELETE FROM password_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}
