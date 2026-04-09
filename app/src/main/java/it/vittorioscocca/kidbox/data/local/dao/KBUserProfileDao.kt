package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBUserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBUserProfileDao {
    @Query("SELECT * FROM kb_user_profiles WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): KBUserProfileEntity?

    @Query("SELECT * FROM kb_user_profiles WHERE uid = :uid LIMIT 1")
    fun observeByUid(uid: String): Flow<KBUserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBUserProfileEntity)
}
