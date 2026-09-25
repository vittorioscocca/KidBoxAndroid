package it.vittorioscocca.kidbox.data.remote.chat

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream

/**
 * Avanzamento dell'invio per singolo messaggio, mostrato come anello dentro la
 * bolla (stile WhatsApp), come su iOS (`ChatUploadProgressStore`).
 *
 * Valore < 0 = in preparazione (anello indeterminato), 0…1 = caricamento.
 * Fuori dal ViewModel di proposito: ogni tick ricompone solo l'anello che
 * osserva quel messaggio, non l'intera lista.
 */
object ChatUploadProgress {
    const val INDETERMINATE = -1f

    private val _state = MutableStateFlow<Map<String, Float>>(emptyMap())
    val state: StateFlow<Map<String, Float>> = _state.asStateFlow()

    fun begin(messageId: String) = _state.update { it + (messageId to INDETERMINATE) }

    fun update(messageId: String, value: Float) = _state.update {
        if (messageId in it) it + (messageId to value.coerceIn(0f, 1f)) else it
    }

    fun end(messageId: String) {
        synchronized(this) {
            cancelHandlers.remove(messageId)
            cancelled.remove(messageId)
        }
        _state.update { it - messageId }
    }

    // ── Stop dall'anello ──────────────────────────────────────────────────
    private val cancelHandlers = mutableMapOf<String, () -> Unit>()
    private val cancelled = mutableSetOf<String>()

    /** Chi ha in mano il task di Storage dice come fermarlo; se lo stop è già arrivato, scatta subito. */
    fun setCancelHandler(messageId: String, handler: () -> Unit) {
        val runNow = synchronized(this) {
            if (messageId in cancelled) true else { cancelHandlers[messageId] = handler; false }
        }
        if (runNow) handler()
    }

    /** Tocco sull'anello: ferma l'upload; il repository vede [isCancelled] e toglie il messaggio. */
    fun cancel(messageId: String) {
        if (!isActive(messageId)) return
        val handler = synchronized(this) {
            cancelled.add(messageId)
            cancelHandlers.remove(messageId)
        }
        handler?.invoke()
    }

    fun isCancelled(messageId: String): Boolean = synchronized(this) { messageId in cancelled }

    fun isActive(messageId: String): Boolean = messageId in _state.value

    fun progressOf(messageId: String): Flow<Float?> =
        state.map { it[messageId] }.distinctUntilChanged()
}

/** Dimensioni in pixel come si vedono, cioè con la rotazione già applicata. */
object ChatMediaDimensions {

    fun ofImage(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return@runCatching null
        val rotation = ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
        if (rotation == 90 || rotation == 270) h to w else w to h
    }.getOrNull()

    fun ofVideo(path: String): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0) null
            else if (rotation == 90 || rotation == 270) h to w else w to h
        }.getOrNull().also { runCatching { retriever.release() } }
    }
}
