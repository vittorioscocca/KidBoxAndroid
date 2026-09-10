package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KBTreatmentDao {
    @Query("SELECT * FROM kb_treatments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KBTreatmentEntity?

    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY startDateEpochMillis DESC")
    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBTreatmentEntity>>

    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND petId = :petId AND isDeleted = 0 ORDER BY startDateEpochMillis DESC")
    fun observeByFamilyAndPet(familyId: String, petId: String): Flow<List<KBTreatmentEntity>>

    /** Tutte le terapie della famiglia: serve alla Dashboard, che non filtra per bambino. */
    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND isDeleted = 0 ORDER BY startDateEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBTreatmentEntity>>

    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND childId = :childId AND isDeleted = 0 ORDER BY startDateEpochMillis DESC")
    suspend fun listByFamilyAndChild(familyId: String, childId: String): List<KBTreatmentEntity>

    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND petId = :petId AND isDeleted = 0 ORDER BY startDateEpochMillis DESC")
    suspend fun listByFamilyAndPet(familyId: String, petId: String): List<KBTreatmentEntity>

    @Query("SELECT * FROM kb_treatments WHERE familyId = :familyId AND isDeleted = 0 AND isActive = 1 AND reminderEnabled = 1")
    suspend fun listActiveWithReminders(familyId: String): List<KBTreatmentEntity>

    @Query("SELECT COUNT(*) FROM kb_treatments WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun countByFamilyId(familyId: String): Int

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
    suspend fun upsert(entity: KBTreatmentEntity)

    @Delete
    suspend fun delete(entity: KBTreatmentEntity)
}
