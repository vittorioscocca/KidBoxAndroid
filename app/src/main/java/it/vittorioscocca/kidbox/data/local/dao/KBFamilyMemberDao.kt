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
    /**
     * La riga di quel membro **in quella famiglia**. È il lookup da usare
     * ovunque conti a quale famiglia appartiene la riga: confronti last-write-wins
     * della sync, controlli di ruolo, aggiornamenti del proprio profilo.
     */
    @Query("SELECT * FROM kb_family_members WHERE familyId = :familyId AND id = :id LIMIT 1")
    suspend fun getByFamilyAndId(familyId: String, id: String): KBFamilyMemberEntity?

    /**
     * Una riga qualsiasi con quell'id, senza guardare la famiglia.
     *
     * Serve SOLO a risolvere il nome da mostrare a partire da un uid: la persona
     * è la stessa in tutte le famiglie, quindi va bene la prima che si trova.
     * Per qualunque altra cosa usare [getByFamilyAndId] — con la chiave composita
     * lo stesso id può esistere in più famiglie, e qui non si sa quale esce.
     */
    @Query("SELECT * FROM kb_family_members WHERE id = :id LIMIT 1")
    suspend fun getAnyById(id: String): KBFamilyMemberEntity?

    @Query(
        "SELECT * FROM kb_family_members WHERE familyId = :familyId AND userId = :userId AND isDeleted = 0 LIMIT 1",
    )
    suspend fun getActiveByFamilyAndUser(familyId: String, userId: String): KBFamilyMemberEntity?

    @Query("SELECT * FROM kb_family_members WHERE familyId = :familyId AND isDeleted = 0")
    fun observeActiveByFamilyId(familyId: String): Flow<List<KBFamilyMemberEntity>>

    /**
     * Lettura secca di tutte le righe della famiglia, cancellate incluse.
     * Serve alla riconciliazione con il server ([FamilySyncCenter.forceResync]):
     * per sapere quali righe locali il server non ha più bisogna vedere tutto,
     * non solo le attive.
     */
    @Query("SELECT * FROM kb_family_members WHERE familyId = :familyId")
    suspend fun getAllByFamilyId(familyId: String): List<KBFamilyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBFamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KBFamilyMemberEntity>)

    @Delete
    suspend fun delete(entity: KBFamilyMemberEntity)

    /**
     * Rimuove quel membro da QUELLA famiglia, lasciando intatte le sue righe
     * nelle altre famiglie a cui appartiene.
     */
    @Query("DELETE FROM kb_family_members WHERE familyId = :familyId AND id = :id")
    suspend fun deleteByFamilyAndId(familyId: String, id: String)

    @Query("DELETE FROM kb_family_members WHERE familyId = :familyId")
    suspend fun deleteByFamilyId(familyId: String): Int

    @Query("DELETE FROM kb_family_members")
    suspend fun deleteAll()
}
