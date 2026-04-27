package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index("senderId"), Index("createdAtEpochMillis"), Index("replyToId")],
)
data class KBChatMessageEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val senderId: String,
    val senderName: String,
    val typeRaw: String,
    val text: String?,
    val latitude: Double?,
    val longitude: Double?,
    val mediaStoragePath: String?,
    val mediaURL: String?,
    val mediaDurationSeconds: Int?,
    val mediaThumbnailURL: String?,
    val replyToId: String?,
    val mediaLocalPath: String?,
    val mediaFileSize: Long?,
    val mediaGroupURLsJSON: String?,
    val mediaGroupTypesJSON: String?,
    val contactPayloadJSON: String?,
    val reactionsJSON: String?,
    val readByJSON: String?,
    val deletedForJSON: String?,
    val transcriptText: String?,
    val transcriptStatusRaw: String,
    val transcriptSourceRaw: String?,
    val transcriptLocaleIdentifier: String?,
    val transcriptIsFinal: Boolean,
    val transcriptUpdatedAtEpochMillis: Long?,
    val transcriptErrorMessage: String?,
    val createdAtEpochMillis: Long,
    val editedAtEpochMillis: Long?,
    val isDeleted: Boolean,
    val isDeletedForEveryone: Boolean,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
