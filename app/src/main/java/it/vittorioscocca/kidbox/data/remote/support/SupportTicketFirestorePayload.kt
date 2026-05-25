package it.vittorioscocca.kidbox.data.remote.support

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.firestore.FieldValue
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Riduce e valida il payload ticket prima della scrittura Firestore (limite doc 1 MiB).
 * Le immagini restano solo nel campo `images`; la `conversation` non contiene base64.
 */
object SupportTicketFirestorePayload {

    const val FIRESTORE_MAX_BYTES = 1_048_576
    private const val SAFETY_MARGIN_BYTES = 64_000
    private const val TARGET_MAX_BYTES = FIRESTORE_MAX_BYTES - SAFETY_MARGIN_BYTES

    /** Stesso ordine di grandezza dei crash report (ultime righe più utili). */
    const val MAX_LOG_BYTES = 48 * 1024

    const val TICKET_IMAGE_MAX_DIMENSION = 720
    const val TICKET_JPEG_QUALITY = 78
    private const val MAX_IMAGES = SupportTicketSubmitDto.MAX_IMAGES
    private const val MAX_SINGLE_IMAGE_BYTES = 180_000

    fun truncateLogs(raw: String): String {
        if (raw.toByteArray(Charsets.UTF_8).size <= MAX_LOG_BYTES) return raw
        var lines = raw.split('\n').toMutableList()
        while (lines.isNotEmpty()) {
            val candidate = lines.joinToString("\n")
            if (candidate.toByteArray(Charsets.UTF_8).size <= MAX_LOG_BYTES) return candidate
            lines.removeAt(0)
        }
        return raw.take(MAX_LOG_BYTES)
    }

    fun conversationForFirestore(
        messages: List<SupportConversationMessage>,
    ): List<Map<String, Any?>> =
        messages.map { msg ->
            mapOf(
                "role" to msg.role,
                "content" to sanitizeContent(msg.content),
            )
        }

    fun compactImages(base64List: List<String>): List<String> =
        base64List
            .take(MAX_IMAGES)
            .mapNotNull { recompressBase64Jpeg(it) }

    fun buildDocumentData(
        ticket: SupportTicketSubmitDto,
        platform: String,
        statusNew: String,
    ): Map<String, Any?> {
        var images = compactImages(ticket.imagesBase64)
        var conversation = conversationForFirestore(ticket.conversation)
        var logs = ticket.rawLogs?.trim().orEmpty().takeIf { it.isNotEmpty() && ticket.type == "bug" }
            ?.let(::truncateLogs)

        var data = coreFields(
            ticket = ticket,
            platform = platform,
            statusNew = statusNew,
            conversation = conversation,
            images = images,
            rawLogs = logs,
        )

        var estimated = estimateMapBytes(data)
        while (estimated > TARGET_MAX_BYTES && images.isNotEmpty()) {
            images = images.dropLast(1)
            data = coreFields(ticket, platform, statusNew, conversation, images, logs)
            estimated = estimateMapBytes(data)
        }
        while (estimated > TARGET_MAX_BYTES && logs != null && logs.isNotEmpty()) {
            logs = truncateLogs(logs.take(logs.length * 3 / 4).ifEmpty { logs })
            data = coreFields(ticket, platform, statusNew, conversation, images, logs)
            estimated = estimateMapBytes(data)
            if (logs.toByteArray(Charsets.UTF_8).size <= 4_096) {
                logs = null
                data = coreFields(ticket, platform, statusNew, conversation, images, null)
                estimated = estimateMapBytes(data)
                break
            }
        }
        while (estimated > TARGET_MAX_BYTES && conversation.size > 2) {
            conversation = conversation.takeLast(conversation.size - 1)
            data = coreFields(ticket, platform, statusNew, conversation, images, logs)
            estimated = estimateMapBytes(data)
        }

        if (estimated > FIRESTORE_MAX_BYTES) {
            error(
                "Ticket troppo grande per Firestore (~${estimated / 1024} KB). " +
                    "Rimuovi qualche screenshot e riprova.",
            )
        }
        return data
    }

    private fun coreFields(
        ticket: SupportTicketSubmitDto,
        platform: String,
        statusNew: String,
        conversation: List<Map<String, Any?>>,
        images: List<String>,
        rawLogs: String?,
    ): MutableMap<String, Any?> {
        val map = mutableMapOf<String, Any?>(
        "id" to ticket.id,
        "familyId" to ticket.familyId,
        "uid" to ticket.uid,
        "userEmail" to ticket.userEmail,
        "type" to ticket.type,
        "title" to ticket.title.take(200),
        "summary" to ticket.summary.take(2000),
        "conversation" to conversation,
        "images" to images,
        "platform" to platform,
        "appVersion" to ticket.appVersion,
        "osVersion" to ticket.osVersion,
        "device" to ticket.device,
        "status" to statusNew,
        "createdAt" to FieldValue.serverTimestamp(),
        )
        if (!rawLogs.isNullOrBlank() && ticket.type == "bug") {
            map["rawLogs"] = rawLogs
        }
        return map
    }

    private fun sanitizeContent(content: Any): Any = when (content) {
        is String -> content.take(16_000)
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            val blocks = content.filterIsInstance<Map<String, Any?>>()
            val imageCount = blocks.count { (it["type"] as? String)?.equals("image", true) == true }
            val text = blocks
                .filter { (it["type"] as? String)?.equals("text", true) == true }
                .mapNotNull { it["text"] as? String }
                .joinToString("\n")
                .trim()
            when {
                text.isNotEmpty() && imageCount > 0 ->
                    "$text\n($imageCount screenshot allegati — vedi campo images)"
                text.isNotEmpty() -> text.take(16_000)
                imageCount > 0 -> "($imageCount screenshot allegati — vedi campo images)"
                else -> "(messaggio)"
            }
        }
        else -> content.toString().take(16_000)
    }

    private fun recompressBase64Jpeg(base64: String): String? {
        val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = scaleBitmap(bitmap, TICKET_IMAGE_MAX_DIMENSION)
        if (scaled !== bitmap) bitmap.recycle()
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, TICKET_JPEG_QUALITY, out)
        scaled.recycle()
        var jpeg = out.toByteArray()
        var quality = TICKET_JPEG_QUALITY
        while (jpeg.size > MAX_SINGLE_IMAGE_BYTES && quality > 40) {
            quality -= 10
            val retry = ByteArrayOutputStream()
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: break
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, retry)
            bmp.recycle()
            jpeg = retry.toByteArray()
        }
        return Base64.encodeToString(jpeg, Base64.NO_WRAP)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h, 1f)
        if (scale >= 1f) return bitmap
        val nw = max(1, (w * scale).roundToInt())
        val nh = max(1, (h * scale).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    private fun estimateMapBytes(map: Map<String, Any?>): Int {
        var total = 128
        for ((key, value) in map) {
            total += key.toByteArray(Charsets.UTF_8).size + 8
            total += estimateValueBytes(value)
        }
        return total
    }

    private fun estimateValueBytes(value: Any?): Int = when (value) {
        null -> 4
        is String -> value.toByteArray(Charsets.UTF_8).size + 16
        is Number, is Boolean -> 16
        is List<*> -> value.sumOf { estimateValueBytes(it) } + 32
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            estimateMapBytes(value as Map<String, Any?>)
        }
        else -> value.toString().toByteArray(Charsets.UTF_8).size + 16
    }
}
