package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.PwnedPrefixCacheEntity

@Dao
interface PwnedPrefixCacheDao {
    @Query("SELECT * FROM pwned_prefix_cache WHERE prefix = :prefix LIMIT 1")
    suspend fun getByPrefix(prefix: String): PwnedPrefixCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PwnedPrefixCacheEntity)

    @Query("DELETE FROM pwned_prefix_cache")
    suspend fun clearAll()
}
