package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pwned_prefix_cache")
data class PwnedPrefixCacheEntity(
    @PrimaryKey val prefix: String,
    val body: String,
    val fetchedAt: Long,
)
