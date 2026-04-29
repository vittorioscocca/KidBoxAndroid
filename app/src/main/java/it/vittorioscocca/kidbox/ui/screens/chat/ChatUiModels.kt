package it.vittorioscocca.kidbox.ui.screens.chat

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.data.chat.model.ContactPayload
import it.vittorioscocca.kidbox.data.chat.model.LabeledStringValue
import it.vittorioscocca.kidbox.domain.model.KBChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// @Immutable tells the Compose compiler that instances of this class are completely
// immutable: equality is structural (data class) and no fields can change after creation.
// This allows Compose to skip recomposing any ChatBubble whose UiChatMessage hasn't
// changed, eliminating unnecessary redraws when unrelated state updates arrive.
@Immutable
data class UiChatMessage(
    val id: String,
    val familyId: String,
    val senderId: String,
    val senderName: String,
    val type: ChatMessageType,
    val text: String?,
    val latitude: Double?,
    val longitude: Double?,
    val mediaUrl: String?,
    val mediaLocalPath: String?,   // absolute on-device path when media is hydrated; null otherwise
    val mediaDurationSeconds: Int?,
    val mediaFileSize: Long?,
    val transcriptText: String?,
    val transcriptStatusRaw: String,
    val replyToId: String?,
    val mediaGroupUrls: List<String>,
    val mediaGroupTypes: List<String>,
    val contactPayload: ContactPayload?,
    val reactions: Map<String, List<String>>,
    val readBy: List<String>,
    val createdAtMillis: Long,
    val editedAtMillis: Long?,
    val isDeleted: Boolean,
    val isDeletedForEveryone: Boolean,
    val syncStateRaw: Int,
) {
    val dayLabel: String
        get() = daySeparatorLabel(createdAtMillis)

    val timeLabel: String
        get() = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(createdAtMillis))
}

internal fun KBChatMessage.toUi(): UiChatMessage =
    UiChatMessage(
        id = id,
        familyId = familyId,
        senderId = senderId,
        senderName = senderName.ifBlank { "Utente" },
        type = ChatMessageType.fromRaw(typeRaw),
        text = text,
        latitude = latitude,
        longitude = longitude,
        mediaUrl = mediaURL,
        mediaLocalPath = mediaLocalPath,
        mediaDurationSeconds = mediaDurationSeconds,
        mediaFileSize = mediaFileSize,
        transcriptText = transcriptText,
        transcriptStatusRaw = transcriptStatusRaw,
        replyToId = replyToId,
        mediaGroupUrls = mediaGroupURLsJSON.toJsonStringList(),
        mediaGroupTypes = mediaGroupTypesJSON.toJsonStringList(),
        contactPayload = contactPayloadJSON.toContactPayload(),
        reactions = reactionsJSON.toReactionsMap(),
        readBy = readByJSON.toJsonStringList(),
        createdAtMillis = createdAtEpochMillis,
        editedAtMillis = editedAtEpochMillis,
        isDeleted = isDeleted,
        isDeletedForEveryone = isDeletedForEveryone,
        syncStateRaw = syncStateRaw,
    )

internal fun Map<String, List<String>>.toJsonStringOrNull(): String? {
    if (isEmpty()) return null
    val root = JSONObject()
    entries.forEach { (emoji, users) ->
        val arr = JSONArray()
        users.forEach(arr::put)
        root.put(emoji, arr)
    }
    return root.toString()
}

internal fun UiChatMessage.isReadBy(uid: String): Boolean = readBy.contains(uid)

internal fun UiChatMessage.userCanEditOrDeleteForEveryone(uid: String, nowMs: Long): Boolean {
    if (senderId != uid) return false
    return nowMs - createdAtMillis <= 5 * 60 * 1000
}

internal fun UiChatMessage.previewText(): String =
    when {
        isDeletedForEveryone -> "Messaggio eliminato"
        type == ChatMessageType.PHOTO -> "Foto"
        type == ChatMessageType.VIDEO -> "Video"
        type == ChatMessageType.AUDIO -> "Audio"
        type == ChatMessageType.DOCUMENT -> text ?: "Documento"
        type == ChatMessageType.LOCATION -> "Posizione condivisa"
        type == ChatMessageType.MEDIA_GROUP -> "${mediaGroupUrls.size} allegati"
        type == ChatMessageType.CONTACT -> contactPayload?.fullName ?: "Contatto"
        else -> text.orEmpty().ifBlank { "Messaggio" }
    }

@Composable
internal fun messageBubbleColor(isOwn: Boolean): Color =
    if (isOwn) Color(0xFFFF6B00) else androidx.compose.material3.MaterialTheme.kidBoxColors.incomingBubble

private fun String?.toJsonStringList(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    val arr = JSONArray(this)
    return buildList {
        for (i in 0 until arr.length()) {
            val value = arr.optString(i)
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun String?.toReactionsMap(): Map<String, List<String>> {
    if (this.isNullOrBlank()) return emptyMap()
    val root = JSONObject(this)
    val out = linkedMapOf<String, List<String>>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val emoji = keys.next()
        val arr = root.optJSONArray(emoji) ?: continue
        val users = buildList {
            for (i in 0 until arr.length()) {
                val uid = arr.optString(i)
                if (uid.isNotBlank()) add(uid)
            }
        }
        out[emoji] = users
    }
    return out
}

private fun String?.toContactPayload(): ContactPayload? {
    if (this.isNullOrBlank()) return null
    val root = JSONObject(this)
    return ContactPayload(
        givenName = root.optString("givenName"),
        familyName = root.optString("familyName"),
        phoneNumbers = root.optJSONArray("phoneNumbers").toLabeledValues(),
        emailAddresses = root.optJSONArray("emailAddresses").toLabeledValues(),
        avatarData = null, // intentionally ignored in Android bubble rendering
    )
}

private fun JSONArray?.toLabeledValues(): List<LabeledStringValue> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val label = obj.optString("label")
            val value = obj.optString("value")
            if (value.isNotBlank()) add(LabeledStringValue(label = label, value = value))
        }
    }
}

private fun daySeparatorLabel(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    return when {
        DateUtils.isToday(timestampMs) -> "Oggi"
        DateUtils.isToday(timestampMs + DateUtils.DAY_IN_MILLIS) -> "Ieri"
        else -> SimpleDateFormat("EEEE d MMM", Locale.ITALY).format(Date(timestampMs))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString() }
    }
}
