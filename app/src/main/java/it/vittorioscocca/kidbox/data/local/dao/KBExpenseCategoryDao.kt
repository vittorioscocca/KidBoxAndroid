package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBExpenseCategoryDao {
    @Query("SELECT * FROM kb_expense_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBExpenseCategoryEntity?

    @Query("SELECT * FROM kb_expense_categories WHERE familyId = :familyId AND isDeleted = 0 ORDER BY sortIndex, name")
    fun observeByFamilyId(familyId: String): Flow<List<KBExpenseCategoryEntity>>

    @Query("SELECT * FROM kb_expense_categories WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBExpenseCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBExpenseCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBExpenseCategoryEntity>)

    @Query("DELETE FROM kb_expense_categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
