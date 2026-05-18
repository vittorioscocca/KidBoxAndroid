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
            noteDao.getById(existing)?.let { return existing }
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

    suspend fun ensureTodoList(trip: KBTripEntity, childId: String): String? {
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
