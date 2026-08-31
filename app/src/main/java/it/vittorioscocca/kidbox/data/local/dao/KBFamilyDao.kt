package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import kotlinx.coroutines.flow.Flow

/**
 * NOTA: DAO implementato come `abstract class` (non interface) per poter definire
 * il metodo `upsert` con `@Transaction` in modo sicuro rispetto alle Foreign Key.
 *
 * Il vecchio `@Insert(onConflict = REPLACE)` eseguiva in SQLite un `DELETE + INSERT`
 * quando la primary key conflittava, triggherando il `ON DELETE CASCADE` definito su
 * `kb_family_members.familyId`. Questo cancellava TUTTI i membri della famiglia ogni
 * volta che il record famiglia veniva aggiornato (es. da `familyListener` di
 * `FamilySyncCenter`), e il `membersListener` non rifuoriva perché Firestore non era
 * cambiato → i membri (incluso l'owner) sparivano in modo permanente.
 *
 * Il nuovo `upsert` usa `UPDATE` per aggiornare la riga esistente (nessuna cascata)
 * e `INSERT OR IGNORE` solo quando la riga non esiste ancora.
 */
@Dao
abstract class KBFamilyDao {

    @Query("SELECT * FROM kb_families WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): KBFamilyEntity?

    @Query("SELECT * FROM kb_families ORDER BY updatedAtEpochMillis DESC")
    abstract fun observeAll(): Flow<List<KBFamilyEntity>>

    @Query("SELECT * FROM kb_families WHERE id = :familyId LIMIT 1")
    abstract fun observeById(familyId: String): Flow<KBFamilyEntity?>

    @Query("SELECT * FROM kb_families ORDER BY updatedAtEpochMillis DESC")
    abstract suspend fun getAll(): List<KBFamilyEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM kb_families LIMIT 1)")
    abstract suspend fun hasAnyFamily(): Boolean

    // ── Internal primitives (non chiamare direttamente dall'esterno) ────────────

    @Update
    protected abstract suspend fun updateInternal(entity: KBFamilyEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnore(entity: KBFamilyEntity): Long

    // ── Upsert sicuro: UPDATE se esiste, INSERT se nuovo ───────────────────────

    /**
     * Aggiorna la riga se esiste già (via @Update, che genera un SQL UPDATE),
     * altrimenti la inserisce (via INSERT OR IGNORE).
     *
     * Mai DELETE + INSERT → nessuna cascata su kb_family_members.
     */
    @Transaction
    open suspend fun upsert(entity: KBFamilyEntity) {
        if (updateInternal(entity) == 0) {
            insertIgnore(entity)
        }
    }

    @Transaction
    open suspend fun upsertAll(entities: List<KBFamilyEntity>) {
        entities.forEach { upsert(it) }
    }

    // ── Delete ──────────────────────────────────────────────────────────────────

    @Delete
    abstract suspend fun delete(entity: KBFamilyEntity)

    @Query("DELETE FROM kb_families WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM kb_families WHERE id = :familyId")
    abstract suspend fun deleteByFamilyId(familyId: String): Int

    @Query("DELETE FROM kb_families")
    abstract suspend fun deleteAll()

    @Query("SELECT id FROM kb_families LIMIT 1")
    abstract suspend fun peekAnyFamilyId(): String?
}
