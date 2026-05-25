package it.vittorioscocca.kidbox.data.remote.support

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth

/** Singolo turno chat da inviare su Firestore (`conversation` array). */
data class SupportConversationMessage(
    val role: String,
    /** Testo semplice oppure lista blocchi multimodale (vision). */
    val content: Any,
)

/**
 * Payload invio ticket su `support_tickets` (root collection).
 * Usa [deviceInfo] / [appVersionFrom] per campi device; [imagesBase64] max 5.
 */
data class SupportTicketSubmitDto(
    val id: String,
    val familyId: String,
    val uid: String,
    val userEmail: String,
    /** "question" | "bug" | "suggestion" */
    val type: String,
    val title: String,
    val summary: String,
    val conversation: List<SupportConversationMessage>,
    val imagesBase64: List<String> = emptyList(),
    val appVersion: String,
    val osVersion: String,
    val device: String,
    /** Solo per type "bug" con consenso utente; altrimenti null/blank. */
    val rawLogs: String? = null,
) {
    init {
        require(type in VALID_TYPES) { "type non valido: $type" }
        require(imagesBase64.size <= MAX_IMAGES) {
            "max $MAX_IMAGES immagini per ticket"
        }
        imagesBase64.forEach { data ->
            require(data.length * 0.75 <= MAX_IMAGE_DECODED_BYTES) {
                "immagine troppo grande (max 5MB decodificata)"
            }
        }
    }

    fun conversationFirestorePayload(): List<Map<String, Any?>> =
        conversation.map { msg ->
            mapOf(
                "role" to msg.role,
                "content" to msg.content,
            )
        }

    companion object {
        const val MAX_IMAGES = 5
        private const val MAX_IMAGE_DECODED_BYTES = 5_000_000L
        val VALID_TYPES = setOf("question", "bug", "suggestion")

        data class DeviceInfo(
            val appVersion: String,
            val osVersion: String,
            val device: String,
        )

        fun appVersionFrom(context: Context): String =
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

        fun osVersion(): String = Build.VERSION.RELEASE ?: "unknown"

        fun deviceModel(): String =
            listOfNotNull(
                Build.MANUFACTURER?.takeIf { it.isNotBlank() },
                Build.MODEL?.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { "unknown" }

        fun deviceInfo(context: Context): DeviceInfo = DeviceInfo(
            appVersion = appVersionFrom(context),
            osVersion = osVersion(),
            device = deviceModel(),
        )

        /**
         * Costruisce DTO con uid/email da Firebase Auth e metadati device da [context].
         */
        fun create(
            context: Context,
            auth: FirebaseAuth,
            id: String,
            familyId: String,
            type: String,
            title: String,
            summary: String,
            conversation: List<SupportConversationMessage>,
            imagesBase64: List<String> = emptyList(),
            rawLogs: String? = null,
        ): SupportTicketSubmitDto {
            val user = auth.currentUser ?: error("Not authenticated")
            val uid = user.uid
            val email = user.email?.trim().orEmpty()
            val device = deviceInfo(context)
            val logs = rawLogs?.trim().takeIf { !it.isNullOrEmpty() && type == "bug" }
            return SupportTicketSubmitDto(
                id = id,
                familyId = familyId,
                uid = uid,
                userEmail = email,
                type = type,
                title = title,
                summary = summary,
                conversation = conversation,
                imagesBase64 = imagesBase64,
                appVersion = device.appVersion,
                osVersion = device.osVersion,
                device = device.device,
                rawLogs = logs,
            )
        }
    }
}
