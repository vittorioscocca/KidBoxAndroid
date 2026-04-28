package it.vittorioscocca.kidbox.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyPhotoDao
import it.vittorioscocca.kidbox.data.local.dao.KBPhotoAlbumDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.util.fixBitmapOrientationFromBytes
import it.vittorioscocca.kidbox.util.fixVideoFrameOrientation
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import it.vittorioscocca.kidbox.data.remote.PhotoAlbumRemoteChange
import it.vittorioscocca.kidbox.data.remote.PhotoRemoteChange
import it.vittorioscocca.kidbox.data.remote.PhotoVideoRemoteStore
import it.vittorioscocca.kidbox.data.remote.PhotoVideoStorageManager
import it.vittorioscocca.kidbox.data.remote.RemoteFamilyPhotoDto
import it.vittorioscocca.kidbox.data.remote.RemotePhotoAlbumDto
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
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

private const val PHOTO_INTEGRITY_TAG = "KidBoxPhotoIntegrity"

@Singleton
class PhotoVideoRepository @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val photoDao: KBFamilyPhotoDao,
    private val albumDao: KBPhotoAlbumDao,
    private val remoteStore: PhotoVideoRemoteStore,
    private val storageManager: PhotoVideoStorageManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private val inboundMutex = Mutex()
    private var photosListener: ListenerRegistration? = null
    private var albumsListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observePhotos(familyId: String): Flow<List<KBFamilyPhotoEntity>> =
        photoDao.observeByFamilyId(familyId)

    fun observeAlbums(familyId: String): Flow<List<KBPhotoAlbumEntity>> =
        albumDao.observeByFamilyId(familyId)
            .map { it.sortedWith(compareBy<KBPhotoAlbumEntity> { album -> album.sortOrder }.thenBy { album -> album.title.lowercase(Locale.ROOT) }) }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && photosListener != null && albumsListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                photosListener = remoteStore.listenPhotos(
                    familyId = familyId,
                    onChange = { change -> scope.launch(Dispatchers.IO) { applyInboundPhotoChange(familyId, change) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
                albumsListener = remoteStore.listenPhotoAlbums(
                    familyId = familyId,
                    onChange = { change -> scope.launch(Dispatchers.IO) { applyInboundAlbumChange(familyId, change) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock { stopRealtimeLocked() }
        }
    }

    suspend fun createAlbum(
        familyId: String,
        title: String,
    ): KBPhotoAlbumEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val nextSort = (albumDao.getAllByFamilyId(familyId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val album = KBPhotoAlbumEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            title = title.trim(),
            coverPhotoId = null,
            sortOrder = nextSort,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            createdBy = uid,
            updatedBy = uid,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        albumDao.upsert(album)
        return album
    }

    suspend fun importMediaFromUri(
        familyId: String,
        uri: Uri,
        albumId: String? = null,
        caption: String? = null,
    ): KBFamilyPhotoEntity {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Impossibile leggere il file selezionato")
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val name = resolveFileName(resolver, uri) ?: fallbackFileName(mimeType, System.currentTimeMillis())
        return importMediaFromBytes(
            familyId = familyId,
            bytes = bytes,
            mimeType = mimeType,
            fileName = name,
            albumId = albumId,
            caption = caption,
        )
    }

    suspend fun importMediaFromBytes(
        familyId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        albumId: String? = null,
        caption: String? = null,
    ): KBFamilyPhotoEntity {
        ensureFamilyExists(familyId = familyId, updatedAtEpochMillis = System.currentTimeMillis(), updatedBy = auth.currentUser?.uid)
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val id = UUID.randomUUID().toString()
        val name = fileName.ifBlank { fallbackFileName(mimeType, now) }
        val localFile = saveLocalMediaCopy(id, bytes, mimeType.ifBlank { "application/octet-stream" })
        val thumbnailBase64 = buildThumbnailBase64FromBytes(bytes, mimeType)
        val videoDuration = extractVideoDurationSecondsFromBytes(bytes, mimeType)
        val initialAlbumIds = if (albumId.isNullOrBlank()) "" else albumId.trim()
        val entity = KBFamilyPhotoEntity(
            id = id,
            familyId = familyId,
            fileName = name,
            mimeType = mimeType,
            fileSize = bytes.size.toLong(),
            storagePath = "families/$familyId/photos/$id/original.enc",
            downloadURL = null,
            localPath = localFile.absolutePath,
            thumbnailBase64 = thumbnailBase64,
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
            videoDurationSeconds = videoDuration,
            takenAtEpochMillis = now,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            createdBy = uid,
            updatedBy = uid,
            albumIdsRaw = initialAlbumIds,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        photoDao.upsert(entity)
        if (!albumId.isNullOrBlank()) {
            setAlbumCoverIfNeeded(albumId, entity)
        }
        return entity
    }

    suspend fun addPhotoToAlbum(
        photoId: String,
        albumId: String,
    ) {
        val photo = photoDao.getById(photoId) ?: return
        val updatedAlbumIds = parseAlbumIds(photo.albumIdsRaw).toMutableSet().apply { add(albumId) }
        val now = System.currentTimeMillis()
        photoDao.upsert(
            photo.copy(
                albumIdsRaw = updatedAlbumIds.joinToString(","),
                updatedAtEpochMillis = now,
                updatedBy = auth.currentUser?.uid ?: photo.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
        setAlbumCoverIfNeeded(albumId, photo)
    }

    suspend fun removePhotoFromAlbum(
        photoId: String,
        albumId: String,
    ) {
        val photo = photoDao.getById(photoId) ?: return
        val updatedAlbumIds = parseAlbumIds(photo.albumIdsRaw).toMutableSet().apply { remove(albumId) }
        val now = System.currentTimeMillis()
        photoDao.upsert(
            photo.copy(
                albumIdsRaw = updatedAlbumIds.joinToString(","),
                updatedAtEpochMillis = now,
                updatedBy = auth.currentUser?.uid ?: photo.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
        val album = albumDao.getById(albumId) ?: return
        if (album.coverPhotoId == photoId) {
            albumDao.upsert(
                album.copy(
                    coverPhotoId = null,
                    updatedAtEpochMillis = now,
                    updatedBy = auth.currentUser?.uid ?: album.updatedBy,
                    syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                    lastSyncError = null,
                ),
            )
        }
    }

    suspend fun setAlbumCover(
        albumId: String,
        photoId: String,
    ) {
        val album = albumDao.getById(albumId) ?: return
        val photo = photoDao.getById(photoId) ?: return
        if (photo.familyId != album.familyId || photo.isDeleted) return
        val now = System.currentTimeMillis()
        albumDao.upsert(
            album.copy(
                coverPhotoId = photoId,
                updatedAtEpochMillis = now,
                updatedBy = auth.currentUser?.uid ?: album.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun reorderAlbums(
        familyId: String,
        orderedAlbumIds: List<String>,
    ) {
        if (orderedAlbumIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val byId = albumDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted }
            .associateBy { it.id }
        orderedAlbumIds.forEachIndexed { index, albumId ->
            val album = byId[albumId] ?: return@forEachIndexed
            if (album.sortOrder == index) return@forEachIndexed
            albumDao.upsert(
                album.copy(
                    sortOrder = index,
                    updatedAtEpochMillis = now,
                    updatedBy = uid,
                    syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                    lastSyncError = null,
                ),
            )
        }
    }

    suspend fun deletePhoto(photoId: String) {
        val photo = photoDao.getById(photoId) ?: return
        photoDao.upsert(
            photo.copy(
                isDeleted = true,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: photo.updatedBy,
                syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun preparePreviewFile(photo: KBFamilyPhotoEntity): File {
        val local = photo.localPath?.let(::File)
        if (local != null && local.exists()) return local
        val bytes = storageManager.downloadDecrypted(photo.storagePath, photo.familyId)
        val outDir = File(context.cacheDir, "KBPhotos").apply { mkdirs() }
        val ext = extensionFromMime(photo.mimeType)
        val out = File(outDir, "${photo.id}.$ext")
        FileOutputStream(out).use { it.write(bytes) }
        photoDao.upsert(photo.copy(localPath = out.absolutePath))
        return out
    }

    suspend fun flushPending(familyId: String) {
        flushPendingInternal(familyId, null)
    }

    suspend fun flushPendingWithProgress(
        familyId: String,
        onPhotoUploaded: (photoId: String, done: Int, total: Int) -> Unit,
    ) {
        flushPendingInternal(familyId, onPhotoUploaded)
    }

    private suspend fun flushPendingInternal(
        familyId: String,
        onPhotoUploaded: ((photoId: String, done: Int, total: Int) -> Unit)?,
    ) {
        albumDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue).forEach { album ->
            runCatching { remoteStore.upsertAlbum(album) }
                .onSuccess {
                    albumDao.upsert(album.copy(syncStateRaw = KBSyncState.SYNCED.rawValue, lastSyncError = null))
                }
                .onFailure { err -> albumDao.upsert(album.copy(lastSyncError = err.localizedMessage)) }
        }

        val pendingPhotos = photoDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
        var uploadedDone = 0
        val uploadedTotal = pendingPhotos.size
        pendingPhotos.forEach { photo ->
            val uploaded = ensureUploaded(photo)
            runCatching { remoteStore.upsertPhoto(uploaded) }
                .onSuccess {
                    photoDao.upsert(
                        uploaded.copy(
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                    uploadedDone += 1
                    onPhotoUploaded?.invoke(uploaded.id, uploadedDone, uploadedTotal)
                }
                .onFailure { err -> photoDao.upsert(uploaded.copy(lastSyncError = err.localizedMessage)) }
        }

        albumDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue).forEach { album ->
            runCatching {
                remoteStore.softDeleteAlbum(
                    familyId = familyId,
                    albumId = album.id,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    updatedBy = auth.currentUser?.uid ?: album.updatedBy,
                )
                albumDao.deleteById(album.id)
            }.onFailure { err -> albumDao.upsert(album.copy(lastSyncError = err.localizedMessage)) }
        }

        photoDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue).forEach { photo ->
            runCatching {
                remoteStore.softDeletePhoto(
                    familyId = familyId,
                    photoId = photo.id,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    updatedBy = auth.currentUser?.uid ?: photo.updatedBy,
                )
                storageManager.delete(photo.storagePath)
                photoDao.deleteById(photo.id)
            }.onFailure { err -> photoDao.upsert(photo.copy(lastSyncError = err.localizedMessage)) }
        }
    }

    private suspend fun ensureUploaded(photo: KBFamilyPhotoEntity): KBFamilyPhotoEntity {
        if (!photo.downloadURL.isNullOrBlank()) return photo
        val localFile = photo.localPath?.let(::File)
        if (localFile == null || !localFile.exists()) return photo
        val upload = storageManager.uploadEncrypted(
            familyId = photo.familyId,
            photoId = photo.id,
            mimeType = photo.mimeType,
            plainBytes = localFile.readBytes(),
        )
        return photo.copy(
            storagePath = upload.storagePath,
            downloadURL = upload.downloadUrl,
        )
    }

    private suspend fun applyInboundPhotoChange(
        familyId: String,
        change: PhotoRemoteChange,
    ) {
        inboundMutex.withLock {
            when (change) {
                is PhotoRemoteChange.Remove -> {
                    if (change.isFromCache) return
                    photoDao.deleteById(change.id)
                }

                is PhotoRemoteChange.Upsert -> {
                    applyInboundPhotoDto(familyId, change.dto)
                }
            }
        }
    }

    private suspend fun applyInboundAlbumChange(
        familyId: String,
        change: PhotoAlbumRemoteChange,
    ) {
        inboundMutex.withLock {
            when (change) {
                is PhotoAlbumRemoteChange.Remove -> {
                    if (change.isFromCache) return
                    albumDao.deleteById(change.id)
                }

                is PhotoAlbumRemoteChange.Upsert -> {
                    applyInboundAlbumDto(familyId, change.dto)
                }
            }
        }
    }

    private suspend fun applyInboundPhotoDto(
        familyId: String,
        dto: RemoteFamilyPhotoDto,
    ) {
        ensureFamilyExists(
            familyId = familyId,
            updatedAtEpochMillis = dto.updatedAtEpochMillis,
            updatedBy = dto.updatedBy,
        )
        val local = photoDao.getById(dto.id)
        val localSync = local?.let { KBSyncState.fromRaw(it.syncStateRaw) }
        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) return
        val remoteUpdatedAt = dto.updatedAtEpochMillis ?: 0L
        if (dto.isDeleted) {
            if (local != null && remoteUpdatedAt >= local.updatedAtEpochMillis) photoDao.deleteById(dto.id)
            return
        }
        if (local != null && remoteUpdatedAt < local.updatedAtEpochMillis) return
        val now = System.currentTimeMillis()
        photoDao.upsert(
            KBFamilyPhotoEntity(
                id = dto.id,
                familyId = familyId,
                fileName = dto.fileName,
                mimeType = dto.mimeType,
                fileSize = dto.fileSize,
                storagePath = dto.storagePath,
                downloadURL = dto.downloadURL,
                localPath = local?.localPath,
                thumbnailBase64 = dto.thumbnailBase64 ?: local?.thumbnailBase64,
                caption = dto.caption,
                videoDurationSeconds = dto.videoDurationSeconds ?: local?.videoDurationSeconds,
                takenAtEpochMillis = if (dto.takenAtEpochMillis > 0L) dto.takenAtEpochMillis else (local?.takenAtEpochMillis ?: now),
                createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now,
                createdBy = local?.createdBy ?: dto.createdBy.orEmpty(),
                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                albumIdsRaw = dto.albumIdsRaw,
                isDeleted = false,
                syncStateRaw = KBSyncState.SYNCED.rawValue,
                lastSyncError = null,
            ),
        )
    }

    private suspend fun applyInboundAlbumDto(
        familyId: String,
        dto: RemotePhotoAlbumDto,
    ) {
        ensureFamilyExists(
            familyId = familyId,
            updatedAtEpochMillis = dto.updatedAtEpochMillis,
            updatedBy = dto.updatedBy,
        )
        val local = albumDao.getById(dto.id)
        val localSync = local?.let { KBSyncState.fromRaw(it.syncStateRaw) }
        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) return
        val remoteUpdatedAt = dto.updatedAtEpochMillis ?: 0L
        if (dto.isDeleted) {
            if (local != null && remoteUpdatedAt >= local.updatedAtEpochMillis) albumDao.deleteById(dto.id)
            return
        }
        if (local != null && remoteUpdatedAt < local.updatedAtEpochMillis) return
        val now = System.currentTimeMillis()
        // The album can arrive from cache before its cover photo snapshot; avoid FK crash.
        val resolvedCoverPhotoId = dto.coverPhotoId
            ?.takeIf { coverId -> photoDao.getById(coverId) != null }
        if (!dto.coverPhotoId.isNullOrBlank() && resolvedCoverPhotoId == null) {
            Log.w(
                PHOTO_INTEGRITY_TAG,
                "Album ${dto.id}: coverPhotoId=${dto.coverPhotoId} not found locally, fallback to null",
            )
        }
        albumDao.upsert(
            KBPhotoAlbumEntity(
                id = dto.id,
                familyId = familyId,
                title = dto.title.ifBlank { local?.title.orEmpty() },
                coverPhotoId = resolvedCoverPhotoId,
                sortOrder = dto.sortOrder,
                createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now,
                createdBy = local?.createdBy ?: dto.createdBy.orEmpty(),
                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                isDeleted = false,
                syncStateRaw = KBSyncState.SYNCED.rawValue,
                lastSyncError = null,
            ),
        )
    }

    private suspend fun ensureFamilyExists(
        familyId: String,
        updatedAtEpochMillis: Long?,
        updatedBy: String?,
    ) {
        if (familyId.isBlank()) return
        if (familyDao.getById(familyId) != null) return
        val now = System.currentTimeMillis()
        val actor = updatedBy?.takeIf { it.isNotBlank() } ?: auth.currentUser?.uid ?: "remote"
        Log.w(
            PHOTO_INTEGRITY_TAG,
            "Missing family row for familyId=$familyId, creating placeholder before photo/album upsert",
        )
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = "",
                heroPhotoURL = null,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = actor,
                updatedBy = actor,
                createdAtEpochMillis = updatedAtEpochMillis ?: now,
                updatedAtEpochMillis = updatedAtEpochMillis ?: now,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            ),
        )
    }

    private suspend fun setAlbumCoverIfNeeded(
        albumId: String,
        photo: KBFamilyPhotoEntity,
    ) {
        val album = albumDao.getById(albumId) ?: return
        if (!album.coverPhotoId.isNullOrBlank()) return
        albumDao.upsert(
            album.copy(
                coverPhotoId = photo.id,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: album.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    private fun parseAlbumIds(raw: String): Set<String> =
        raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun saveLocalMediaCopy(
        photoId: String,
        bytes: ByteArray,
        mimeType: String,
    ): File {
        val ext = extensionFromMime(mimeType)
        val outDir = File(context.cacheDir, "KBPhotos").apply { mkdirs() }
        val out = File(outDir, "$photoId.$ext")
        FileOutputStream(out).use { it.write(bytes) }
        return out
    }

    private fun resolveFileName(
        resolver: ContentResolver,
        uri: Uri,
    ): String? {
        val cursor = resolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return it.getString(idx)
        }
        return null
    }

    private fun buildThumbnailBase64(
        uri: Uri,
        bytes: ByteArray,
        mimeType: String,
    ): String? {
        val thumbBitmap = runCatching {
            if (mimeType.startsWith("video/")) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { fixVideoFrameOrientation(it, retriever) }
                retriever.release()
                frame
            } else {
                decodeSampledBitmap(bytes, 320, 320)
                    .let { fixBitmapOrientationFromBytes(it, bytes) }
            }
        }.getOrNull() ?: return null
        return bitmapToBase64(thumbBitmap)
    }

    private fun buildThumbnailBase64FromBytes(
        bytes: ByteArray,
        mimeType: String,
    ): String? {
        val thumbBitmap = runCatching {
            if (mimeType.startsWith("video/")) null
            else decodeSampledBitmap(bytes, 320, 320)
                .let { fixBitmapOrientationFromBytes(it, bytes) }
        }.getOrNull() ?: return null
        return bitmapToBase64(thumbBitmap)
    }

    private fun extractVideoDurationSeconds(
        uri: Uri,
        mimeType: String,
    ): Double? {
        if (!mimeType.startsWith("video/")) return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            durationMs?.toDouble()?.div(1000.0)
        }.getOrNull()
    }

    private fun extractVideoDurationSecondsFromBytes(
        bytes: ByteArray,
        mimeType: String,
    ): Double? {
        if (!mimeType.startsWith("video/")) return null
        return runCatching {
            val tmpFile = File.createTempFile("kb_video_meta_", ".tmp", context.cacheDir).apply {
                writeBytes(bytes)
            }
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tmpFile.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            tmpFile.delete()
            durationMs?.toDouble()?.div(1000.0)
        }.getOrNull()
    }

    private fun decodeSampledBitmap(
        bytes: ByteArray,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Invalid bitmap payload")
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun extensionFromMime(mimeType: String): String = when {
        mimeType == "image/png" -> "png"
        mimeType == "image/webp" -> "webp"
        mimeType.startsWith("image/") -> "jpg"
        mimeType == "video/mp4" -> "mp4"
        mimeType == "video/quicktime" -> "mov"
        mimeType.startsWith("video/") -> "mp4"
        else -> "bin"
    }

    private fun fallbackFileName(
        mimeType: String,
        now: Long,
    ): String {
        val prefix = if (mimeType.startsWith("video/")) "video" else "photo"
        val ext = extensionFromMime(mimeType)
        return "${prefix}_$now.$ext"
    }

    private fun stopRealtimeLocked() {
        photosListener?.remove()
        albumsListener?.remove()
        photosListener = null
        albumsListener = null
        listeningFamilyId = null
    }
}
