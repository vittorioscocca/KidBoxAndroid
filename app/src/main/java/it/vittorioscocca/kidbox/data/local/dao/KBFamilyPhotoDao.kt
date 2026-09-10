package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBFamilyPhotoDao {
    @Query("SELECT * FROM kb_family_photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBFamilyPhotoEntity?

    @Query("SELECT * FROM kb_family_photos WHERE familyId = :familyId AND isDeleted = 0 ORDER BY takenAtEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBFamilyPhotoEntity>>

    @Query("SELECT * FROM kb_family_photos WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBFamilyPhotoEntity>

    @Query("SELECT * FROM kb_family_photos WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(
        familyId: String,
        syncStateRaw: Int,
    ): List<KBFamilyPhotoEntity>

    // `@Upsert` e NON `@Insert(onConflict = REPLACE)`.
    //
    // REPLACE in SQLite è un DELETE + INSERT, e il DELETE fa scattare le azioni
    // delle foreign key che puntano a questa tabella: i figli con CASCADE
    // spariscono, quelli con SET NULL restano scollegati. Riscrivere una riga
    // identica a se stessa cancellava così i suoi figli — è il motivo per cui
    // dopo un force refresh una lista to-do appariva vuota, e la stessa cosa era
    // già stata corretta su KBFamilyDao per i membri.
    // `@Upsert` fa INSERT e, in conflitto, UPDATE: nessun DELETE, nessuna
    // cascata.
    @Upsert
    suspend fun upsert(entity: KBFamilyPhotoEntity)

    @Query("DELETE FROM kb_family_photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(entity: KBFamilyPhotoEntity)
}
