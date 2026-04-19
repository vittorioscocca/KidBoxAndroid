package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.remote.notes.NoteRemoteChange
import it.vittorioscocca.kidbox.data.remote.notes.NoteRemoteStore
import it.vittorioscocca.kidbox.data.remote.notes.noteTitleForStorage
import it.vittorioscocca.kidbox.domain.model.KBNote
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: KBNoteDao,
    private val familyDao: KBFamilyDao,
    private val remoteStore: NoteRemoteStore,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()
    private val realtimeMutex = Mutex()
    private var noteListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeByFamilyId(
        familyId: String,
    ): Flow<List<KBNote>> = noteDao.observeByFamilyId(familyId).map { list -> list.map { it.toDomain() } }

    fun observeById(
        noteId: String,
    ): Flow<KBNote?> = noteDao.observeById(noteId).map { it?.toDomain() }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && noteListener != null) {
                    return@withLock
                }
                stopRealtimeLocked()
                listeningFamilyId = familyId
                noteListener = remoteStore.listen(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
                flushPending(familyId)
            }
        }
    }

    fun stopRealtime() {
        scope.launch { realtimeMutex.withLock { stopRealtimeLocked() } }
    }

    suspend fun upsertNote(
        familyId: String,
        noteId: String? = null,
        title: String,
        body: String,
    ): String {
        ensureFamilyExists(familyId)
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val id = noteId ?: java.util.UUID.randomUUID().toString()
        val current = noteDao.getById(id)
        val storedTitle = title.noteTitleForStorage()
        val storedBody = body.trimEnd()
        val target = KBNoteEntity(
            id = id,
            familyId = familyId,
            title = storedTitle,
            body = storedBody,
            createdBy = current?.createdBy ?: uid,
            createdByName = current?.createdByName.orEmpty(),
            updatedBy = uid,
            updatedByName = current?.updatedByName.orEmpty(),
            createdAtEpochMillis = current?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        noteDao.upsert(target)
        runCatching {
            remoteStore.upsert(target)
            noteDao.upsert(
                target.copy(
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
        }.onFailure { err ->
            noteDao.upsert(
                target.copy(
                    syncStateRaw = KBSyncState.ERROR.rawValue,
                    lastSyncError = err.message,
                ),
            )
        }
        return id
    }

    suspend fun softDelete(
        familyId: String,
        noteId: String,
    ) {
        val current = noteDao.getById(noteId) ?: return
        val now = System.currentTimeMillis()
        val pendingDelete = current.copy(
            isDeleted = true,
            updatedAtEpochMillis = now,
            syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
            lastSyncError = null,
        )
        noteDao.upsert(pendingDelete)
        runCatching {
            remoteStore.softDelete(familyId, noteId)
            noteDao.deleteById(noteId)
        }.onFailure { err ->
            noteDao.upsert(
                pendingDelete.copy(
                    isDeleted = false,
                    syncStateRaw = KBSyncState.ERROR.rawValue,
                    lastSyncError = err.message,
                ),
            )
        }
    }

    private suspend fun applyInbound(changes: List<NoteRemoteChange>) {
        inboundMutex.withLock {
            changes.forEach { change ->
                when (change) {
                    is NoteRemoteChange.Remove -> noteDao.deleteById(change.id)
                    is NoteRemoteChange.Upsert -> {
                        val dto = change.dto
                        if (dto.isDeleted) {
                            noteDao.deleteById(dto.id)
                            return@forEach
                        }
                        ensureFamilyExists(dto.familyId)
                        val local = noteDao.getById(dto.id)
                        if (
                            local != null &&
                            (local.isDeleted || KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE)
                        ) {
                            return@forEach
                        }
                        val remoteUpdated = dto.updatedAtEpochMillis ?: 0L
                        val localUpdated = local?.updatedAtEpochMillis ?: 0L
                        val localIsEmpty = local != null && local.title.isBlank() && local.body.isBlank()
                        if (!localIsEmpty && remoteUpdated < localUpdated) {
                            return@forEach
                        }
                        val (remoteTitle, remoteBody) = remoteStore.decryptOrFallback(dto)
                        val storedTitle = remoteTitle.noteTitleForStorage()
                        val storedBody = remoteBody.trimEnd()
                        val now = System.currentTimeMillis()
                        noteDao.upsert(
                            KBNoteEntity(
                                id = dto.id,
                                familyId = dto.familyId,
                                title = storedTitle,
                                body = storedBody,
                                createdBy = dto.createdBy ?: local?.createdBy.orEmpty(),
                                createdByName = dto.createdByName ?: local?.createdByName.orEmpty(),
                                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                                updatedByName = dto.updatedByName ?: local?.updatedByName.orEmpty(),
                                createdAtEpochMillis = dto.createdAtEpochMillis ?: local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                                isDeleted = false,
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun flushPending(familyId: String) {
        scope.launch {
            noteDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue).forEach { note ->
                runCatching {
                    remoteStore.upsert(note)
                    noteDao.upsert(note.copy(syncStateRaw = KBSyncState.SYNCED.rawValue, lastSyncError = null))
                }.onFailure { err ->
                    noteDao.upsert(note.copy(syncStateRaw = KBSyncState.ERROR.rawValue, lastSyncError = err.message))
                }
            }
            noteDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue).forEach { note ->
                runCatching {
                    remoteStore.softDelete(familyId = familyId, noteId = note.id)
                    noteDao.deleteById(note.id)
                }.onFailure { err ->
                    noteDao.upsert(note.copy(syncStateRaw = KBSyncState.ERROR.rawValue, lastSyncError = err.message))
                }
            }
        }
    }

    private suspend fun ensureFamilyExists(
        familyId: String,
    ) {
        if (familyId.isBlank()) return
        val existing = familyDao.getById(familyId)
        if (existing != null) return
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = "Famiglia",
                heroPhotoURL = null,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = uid,
                updatedBy = uid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            ),
        )
    }

    private fun stopRealtimeLocked() {
        noteListener?.remove()
        noteListener = null
        listeningFamilyId = null
    }

    private fun KBNoteEntity.toDomain(): KBNote = KBNote(
        id = id,
        familyId = familyId,
        title = title,
        body = body,
        createdBy = createdBy,
        createdByName = createdByName,
        updatedBy = updatedBy,
        updatedByName = updatedByName,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        isDeleted = isDeleted,
        syncStateRaw = syncStateRaw,
        lastSyncError = lastSyncError,
    )
}
