package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBFamilyMemberDao {
    @Query("SELECT * FROM kb_family_members WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBFamilyMemberEntity?

    @Query("SELECT * FROM kb_family_members WHERE familyId = :familyId AND isDeleted = 0")
    fun observeActiveByFamilyId(familyId: String): Flow<List<KBFamilyMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBFamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBFamilyMemberEntity>)

    @Delete
    suspend fun delete(entity: KBFamilyMemberEntity)

    @Query("DELETE FROM kb_family_members WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM kb_family_members WHERE familyId = :familyId")
    suspend fun deleteByFamilyId(familyId: String): Int

    @Query("DELETE FROM kb_family_members")
    suspend fun deleteAll()
}
