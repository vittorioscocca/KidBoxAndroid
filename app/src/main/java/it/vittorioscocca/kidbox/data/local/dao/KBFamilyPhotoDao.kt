package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBFamilyPhotoDao {
    @Query("SELECT * FROM kb_family_photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBFamilyPhotoEntity?

    @Query("SELECT * FROM kb_family_photos WHERE familyId = :familyId AND isDeleted = 0 ORDER BY takenAtEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBFamilyPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBFamilyPhotoEntity)

    @Delete
    suspend fun delete(entity: KBFamilyPhotoEntity)
}
