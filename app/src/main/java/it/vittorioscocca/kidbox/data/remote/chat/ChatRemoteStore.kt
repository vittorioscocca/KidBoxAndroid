package it.vittorioscocca.kidbox.data.remote.chat

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.chat.crypto.ChatCryptoService
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.data.chat.model.RemoteChatMessageDto
import it.vittorioscocca.kidbox.data.local.MessageSettingsPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBChatMessageDao
import it.vittorioscocca.kidbox.data.local.entity.KBChatMessageEntity
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.ui.screens.chat.TranscriptionService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

@Singleton
class ChatRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
    private val crypto: ChatCryptoService,
    private val chatDao: KBChatMessageDao,
    private val storageService: ChatStorageService,
    private val transcriptionService: TranscriptionService,
    private val messageSettingsPreferences: MessageSettingsPreferences,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transcriptionLogTag = "KB_Transcription"

    suspend fun upsert(local: KBChatMessageEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families")
            .document(local.familyId)
            .collection("chatMessages")
            .document(local.id)

        val payload = mutableMapOf<String, Any?>(
            "familyId" to local.familyId,
            "senderId" to local.senderId,
            "senderName" to local.senderName,
            "type" to local.typeRaw,
            "isDeleted" to local.isDeleted,
            "isDeletedForEveryone" to local.isDeletedForEveryone,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            // CRITICO: `text` plain non deve mai essere inviato.
            "text" to FieldValue.delete(),
        )

        val textEnc = local.text
            ?.takeIf { it.isNotBlank() }
            ?.let { crypto.encryptStringToBase64(it, local.familyId) }
        if (!textEnc.isNullOrBlank()) payload["textEnc"] = textEnc

        local.mediaStoragePath?.let { payload["mediaStoragePath"] = it }
        local.mediaURL?.let { payload["mediaURL"] = it }
        local.mediaDurationSeconds?.let { payload["mediaDurationSeconds"] = it }
        local.mediaThumbnailURL?.let { payload["mediaThumbnailURL"] = it }
        local.replyToId?.let { payload["replyToId"] = it }
        local.reactionsJSON?.let { payload["reactionsJSON"] = it }
        local.latitude?.let { payload["latitude"] = it }
        local.longitude?.let { payload["longitude"] = it }
        local.mediaFileSize?.takeIf { it > 0 }?.let { payload["mediaFileSize"] = it }
        local.mediaGroupURLsJSON?.let { payload["mediaGroupURLsJSON"] = it }
        local.mediaGroupTypesJSON?.let { payload["mediaGroupTypesJSON"] = it }
        local.contactPayloadJSON?.let { payload["contactPayloadJSON"] = it }
        local.deletedForJSON?.toStringListOrNull()?.let {
            payload["deletedFor"] = it
        }

        val exists = ref.get().await().exists()
        if (exists) {
            payload.remove("createdAt")
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun updateReactions(
        familyId: String,
        messageId: String,
        reactionsJSON: String?,
    ) {
        val ref = db.collection("families")
            .document(familyId)
            .collection("chatMessages")
            .document(messageId)
        ref.set(
            mapOf(
                "reactionsJSON" to (reactionsJSON ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun updateMessageText(
        familyId: String,
        messageId: String,
        text: String,
    ) {
        val textEnc = crypto.encryptStringToBase64(text, familyId)
        val ref = db.collection("families")
            .document(familyId)
            .collection("chatMessages")
            .document(messageId)
        ref.set(
            mapOf(
                "isEdited" to true,
                "editedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "textEnc" to textEnc,
                "text" to FieldValue.delete(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun addToDeletedFor(
        familyId: String,
        messageId: String,
        uid: String,
    ) {
        val ref = db.collection("families")
            .document(familyId)
            .collection("chatMessages")
            .document(messageId)
        ref.update("deletedFor", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun markAsRead(
        familyId: String,
        messageIds: List<String>,
        uid: String,
    ) {
        if (messageIds.isEmpty()) return
        messageIds.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { msgId ->
                val ref = db.collection("families")
                    .document(familyId)
                    .collection("chatMessages")
                    .document(msgId)
                batch.update(ref, "readBy", FieldValue.arrayUnion(uid))
            }
            batch.commit().await()
        }
    }

    suspend fun softDelete(
        familyId: String,
        messageId: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families")
            .document(familyId)
            .collection("chatMessages")
            .document(messageId)
        ref.set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    fun listenMessages(
        familyId: String,
        limit: Int = 50,
        onOldestDocument: ((DocumentSnapshot) -> Unit)? = null,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families")
            .document(familyId)
            .collection("chatMessages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(limit.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val snapshot = snap ?: return@addSnapshotListener
                if (!snapshot.metadata.hasPendingWrites()) {
                    snapshot.documents.firstOrNull()?.let { onOldestDocument?.invoke(it) }
                }
                if (snapshot.documentChanges.isEmpty()) return@addSnapshotListener

                ioScope.launch {
                    snapshot.documentChanges.forEach { diff ->
                        when (diff.type) {
                            DocumentChange.Type.REMOVED -> chatDao.deleteById(diff.document.id)
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED,
                            -> applyRemoteUpsert(familyId, diff.document)
                        }
                    }
                }
            }
    }

    suspend fun fetchOlderMessages(
        familyId: String,
        cursor: DocumentSnapshot?,
        limit: Int = 50,
    ): Pair<List<RemoteChatMessageDto>, DocumentSnapshot?> {
        var query = db.collection("families")
            .document(familyId)
            .collection("chatMessages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        if (cursor != null) {
            query = query.startAfter(cursor)
        }

        val snap = query.get().await()
        val dtos = snap.documents.map { doc -> doc.toRemoteDto(familyId) }.reversed()
        val nextCursor = snap.documents.lastOrNull()

        // Update local Room cache and trigger media hydration / transcription for each
        // page of older messages, exactly the same as for live updates.
        dtos.forEach { dto ->
            if (dto.isDeleted) {
                chatDao.deleteById(dto.id)
            } else {
                val existing = chatDao.getById(dto.id)
                val merged = dto.toLocalEntity(local = existing)
                chatDao.upsert(merged)
                maybeHydrateEncryptedMedia(familyId = familyId, dto = dto, local = merged)
            }
        }

        return dtos to nextCursor
    }

    suspend fun setTyping(
        isTyping: Boolean,
        familyId: String,
        uid: String,
        displayName: String,
    ) {
        db.collection("families")
            .document(familyId)
            .collection("typing")
            .document(uid)
            .set(
                mapOf(
                    "isTyping" to isTyping,
                    "name" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    fun listenTyping(
        familyId: String,
        excludeUid: String,
        onChange: (List<String>) -> Unit,
    ): ListenerRegistration {
        return db.collection("families")
            .document(familyId)
            .collection("typing")
            .addSnapshotListener { snap, _ ->
                val names = snap?.documents
                    ?.filter { it.id != excludeUid }
                    ?.filter { it.getBoolean("isTyping") == true }
                    ?.mapNotNull { it.getString("name") }
                    ?: emptyList()
                onChange(names)
            }
    }

    private suspend fun applyRemoteUpsert(familyId: String, doc: DocumentSnapshot) {
        val dto = doc.toRemoteDto(familyId)
        if (dto.isDeleted) {
            chatDao.deleteById(dto.id)
            return
        }

        val local = chatDao.getById(dto.id)
        val localSync = local?.let { KBSyncState.fromRaw(it.syncStateRaw) }
        if (localSync == KBSyncState.PENDING_DELETE) return

        val remoteUpdated = dto.editedAtEpochMillis ?: dto.createdAtEpochMillis ?: 0L
        val localUpdated = local?.editedAtEpochMillis ?: local?.createdAtEpochMillis ?: 0L
        if (localSync == KBSyncState.PENDING_UPSERT && remoteUpdated < localUpdated) return

        val merged = dto.toLocalEntity(local = local)
        chatDao.upsert(merged)
        maybeHydrateEncryptedMedia(familyId = familyId, dto = dto, local = merged)
    }

    /**
     * Ensures the encrypted media for a chat message is downloaded and cached
     * locally. Always triggers [maybeTranscribeIncomingAudio] afterwards (also when
     * the media is already cached) so that transcription is not lost for messages
     * that only need a status refresh.
     */
    private fun maybeHydrateEncryptedMedia(
        familyId: String,
        dto: RemoteChatMessageDto,
        local: KBChatMessageEntity,
    ) {
        val type = ChatMessageType.fromRaw(dto.typeRaw)
        val supportsBinaryMedia = type in setOf(
            ChatMessageType.PHOTO,
            ChatMessageType.VIDEO,
            ChatMessageType.AUDIO,
            ChatMessageType.DOCUMENT,
        )
        if (!supportsBinaryMedia) return
        val storagePath = dto.mediaStoragePath ?: return

        ioScope.launch {
            val localPath = local.mediaLocalPath
            val alreadyCached = !localPath.isNullOrBlank() && java.io.File(localPath).exists()
            val readyEntity: KBChatMessageEntity? = if (alreadyCached) {
                local
            } else {
                runCatching {
                    val plainBytes = storageService.downloadDecrypted(
                        storagePath = storagePath,
                        familyId = familyId,
                    )
                    val cachedPath = storageService.cachePlainMediaLocally(
                        familyId = familyId,
                        messageId = dto.id,
                        fileName = storagePath.substringAfterLast('/').removeSuffix(".kbenc"),
                        bytes = plainBytes,
                    )
                    val current = chatDao.getById(dto.id) ?: return@runCatching null
                    val mediaReady = current.copy(
                        mediaLocalPath = cachedPath,
                        lastSyncError = null,
                    )
                    chatDao.upsert(mediaReady)
                    mediaReady
                }.getOrElse { err ->
                    val current = chatDao.getById(dto.id) ?: return@launch
                    if (current.lastSyncError.isNullOrBlank()) {
                        chatDao.upsert(current.copy(lastSyncError = err.message))
                    }
                    null
                }
            }

            val finalEntity = readyEntity ?: return@launch
            maybeTranscribeIncomingAudio(
                familyId = familyId,
                dto = dto,
                local = finalEntity,
            )
        }
    }

    /**
     * Run on-device speech recognition for incoming audio messages.
     *
     * Each guard logs the reason it skipped so the pipeline is debuggable from
     * Logcat with the `KB_Transcription` filter.
     */
    private suspend fun maybeTranscribeIncomingAudio(
        familyId: String,
        dto: RemoteChatMessageDto,
        local: KBChatMessageEntity,
    ) {
        val tag = transcriptionLogTag
        val currentUid = auth.currentUser?.uid.orEmpty()
        if (currentUid.isBlank()) {
            Log.w(tag, "skip incoming: no current user id=${dto.id}")
            return
        }
        if (ChatMessageType.fromRaw(dto.typeRaw) != ChatMessageType.AUDIO) {
            return
        }
        if (!messageSettingsPreferences.isAudioTranscriptionEnabled()) {
            Log.w(tag, "skip incoming: transcription disabled in settings id=${dto.id}")
            return
        }
        if (dto.senderId == currentUid) {
            Log.w(tag, "skip incoming: own message id=${dto.id}")
            return
        }
        if (!local.transcriptText.isNullOrBlank()) {
            Log.w(tag, "skip incoming: already transcribed id=${dto.id}")
            return
        }
        // Allow a retry when the remote status is "completed" but the text is blank — this
        // happens when iOS transcription ran but returned nothing (silence, failed recognizer).
        // We skip only "processing" (another device is actively transcribing) to avoid races.
        if (local.transcriptStatusRaw == "processing") {
            Log.w(tag, "skip incoming: status=processing id=${dto.id}")
            return
        }
        val localPath = local.mediaLocalPath
        if (localPath.isNullOrBlank()) {
            Log.w(tag, "skip incoming: missing local media path id=${dto.id}")
            return
        }
        val audioFile = java.io.File(localPath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.w(
                tag,
                "skip incoming: file missing/empty id=${dto.id} path=$localPath",
            )
            return
        }

        Log.w(
            tag,
            "start incoming: family=$familyId id=${dto.id} sender=${dto.senderId} file=${audioFile.name} size=${audioFile.length()}",
        )

        chatDao.upsert(
            local.copy(
                transcriptStatusRaw = "processing",
                transcriptUpdatedAtEpochMillis = System.currentTimeMillis(),
                transcriptErrorMessage = null,
            ),
        )

        val transcript = runCatching {
            transcriptionService.transcribeAudioBestEffort(audioFile)
        }.onFailure {
            Log.e(tag, "incoming threw ${it.javaClass.simpleName}: ${it.message}", it)
        }.getOrNull()?.trim().orEmpty()

        val refreshed = chatDao.getById(dto.id) ?: return
        if (transcript.isNotBlank()) {
            Log.w(
                tag,
                "complete incoming: id=${dto.id} chars=${transcript.length}",
            )
            chatDao.upsert(
                refreshed.copy(
                    transcriptText = transcript,
                    transcriptStatusRaw = "completed",
                    transcriptIsFinal = true,
                    transcriptSourceRaw = "androidSpeechRecognizer",
                    transcriptLocaleIdentifier = "it-IT",
                    transcriptUpdatedAtEpochMillis = System.currentTimeMillis(),
                    transcriptErrorMessage = null,
                ),
            )
        } else {
            Log.w(tag, "fail incoming: id=${dto.id} blank result")
            chatDao.upsert(
                refreshed.copy(
                    transcriptStatusRaw = "failed",
                    transcriptUpdatedAtEpochMillis = System.currentTimeMillis(),
                    transcriptErrorMessage = "Trascrizione non disponibile sul dispositivo",
                ),
            )
        }
    }

    private fun DocumentSnapshot.toRemoteDto(familyId: String): RemoteChatMessageDto {
        val data = data ?: emptyMap<String, Any?>()
        val readBy = (data["readBy"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val deletedFor = (data["deletedFor"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        return RemoteChatMessageDto(
            id = id,
            familyId = familyId,
            senderId = data["senderId"] as? String ?: "",
            senderName = data["senderName"] as? String ?: "",
            typeRaw = data["type"] as? String ?: "text",
            text = data["text"] as? String,
            textEnc = data["textEnc"] as? String,
            mediaStoragePath = data["mediaStoragePath"] as? String,
            mediaURL = data["mediaURL"] as? String,
            mediaDurationSeconds = (data["mediaDurationSeconds"] as? Number)?.toInt(),
            mediaThumbnailURL = data["mediaThumbnailURL"] as? String,
            replyToId = data["replyToId"] as? String,
            reactionsJSON = data["reactionsJSON"] as? String,
            readByJSON = readBy.toJsonOrNull(),
            createdAtEpochMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time,
            editedAtEpochMillis = (data["editedAt"] as? Timestamp)?.toDate()?.time,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            isDeletedForEveryone = data["isDeletedForEveryone"] as? Boolean ?: false,
            deletedFor = deletedFor,
            latitude = (data["latitude"] as? Number)?.toDouble(),
            longitude = (data["longitude"] as? Number)?.toDouble(),
            mediaFileSize = (data["mediaFileSize"] as? Number)?.toLong(),
            mediaGroupURLsJSON = data["mediaGroupURLsJSON"] as? String,
            mediaGroupTypesJSON = data["mediaGroupTypesJSON"] as? String,
            contactPayloadJSON = data["contactPayloadJSON"] as? String,
            transcriptText = data["transcriptText"] as? String,
            transcriptStatusRaw = data["transcriptStatusRaw"] as? String,
            transcriptSourceRaw = data["transcriptSourceRaw"] as? String,
            transcriptLocaleIdentifier = data["transcriptLocaleIdentifier"] as? String,
            transcriptIsFinal = data["transcriptIsFinal"] as? Boolean,
            transcriptUpdatedAtEpochMillis = (data["transcriptUpdatedAt"] as? Timestamp)?.toDate()?.time,
            transcriptErrorMessage = data["transcriptErrorMessage"] as? String,
        )
    }

    private fun RemoteChatMessageDto.toLocalEntity(local: KBChatMessageEntity? = null): KBChatMessageEntity {
        val now = System.currentTimeMillis()
        val textPlain = when {
            !textEnc.isNullOrBlank() -> runCatching {
                crypto.decryptStringFromBase64(textEnc!!, familyId)
            }.getOrNull() ?: text
            else -> text
        }
        return KBChatMessageEntity(
            id = id,
            familyId = familyId,
            senderId = senderId,
            senderName = senderName,
            typeRaw = typeRaw,
            text = textPlain,
            latitude = latitude,
            longitude = longitude,
            mediaStoragePath = mediaStoragePath,
            mediaURL = mediaURL,
            mediaDurationSeconds = mediaDurationSeconds,
            mediaThumbnailURL = mediaThumbnailURL,
            replyToId = replyToId,
            mediaLocalPath = local?.mediaLocalPath,
            mediaFileSize = mediaFileSize,
            mediaGroupURLsJSON = mediaGroupURLsJSON,
            mediaGroupTypesJSON = mediaGroupTypesJSON,
            contactPayloadJSON = contactPayloadJSON,
            reactionsJSON = reactionsJSON,
            readByJSON = readByJSON,
            deletedForJSON = deletedFor.toJsonOrNull(),
            // Preserve local transcript fields when remote payload does not contain them.
            transcriptText = transcriptText ?: local?.transcriptText,
            transcriptStatusRaw = transcriptStatusRaw ?: local?.transcriptStatusRaw ?: "none",
            transcriptSourceRaw = transcriptSourceRaw ?: local?.transcriptSourceRaw,
            transcriptLocaleIdentifier = transcriptLocaleIdentifier ?: local?.transcriptLocaleIdentifier,
            transcriptIsFinal = transcriptIsFinal ?: local?.transcriptIsFinal ?: false,
            transcriptUpdatedAtEpochMillis = transcriptUpdatedAtEpochMillis ?: local?.transcriptUpdatedAtEpochMillis,
            transcriptErrorMessage = transcriptErrorMessage ?: local?.transcriptErrorMessage,
            createdAtEpochMillis = createdAtEpochMillis ?: now,
            editedAtEpochMillis = editedAtEpochMillis,
            isDeleted = false,
            isDeletedForEveryone = isDeletedForEveryone,
            syncStateRaw = KBSyncState.SYNCED.rawValue,
            lastSyncError = null,
        )
    }
}

private fun List<String>.toJsonOrNull(): String? {
    if (isEmpty()) return null
    return JSONArray(this).toString()
}

private fun String.toStringListOrNull(): List<String>? {
    if (isBlank()) return null
    val arr = JSONArray(this)
    return buildList {
        for (i in 0 until arr.length()) {
            val v = arr.optString(i)
            if (v.isNotBlank()) add(v)
        }
    }
}

private fun <T> List<T>.chunked(size: Int): List<List<T>> {
    if (isEmpty()) return emptyList()
    val out = ArrayList<List<T>>()
    var index = 0
    while (index < this.size) {
        val end = kotlin.math.min(index + size, this.size)
        out.add(subList(index, end))
        index = end
    }
    return out
}
