package it.vittorioscocca.kidbox.data.travel

import it.vittorioscocca.kidbox.data.local.dao.KBFamilyPhotoDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBPhotoAlbumDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.util.UUID

object TravelTripAlbumTitles {
    fun forTrip(tripName: String): String = "Viaggio · $tripName"
}

@Singleton
class TravelTripExtrasRepository @Inject constructor(
    private val tripDao: KBTripDao,
    private val photoAlbumDao: KBPhotoAlbumDao,
    private val familyPhotoDao: KBFamilyPhotoDao,
    private val noteDao: KBNoteDao,
    private val todoListDao: KBTodoListDao,
    private val todoItemDao: KBTodoItemDao,
) {
    private val noteBodyTemplate = """
        Annotazioni di viaggio

        • Idee e promemoria
        • Indirizzi e contatti utili
        • Spese da ricordare

    """.trimIndent()

    suspend fun ensureAlbum(trip: KBTripEntity, userId: String): String? {
        if (userId.isBlank()) return null
        val currentTrip = tripDao.observeById(trip.id).first() ?: trip
        val expectedTitle = TravelTripAlbumTitles.forTrip(currentTrip.name)
        val existing = currentTrip.photoAlbumId?.takeIf { it.isNotBlank() }
        if (existing != null) {
            val album = photoAlbumDao.getById(existing)
            if (album != null && !album.isDeleted) {
                if (album.title != expectedTitle) {
                    val now = System.currentTimeMillis()
                    photoAlbumDao.upsert(
                        album.copy(
                            title = expectedTitle,
                            updatedAtEpochMillis = now,
                            updatedBy = userId,
                            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }
                return existing
            }
        }

        // Album già esistente dello stesso viaggio, rimasto scollegato: si
        // riaggancia invece di crearne un altro. Stessa fragilità di nota e
        // lista todo — l'unico legame è `trip.photoAlbumId`, e se si perde si
        // accumulano duplicati.
        val orphanAlbum = photoAlbumDao.getAllByFamilyId(currentTrip.familyId)
            .firstOrNull { !it.isDeleted && it.title.trim() == expectedTitle.trim() }
        if (orphanAlbum != null) {
            tripDao.upsert(currentTrip.copy(photoAlbumId = orphanAlbum.id, updatedAtEpoch = System.currentTimeMillis()))
            return orphanAlbum.id
        }

        val sortOrder = photoAlbumDao.getAllByFamilyId(currentTrip.familyId).size
        val album = KBPhotoAlbumEntity(
            id = UUID.randomUUID().toString(),
            familyId = currentTrip.familyId,
            title = expectedTitle,
            coverPhotoId = null,
            sortOrder = sortOrder,
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
            createdBy = userId,
            updatedBy = userId,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        photoAlbumDao.upsert(album)
        tripDao.upsert(
            currentTrip.copy(
                photoAlbumId = album.id,
                updatedAtEpoch = System.currentTimeMillis(),
            ),
        )
        return album.id
    }

    suspend fun photoCount(albumId: String, familyId: String): Int {
        if (albumId.isBlank()) return 0
        return familyPhotoDao.observeByFamilyId(familyId).first()
            .count { !it.isDeleted && it.albumIdsRaw.containsAlbum(albumId) }
    }

    suspend fun ensureNote(
        trip: KBTripEntity,
        userId: String,
        userDisplayName: String,
    ): String? {
        if (userId.isBlank()) return null
        val existing = trip.notesNoteId?.takeIf { it.isNotBlank() }
        if (existing != null) {
            noteDao.getById(existing)?.takeIf { !it.isDeleted }?.let { return existing }
        }

        // Nota già esistente di questo viaggio, rimasta scollegata: si
        // riaggancia per titolo invece di crearne un'altra.
        val orphanNote = noteDao.observeByFamilyId(trip.familyId).first()
            .firstOrNull { !it.isDeleted && it.title.trim() == trip.name.trim() }
        if (orphanNote != null) {
            tripDao.upsert(trip.copy(notesNoteId = orphanNote.id, updatedAtEpoch = System.currentTimeMillis()))
            return orphanNote.id
        }

        val now = System.currentTimeMillis()
        val note = KBNoteEntity(
            id = UUID.randomUUID().toString(),
            familyId = trip.familyId,
            title = trip.name,
            body = noteBodyTemplate,
            visibilityScope = "family",
            visibilityMemberIdsJson = "[]",
            createdBy = userId,
            createdByName = userDisplayName,
            updatedBy = userId,
            updatedByName = userDisplayName,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        noteDao.upsert(note)
        tripDao.upsert(
            trip.copy(
                notesNoteId = note.id,
                updatedAtEpoch = now,
            ),
        )
        return note.id
    }

    suspend fun noteHasUserContent(noteId: String): Boolean {
        val note = noteDao.getById(noteId) ?: return false
        val body = note.body.trim()
        val template = noteBodyTemplate.trim()
        return body.isNotEmpty() && body != template
    }

    /**
     * @param createIfMissing `false` quando la chiamata non nasce da un gesto
     *   dell'utente (per esempio l'apertura della scheda viaggio). Senza
     *   questo freno, una lista cancellata dalla sezione Todo tornava da sola
     *   al primo sguardo al viaggio: `getById` non la trova più (cancellata
     *   con hard delete), si cade nel ramo "crea nuova lista" e la voce
     *   riappare con un id diverso — sembrando che Elimina non facesse nulla.
     */
    suspend fun ensureTodoList(trip: KBTripEntity, childId: String, createIfMissing: Boolean = true): String? {
        if (childId.isBlank()) return null
        val existing = trip.todoListId?.takeIf { it.isNotBlank() }
        if (existing != null) {
            todoListDao.getById(existing)?.let { list ->
                if (list.name != trip.name) {
                    todoListDao.upsert(
                        list.copy(
                            name = trip.name,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                return existing
            }
        }

        // Lista già esistente di questo viaggio, rimasta scollegata: si
        // riaggancia invece di crearne un'altra.
        val orphanList = todoListDao.getByFamilyAndChild(trip.familyId, childId)
            .firstOrNull { it.name.trim() == trip.name.trim() }
        if (orphanList != null) {
            tripDao.upsert(trip.copy(todoListId = orphanList.id, updatedAtEpoch = System.currentTimeMillis()))
            return orphanList.id
        }

        if (!createIfMissing) return null

        val now = System.currentTimeMillis()
        val list = KBTodoListEntity(
            id = UUID.randomUUID().toString(),
            familyId = trip.familyId,
            childId = childId,
            name = trip.name,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            isDeleted = false,
        )
        todoListDao.upsert(list)
        tripDao.upsert(trip.copy(todoListId = list.id, updatedAtEpoch = now))
        return list.id
    }

    suspend fun openTodoCount(listId: String, familyId: String, childId: String): Int {
        if (listId.isBlank()) return 0
        return todoItemDao.getByFamilyAndChild(familyId, childId)
            .count { !it.isDeleted && !it.isDone && it.listId == listId }
    }

    fun travelExpenseCategoryId(familyId: String): String = "expcat-$familyId-viaggi"

    private fun String.containsAlbum(albumId: String): Boolean {
        if (isBlank()) return false
        return split(",").map { it.trim() }.any { it == albumId }
    }
}
