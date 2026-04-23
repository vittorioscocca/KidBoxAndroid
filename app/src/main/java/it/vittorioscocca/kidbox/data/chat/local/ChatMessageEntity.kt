package it.vittorioscocca.kidbox.data.chat.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import it.vittorioscocca.kidbox.data.chat.model.ContactPayload
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity

/**
 * Local chat message model aligned to iOS `KBChatMessage`.
 *
 * Complex fields are persisted as JSON via [ChatMessageConverters].
 */
@Entity(
    tableName = "kb_chat_messages_v2",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("senderId"),
        Index("createdAtEpochMillis"),
        Index("replyToId"),
        Index("syncStateRaw"),
        Index("isDeleted"),
    ],
)
@TypeConverters(ChatMessageConverters::class)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val senderId: String,
    val senderName: String,

    // Message type
    val typeRaw: String,
    val text: String?,
    val textEnc: String?,

    // Location payload
    val latitude: Double?,
    val longitude: Double?,

    // Single media payload
    val mediaStoragePath: String?,
    val mediaURL: String?,
    val mediaDurationSeconds: Int?,
    val mediaThumbnailURL: String?,
    val mediaLocalPath: String?,
    val mediaFileSize: Long?,
    val replyToId: String?,

    // Complex payloads persisted as JSON through converters
    val reactions: Map<String, List<String>>?,
    val readBy: List<String>?,
    val deletedFor: List<String>?,
    val mediaGroup: List<MediaGroupItem>?,
    val contactPayload: ContactPayload?,

    // Transcript fields
    val transcriptText: String?,
    val transcriptStatusRaw: String,
    val transcriptSourceRaw: String?,
    val transcriptLocaleIdentifier: String?,
    val transcriptIsFinal: Boolean,
    val transcriptUpdatedAtEpochMillis: Long?,
    val transcriptErrorMessage: String?,

    // Timeline & lifecycle
    val createdAtEpochMillis: Long,
    val editedAtEpochMillis: Long?,
    val isDeleted: Boolean,
    val isDeletedForEveryone: Boolean,

    // Sync lifecycle
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
