package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBHealthInsightEntity

@Dao
interface KBHealthInsightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KBHealthInsightEntity)

    @Query(
        "SELECT * FROM kb_health_insights WHERE familyId = :familyId AND isRead = 0 " +
            "ORDER BY createdAtEpochMillis DESC LIMIT 1",
    )
    suspend fun getLatestUnread(familyId: String): KBHealthInsightEntity?

    @Query("UPDATE kb_health_insights SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)
}
