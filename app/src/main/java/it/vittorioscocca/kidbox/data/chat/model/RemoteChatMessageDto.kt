package it.vittorioscocca.kidbox.data.chat.model

/**
 * Android mirror of iOS RemoteChatMessageDTO.
 *
 * NOTE:
 * - `textEnc` carries base64(iv + ciphertext + tag) AES-GCM payload.
 * - Complex collections are represented as JSON strings to keep the
 *   wire format identical to iOS.
 */
data class RemoteChatMessageDto(
    val id: String,
    val familyId: String,
    val senderId: String,
    val senderName: String,
    val typeRaw: String,
    val text: String?,
    val textEnc: String?,
    val mediaStoragePath: String?,
    val mediaURL: String?,
    val mediaDurationSeconds: Int?,
    val mediaThumbnailURL: String?,
    val replyToId: String?,
    val reactionsJSON: String?,
    val readByJSON: String?,
    val createdAtEpochMillis: Long?,
    val editedAtEpochMillis: Long?,
    val isDeleted: Boolean,
    val isDeletedForEveryone: Boolean,
    val deletedFor: List<String>,
    val latitude: Double?,
    val longitude: Double?,
    val mediaFileSize: Long?,
    val mediaGroupURLsJSON: String?,
    val mediaGroupTypesJSON: String?,
    val contactPayloadJSON: String?,
    val transcriptText: String?,
    val transcriptStatusRaw: String?,
    val transcriptSourceRaw: String?,
    val transcriptLocaleIdentifier: String?,
    val transcriptIsFinal: Boolean?,
    val transcriptUpdatedAtEpochMillis: Long?,
    val transcriptErrorMessage: String?,
)
