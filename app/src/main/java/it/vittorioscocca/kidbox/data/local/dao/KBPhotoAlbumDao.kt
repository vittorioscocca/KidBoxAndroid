package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBPhotoAlbumDao {
    @Query("SELECT * FROM kb_photo_albums WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBPhotoAlbumEntity?

    @Query("SELECT * FROM kb_photo_albums WHERE familyId = :familyId AND isDeleted = 0 ORDER BY sortOrder, title")
    fun observeByFamilyId(familyId: String): Flow<List<KBPhotoAlbumEntity>>

    @Query("SELECT * FROM kb_photo_albums WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBPhotoAlbumEntity>

    @Query("SELECT * FROM kb_photo_albums WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(
        familyId: String,
        syncStateRaw: Int,
    ): List<KBPhotoAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBPhotoAlbumEntity)

    @Query("DELETE FROM kb_photo_albums WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBPhotoAlbumEntity)
}
