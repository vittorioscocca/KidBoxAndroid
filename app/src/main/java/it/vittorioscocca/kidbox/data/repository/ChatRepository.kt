package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.data.local.dao.KBChatMessageDao
import it.vittorioscocca.kidbox.data.local.entity.KBChatMessageEntity
import it.vittorioscocca.kidbox.data.remote.chat.ChatRemoteStore
import it.vittorioscocca.kidbox.data.remote.chat.ChatStorageService
import it.vittorioscocca.kidbox.domain.model.KBChatMessage
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.io.File
import android.net.Uri
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: KBChatMessageDao,
    private val remoteStore: ChatRemoteStore,
    private val storageService: ChatStorageService,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null
    private val lastLocalMediaCleanupAtMs = mutableMapOf<String, Long>()

    fun observeMessages(familyId: String): Flow<List<KBChatMessage>> =
        chatDao.observeByFamilyId(familyId).map { list -> list.map { it.toDomain() } }

    fun scheduleLocalMediaCacheCleanup(
        familyId: String,
        minIntervalMs: Long = 45_000L,
    ) {
        if (familyId.isBlank()) return
        val now = System.currentTimeMillis()
        val last = lastLocalMediaCleanupAtMs[familyId] ?: 0L
        if (now - last < minIntervalMs) return
        lastLocalMediaCleanupAtMs[familyId] = now
        scope.launch {
            val keepPaths = chatDao.getMediaLocalPathsByFamilyId(familyId)
                .filter { it.isNotBlank() }
                .toSet()
            storageService.cleanupCachedMedia(
                familyId = familyId,
                keepAbsolutePaths = keepPaths,
            )
        }
    }

    fun startRealtime(
        familyId: String,
        limit: Int = 50,
        onOldestDocument: ((DocumentSnapshot) -> Unit)? = null,
        onError: (Exception) -> Unit = {},
    ) {
        if (listener != null && listeningFamilyId == familyId) return
        stopRealtime()
        listeningFamilyId = familyId
        listener = remoteStore.listenMessages(
            familyId = familyId,
            limit = limit,
            onOldestDocument = onOldestDocument,
            onError = onError,
        )
        flushPending(familyId)
    }

    fun stopRealtime() {
        listener?.remove()
        listener = null
        listeningFamilyId = null
    }

    suspend fun sendMessage(
        familyId: String,
        type: ChatMessageType,
        text: String? = null,
        mediaBytes: ByteArray? = null,
        fileName: String? = null,
        mimeType: String? = null,
        mediaDurationSeconds: Int? = null,
        mediaThumbnailURL: String? = null,
        mediaGroupURLsJSON: String? = null,
        mediaGroupTypesJSON: String? = null,
        contactPayloadJSON: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        replyToId: String? = null,
    ): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val senderName = auth.currentUser?.displayName.orEmpty()
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        var local = KBChatMessageEntity(
            id = messageId,
            familyId = familyId,
            senderId = uid,
            senderName = senderName,
            typeRaw = type.rawValue,
            text = text,
            latitude = latitude,
            longitude = longitude,
            mediaStoragePath = null,
            mediaURL = null,
            mediaDurationSeconds = mediaDurationSeconds,
            mediaThumbnailURL = mediaThumbnailURL,
            replyToId = replyToId,
            mediaLocalPath = null,
            mediaFileSize = null,
            mediaGroupURLsJSON = mediaGroupURLsJSON,
            mediaGroupTypesJSON = mediaGroupTypesJSON,
            contactPayloadJSON = contactPayloadJSON,
            reactionsJSON = null,
            readByJSON = null,
            deletedForJSON = null,
            transcriptText = null,
            transcriptStatusRaw = "none",
            transcriptSourceRaw = null,
            transcriptLocaleIdentifier = null,
            transcriptIsFinal = false,
            transcriptUpdatedAtEpochMillis = null,
            transcriptErrorMessage = null,
            createdAtEpochMillis = now,
            editedAtEpochMillis = null,
            isDeleted = false,
            isDeletedForEveryone = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        chatDao.upsert(local)

        return runCatching {
            if (mediaBytes != null) {
                val info = if (!fileName.isNullOrBlank() && !mimeType.isNullOrBlank()) {
                    fileName to mimeType
                } else {
                    ChatStorageService.defaultFileInfo(type)
                }
                val upload = storageService.upload(
                    familyId = familyId,
                    messageId = messageId,
                    fileName = info.first,
                    mimeType = info.second,
                    bytes = mediaBytes,
                )
                local = local.copy(
                    mediaStoragePath = upload.storagePath,
                    mediaURL = upload.downloadUrl,
                    mediaLocalPath = storageService.cachePlainMediaLocally(
                        familyId = familyId,
                        messageId = messageId,
                        fileName = info.first,
                        bytes = mediaBytes,
                    ),
                    mediaFileSize = upload.bytes,
                )
                chatDao.upsert(local)
            }

            remoteStore.upsert(local)
            val synced = local.copy(
                syncStateRaw = KBSyncState.SYNCED.rawValue,
                lastSyncError = null,
            )
            chatDao.upsert(synced)
            messageId
        }.getOrElse { err ->
            chatDao.upsert(
                local.copy(
                    syncStateRaw = KBSyncState.ERROR.rawValue,
                    lastSyncError = err.message,
                ),
            )
            throw err
        }
    }

    /**
     * Uploads [items] (photo/video byte arrays) concurrently to Firebase Storage, then
     * creates a single MEDIA_GROUP message in Room and Firestore.
     *
     * Each item is a pair of (bytes, isVideo). Max 10 items enforced here as a safety net.
     */
    suspend fun sendMediaGroupMessage(
        familyId: String,
        items: List<Pair<ByteArray, Boolean>>,  // Boolean = isVideo
        replyToId: String? = null,
    ): String {
        require(items.isNotEmpty()) { "sendMediaGroupMessage requires at least one item" }
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val senderName = auth.currentUser?.displayName.orEmpty()
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val capped = items.take(10)

        // Upload all files concurrently — each gets its own sub-path under the message id.
        val uploadedUrls: List<String>
        val uploadedTypes: List<String>
        withContext(Dispatchers.IO) {
            val results = coroutineScope {
                capped.mapIndexed { i, (bytes, isVideo) ->
                    async {
                        val type = if (isVideo) ChatMessageType.VIDEO else ChatMessageType.PHOTO
                        val (fileName, mimeType) = ChatStorageService.defaultFileInfo(type)
                        storageService.upload(
                            familyId = familyId,
                            messageId = "$messageId-$i",
                            fileName = fileName,
                            mimeType = mimeType,
                            bytes = bytes,
                        )
                    }
                }.awaitAll()
            }
            uploadedUrls = results.map { it.downloadUrl }
            uploadedTypes = capped.map { (_, isVideo) -> if (isVideo) "video" else "photo" }
        }

        val urlsJson   = JSONArray(uploadedUrls).toString()
        val typesJson  = JSONArray(uploadedTypes).toString()

        val local = KBChatMessageEntity(
            id = messageId,
            familyId = familyId,
            senderId = uid,
            senderName = senderName,
            typeRaw = ChatMessageType.MEDIA_GROUP.rawValue,
            text = null, latitude = null, longitude = null,
            mediaStoragePath = null, mediaURL = null,
            mediaDurationSeconds = null, mediaThumbnailURL = null,
            replyToId = replyToId, mediaLocalPath = null, mediaFileSize = null,
            mediaGroupURLsJSON = urlsJson, mediaGroupTypesJSON = typesJson,
            contactPayloadJSON = null, reactionsJSON = null,
            readByJSON = null, deletedForJSON = null,
            transcriptText = null, transcriptStatusRaw = "none",
            transcriptSourceRaw = null, transcriptLocaleIdentifier = null,
            transcriptIsFinal = false, transcriptUpdatedAtEpochMillis = null,
            transcriptErrorMessage = null,
            createdAtEpochMillis = now, editedAtEpochMillis = null,
            isDeleted = false, isDeletedForEveryone = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        chatDao.upsert(local)

        return runCatching {
            remoteStore.upsert(local)
            chatDao.upsert(local.copy(syncStateRaw = KBSyncState.SYNCED.rawValue, lastSyncError = null))
            messageId
        }.getOrElse { err ->
            chatDao.upsert(local.copy(syncStateRaw = KBSyncState.ERROR.rawValue, lastSyncError = err.message))
            throw err
        }
    }

    suspend fun fetchOlderMessages(
        familyId: String,
        cursor: DocumentSnapshot?,
        limit: Int = 50,
    ): Pair<DocumentSnapshot?, Int> {
        val (dtos, nextCursor) = remoteStore.fetchOlderMessages(
            familyId = familyId,
            cursor = cursor,
            limit = limit,
        )
        return nextCursor to dtos.size
    }

    suspend fun setTyping(
        familyId: String,
        isTyping: Boolean,
    ) {
        val uid = auth.currentUser?.uid ?: return
        val displayName = auth.currentUser?.displayName.orEmpty()
        remoteStore.setTyping(
            isTyping = isTyping,
            familyId = familyId,
            uid = uid,
            displayName = displayName,
        )
    }

    fun listenTyping(
        familyId: String,
        onChange: (List<String>) -> Unit,
    ): ListenerRegistration {
        val uid = auth.currentUser?.uid.orEmpty()
        return remoteStore.listenTyping(
            familyId = familyId,
            excludeUid = uid,
            onChange = onChange,
        )
    }

    suspend fun updateReactions(
        familyId: String,
        messageId: String,
        reactionsJSON: String?,
    ) {
        val local = chatDao.getById(messageId) ?: return
        val pending = local.copy(
            reactionsJSON = reactionsJSON,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        chatDao.upsert(pending)
        runCatching {
            remoteStore.updateReactions(familyId, messageId, reactionsJSON)
            chatDao.upsert(
                pending.copy(
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
        }.onFailure { err ->
            chatDao.upsert(
                pending.copy(
                    syncStateRaw = KBSyncState.ERROR.rawValue,
                    lastSyncError = err.message,
                ),
            )
        }
    }

    suspend fun updateMessageText(
        familyId: String,
        messageId: String,
        text: String,
    ) {
        val local = chatDao.getById(messageId) ?: return
        val now = System.currentTimeMillis()
        val pending = local.copy(
            text = text,
            editedAtEpochMillis = now,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        chatDao.upsert(pending)
        runCatching {
            remoteStore.updateMessageText(familyId, messageId, text)
            chatDao.upsert(
                pending.copy(
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
        }.onFailure { err ->
            chatDao.upsert(
                pending.copy(
                    syncStateRaw = KBSyncState.ERROR.rawValue,
                    lastSyncError = err.message,
                ),
            )
        }
    }

    suspend fun markAsRead(
        familyId: String,
        messageIds: List<String>,
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (messageIds.isEmpty()) return

        // Update locale immediato (optimistic), poi commit remoto.
        messageIds.forEach { id ->
            val local = chatDao.getById(id) ?: return@forEach
            val updatedReadBy = local.readByJSON.appendUniqueString(uid)
            chatDao.upsert(local.copy(readByJSON = updatedReadBy))
        }

        runCatching {
            remoteStore.markAsRead(familyId, messageIds, uid)
        }.onFailure { err ->
            // Best effort: non forziamo ERROR, i messaggi restano leggibili localmente.
            messageIds.forEach { id ->
                val local = chatDao.getById(id) ?: return@forEach
                if (local.lastSyncError.isNullOrBlank()) {
                    chatDao.upsert(local.copy(lastSyncError = err.message))
                }
            }
        }
    }

    /**
     * Clears every message in the chat for [familyId]:
     * - Soft-deletes each message remotely (so other devices stop seeing them).
     * - Deletes the original media payload from Firebase Storage.
     * - Wipes every local row from Room so the UI updates immediately.
     *
     * Failures on individual messages are swallowed: clearing the chat must always
     * leave the user with an empty thread locally, even if some remote ops fail.
     */
    suspend fun clearAllMessages(familyId: String) {
        if (familyId.isBlank()) return
        val all = chatDao.getAllByFamilyId(familyId)
        chatDao.deleteAllByFamilyId(familyId)
        all.forEach { msg ->
            runCatching {
                msg.mediaStoragePath?.takeIf { it.isNotBlank() }?.let { storageService.delete(it) }
            }
            runCatching { remoteStore.softDelete(familyId, msg.id) }
        }
    }

    suspend fun softDelete(
        familyId: String,
        messageId: String,
    ) {
        val local = chatDao.getById(messageId) ?: return
        // Optimistic local update: show placeholder immediately without waiting for
        // the Firestore round-trip (mirrors iOS behaviour).
        chatDao.upsert(
            local.copy(
                isDeleted = true,
                isDeletedForEveryone = true,
                syncStateRaw = KBSyncState.SYNCED.rawValue,
                lastSyncError = null,
            ),
        )
        runCatching {
            remoteStore.softDelete(familyId, messageId)
            // Best-effort media cleanup.
            local.mediaStoragePath?.takeIf { it.isNotBlank() }?.let { storageService.delete(it) }
        }.onFailure { err ->
            chatDao.upsert(
                local.copy(
                    syncStateRaw = KBSyncState.ERROR.rawValue,
                    lastSyncError = err.message,
                ),
            )
        }
    }

    suspend fun deleteForMe(
        familyId: String,
        messageId: String,
    ) {
        val uid = auth.currentUser?.uid ?: return
        val local = chatDao.getById(messageId) ?: return
        val updatedDeletedFor = local.deletedForJSON.appendUniqueString(uid)
        chatDao.upsert(local.copy(deletedForJSON = updatedDeletedFor))
        runCatching {
            remoteStore.addToDeletedFor(familyId, messageId, uid)
        }.onFailure { err ->
            chatDao.upsert(local.copy(lastSyncError = err.message))
        }
    }

    /**
     * Background pass that downloads and caches media for messages whose
     * [KBChatMessageEntity.mediaStoragePath] is set but [KBChatMessageEntity.mediaLocalPath]
     * is missing or points to a file that no longer exists on disk.
     *
     * Only PHOTO, VIDEO, AUDIO, and DOCUMENT messages are hydrated; MEDIA_GROUP items
     * reference URLs directly and do not use [mediaStoragePath].
     *
     * Runs concurrently (one coroutine per message) but is not cancellation-safe mid-download
     * — it is fire-and-forget from [scope] so it survives ViewModel cancellation.
     */
    suspend fun hydrateMissingMedia(familyId: String) {
        if (familyId.isBlank()) return
        val singleMediaTypes = setOf(
            ChatMessageType.PHOTO.rawValue,
            ChatMessageType.VIDEO.rawValue,
            ChatMessageType.AUDIO.rawValue,
            ChatMessageType.DOCUMENT.rawValue,
        )
        val needsHydration = chatDao.getAllByFamilyId(familyId).filter { entity ->
            entity.typeRaw in singleMediaTypes &&
                !entity.mediaStoragePath.isNullOrBlank() &&
                (entity.mediaLocalPath.isNullOrBlank() || !File(entity.mediaLocalPath).exists())
        }
        if (needsHydration.isEmpty()) return

        withContext(Dispatchers.IO) {
            coroutineScope {
                needsHydration.map { entity ->
                    async {
                        runCatching {
                            val storagePath = entity.mediaStoragePath!!
                            val bytes = storageService.downloadDecrypted(storagePath, familyId)
                            val fileName = storagePath.substringAfterLast('/')
                            val localPath = storageService.cachePlainMediaLocally(
                                familyId = familyId,
                                messageId = entity.id,
                                fileName = fileName,
                                bytes = bytes,
                            )
                            chatDao.upsert(entity.copy(mediaLocalPath = localPath))
                        }
                        // Failures are swallowed per-message so one bad download
                        // does not abort the rest of the batch.
                    }
                }.awaitAll()
            }
        }
    }

    fun flushPending(familyId: String) {
        scope.launch {
            chatDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue).forEach { msg ->
                runCatching {
                    remoteStore.upsert(msg)
                    chatDao.upsert(
                        msg.copy(
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }.onFailure { err ->
                    chatDao.upsert(
                        msg.copy(
                            syncStateRaw = KBSyncState.ERROR.rawValue,
                            lastSyncError = err.message,
                        ),
                    )
                }
            }
            chatDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue).forEach { msg ->
                runCatching {
                    remoteStore.upsert(msg.copy(isDeleted = true))
                    chatDao.deleteById(msg.id)
                }.onFailure { err ->
                    chatDao.upsert(
                        msg.copy(
                            syncStateRaw = KBSyncState.ERROR.rawValue,
                            lastSyncError = err.message,
                        ),
                    )
                }
            }
        }
    }
}

private fun String?.appendUniqueString(value: String): String {
    val arr = if (this.isNullOrBlank()) JSONArray() else JSONArray(this)
    for (i in 0 until arr.length()) {
        if (arr.optString(i) == value) return arr.toString()
    }
    arr.put(value)
    return arr.toString()
}

private fun KBChatMessageEntity.toDomain(): KBChatMessage =
    KBChatMessage(
        id = id,
        familyId = familyId,
        senderId = senderId,
        senderName = senderName,
        typeRaw = typeRaw,
        text = text,
        latitude = latitude,
        longitude = longitude,
        mediaStoragePath = mediaStoragePath,
        mediaURL = mediaLocalPath
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?.let { Uri.fromFile(File(it)).toString() }
            ?: mediaURL,
        mediaDurationSeconds = mediaDurationSeconds,
        mediaThumbnailURL = mediaThumbnailURL,
        replyToId = replyToId,
        mediaLocalPath = mediaLocalPath,
        mediaFileSize = mediaFileSize,
        mediaGroupURLsJSON = mediaGroupURLsJSON,
        mediaGroupTypesJSON = mediaGroupTypesJSON,
        contactPayloadJSON = contactPayloadJSON,
        reactionsJSON = reactionsJSON,
        readByJSON = readByJSON,
        deletedForJSON = deletedForJSON,
        transcriptText = transcriptText,
        transcriptStatusRaw = transcriptStatusRaw,
        transcriptSourceRaw = transcriptSourceRaw,
        transcriptLocaleIdentifier = transcriptLocaleIdentifier,
        transcriptIsFinal = transcriptIsFinal,
        transcriptUpdatedAtEpochMillis = transcriptUpdatedAtEpochMillis,
        transcriptErrorMessage = transcriptErrorMessage,
        createdAtEpochMillis = createdAtEpochMillis,
        editedAtEpochMillis = editedAtEpochMillis,
        isDeleted = isDeleted,
        isDeletedForEveryone = isDeletedForEveryone,
        syncStateRaw = syncStateRaw,
        lastSyncError = lastSyncError,
    )
