package it.vittorioscocca.kidbox.ui.screens.settings.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.remote.ai.AIMessagePayload
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.data.remote.support.SupportConversationMessage
import it.vittorioscocca.kidbox.data.remote.support.SupportTicketFirestorePayload
import it.vittorioscocca.kidbox.data.remote.support.SupportTicketRemoteStore
import it.vittorioscocca.kidbox.data.remote.support.SupportTicketSubmitDto
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
import it.vittorioscocca.kidbox.util.KBFileLogger
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SupportUiState(
    val messages: List<SupportMessage> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val attachedImages: List<Uri> = emptyList(),
    val detectedType: String? = null,
    val showSubmitConfirm: Boolean = false,
    val ticketSent: Boolean = false,
    val errorMessage: String? = null,
)

data class SupportMessage(
    val id: String,
    val role: String,
    val text: String,
    val imageUris: List<Uri> = emptyList(),
)

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val aiService: AIService,
    private val supportRemote: SupportTicketRemoteStore,
    private val familySessionPreferences: FamilySessionPreferences,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    private val ticketId: String = UUID.randomUUID().toString()

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun addImage(uri: Uri) {
        _uiState.update { state ->
            if (state.attachedImages.size >= MAX_IMAGES || state.attachedImages.contains(uri)) {
                state
            } else {
                state.copy(attachedImages = state.attachedImages + uri)
            }
        }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { it.copy(attachedImages = it.attachedImages.filter { u -> u != uri }) }
    }

    fun sendMessage(text: String, images: List<Uri> = emptyList()) {
        val trimmed = text.trim()
        val imageList = images.take(MAX_IMAGES)
        if (trimmed.isEmpty() && imageList.isEmpty()) return

        viewModelScope.launch {
            val familyId = resolveFamilyId() ?: run {
                _uiState.update { it.copy(errorMessage = "Seleziona una famiglia attiva.") }
                return@launch
            }

            val userMessage = SupportMessage(
                id = UUID.randomUUID().toString(),
                role = ROLE_USER,
                text = trimmed,
                imageUris = imageList,
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    isLoading = true,
                    inputText = "",
                    attachedImages = emptyList(),
                    errorMessage = null,
                )
            }

            val apiMessages = buildApiMessages(_uiState.value.messages)
            val result = aiService.sendMessages(
                messages = apiMessages,
                systemPrompt = SUPPORT_SYSTEM_PROMPT,
                familyId = familyId,
                purpose = PURPOSE_SUPPORT,
            )

            result.fold(
                onSuccess = { response ->
                    val parsed = parseAssistantReply(response.reply)
                    val assistantMessage = SupportMessage(
                        id = UUID.randomUUID().toString(),
                        role = ROLE_ASSISTANT,
                        text = parsed.displayText,
                    )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + assistantMessage,
                            isLoading = false,
                            detectedType = parsed.type ?: state.detectedType,
                            showSubmitConfirm = state.showSubmitConfirm || parsed.requestSubmit,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapAiError(err),
                        )
                    }
                },
            )
        }
    }

    fun confirmSubmit() {
        viewModelScope.launch {
            val state = _uiState.value
            val familyId = resolveFamilyId() ?: run {
                _uiState.update { it.copy(errorMessage = "Seleziona una famiglia attiva.") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    showSubmitConfirm = false,
                    errorMessage = null,
                )
            }

            runCatching {
                val type = state.detectedType ?: TYPE_QUESTION
                val conversation = ArrayList<SupportConversationMessage>(state.messages.size)
                for (msg in state.messages) {
                    val content: Any = if (msg.imageUris.isEmpty()) {
                        msg.text
                    } else {
                        buildMultimodalContent(msg.text, msg.imageUris)
                    }
                    conversation.add(SupportConversationMessage(role = msg.role, content = content))
                }
                val imageBase64 = encodeImagesForTicket(state.messages, state.attachedImages)
                val rawLogs = if (type == TYPE_BUG) {
                    KBFileLogger.readLogs()
                        .takeIf { it.isNotBlank() }
                        ?.let(SupportTicketFirestorePayload::truncateLogs)
                } else {
                    null
                }
                val title = state.messages.firstOrNull { it.role == ROLE_USER }?.text
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(TITLE_MAX_LEN)
                    ?.ifBlank { DEFAULT_TICKET_TITLE }
                    ?: DEFAULT_TICKET_TITLE
                val summary = state.messages.lastOrNull { it.role == ROLE_ASSISTANT }?.text
                    ?.take(SUMMARY_MAX_LEN)
                    ?: title

                val dto = SupportTicketSubmitDto.create(
                    context = context,
                    auth = auth,
                    id = ticketId,
                    familyId = familyId,
                    type = type,
                    title = title,
                    summary = summary,
                    conversation = conversation,
                    imagesBase64 = imageBase64,
                    rawLogs = rawLogs,
                )
                supportRemote.submit(dto)
            }.onSuccess {
                _uiState.update {
                    it.copy(isLoading = false, ticketSent = true)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Invio ticket non riuscito.",
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissSubmitConfirm() {
        _uiState.update { it.copy(showSubmitConfirm = false) }
    }

    private fun resolveFamilyId(): String? =
        familySessionPreferences.getActiveFamilyId()?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun buildApiMessages(messages: List<SupportMessage>): List<AIMessagePayload> {
        val out = ArrayList<AIMessagePayload>(messages.size)
        for (msg in messages) {
            val content: Any = if (msg.role == ROLE_USER && msg.imageUris.isNotEmpty()) {
                buildMultimodalContent(msg.text, msg.imageUris)
            } else {
                msg.text
            }
            out.add(AIMessagePayload(role = msg.role, content = content))
        }
        return out
    }

    private suspend fun buildMultimodalContent(text: String, imageUris: List<Uri>): List<Map<String, Any?>> {
        val blocks = mutableListOf<Map<String, Any?>>()
        for (uri in imageUris.take(MAX_IMAGES)) {
            val data = uriToJpegBase64(uri) ?: continue
            blocks.add(
                mapOf(
                    "type" to "image",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to "image/jpeg",
                        "data" to data,
                    ),
                ),
            )
        }
        if (text.isNotBlank()) {
            blocks.add(mapOf("type" to "text", "text" to text))
        }
        if (blocks.isEmpty()) {
            blocks.add(mapOf("type" to "text", "text" to text.ifBlank { "(immagine)" }))
        }
        return blocks
    }

    private suspend fun uriToJpegBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, boundsOpts)
                val sampleSize = computeInSampleSize(
                    boundsOpts.outWidth,
                    boundsOpts.outHeight,
                    MAX_IMAGE_DIMENSION,
                )
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeStream(input2, null, decodeOpts)
                        ?: return@runCatching null
                    val scaled = scaleBitmapToMax(bitmap, MAX_IMAGE_DIMENSION)
                    if (scaled !== bitmap) bitmap.recycle()
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    scaled.recycle()
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }
        }.getOrNull()
    }

    private suspend fun encodeImagesForTicket(
        messages: List<SupportMessage>,
        attached: List<Uri>,
    ): List<String> = withContext(Dispatchers.IO) {
        val uris = (messages.flatMap { it.imageUris } + attached).distinct().take(MAX_IMAGES)
        uris.mapNotNull { uriToJpegBase64(it) }
    }

    private fun parseAssistantReply(raw: String): ParsedAssistantReply {
        val type = TYPE_TAG_REGEX.find(raw)?.groupValues?.get(1)?.lowercase()
        val requestSubmit = SUBMIT_TAG_REGEX.containsMatchIn(raw) ||
            SUBMIT_PHRASES.any { raw.contains(it, ignoreCase = true) }
        val displayText = raw
            .replace(TYPE_TAG_REGEX, "")
            .replace(SUBMIT_TAG_REGEX, "")
            .trim()
        return ParsedAssistantReply(
            displayText = displayText,
            type = type,
            requestSubmit = requestSubmit,
        )
    }

    private fun mapAiError(err: Throwable): String = when (err) {
        is AIServiceException -> when (err.serviceError) {
            AIServiceError.RateLimitReached ->
                "Limite messaggi AI giornaliero raggiunto. Riprova domani."
            AIServiceError.NetworkError ->
                "Connessione assente. Controlla la rete e riprova."
            is AIServiceError.ServerError ->
                err.serviceError.message ?: "Errore del servizio AI."
        }
        else -> err.message ?: "Errore imprevisto."
    }

    private data class ParsedAssistantReply(
        val displayText: String,
        val type: String?,
        val requestSubmit: Boolean,
    )

    private companion object {
        const val MAX_IMAGES = 5
        const val MAX_IMAGE_DIMENSION = 1024
        const val JPEG_QUALITY = 85
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val PURPOSE_SUPPORT = "support"
        const val TYPE_QUESTION = "question"
        const val TYPE_BUG = "bug"
        const val TYPE_SUGGESTION = "suggestion"
        const val TITLE_MAX_LEN = 120
        const val SUMMARY_MAX_LEN = 2000
        const val DEFAULT_TICKET_TITLE = "Richiesta supporto KidBox"

        val TYPE_TAG_REGEX = Regex("""\[TYPE:(question|bug|suggestion)]""", RegexOption.IGNORE_CASE)
        val SUBMIT_TAG_REGEX = Regex("""\[SUBMIT]""", RegexOption.IGNORE_CASE)

        val SUBMIT_PHRASES = listOf(
            "posso inviare il ticket",
            "invio il ticket",
            "confermi l'invio",
            "procedo con l'invio",
            "ticket pronto per l'invio",
        )

        val SUPPORT_SYSTEM_PROMPT = """
Sei l'assistente di supporto di KidBox, app di gestione familiare.
Conosci tutte le funzionalità: famiglia, bambini, calendario, note, documenti,
spese, farmaci, visite mediche, posizione, chat, animali, garage/veicoli,
casa, viaggi, portafoglio, password, routine, AI assistente.
Rispondi in italiano, in modo chiaro e conciso.
Se l'utente descrive un problema tecnico o un bug, chiedi conferma e poi
imposta il tipo su "bug". Se suggerisce una feature, tipo "suggestion".
Altrimenti "question". Quando capisci il tipo, includi nel tuo messaggio
il tag nascosto [TYPE:bug] o [TYPE:suggestion] o [TYPE:question].
NON chiedere MAI all'utente di esportare, copiare o inviare manualmente i log
dall'app o dalle impostazioni: KidBox allega automaticamente all'invio del
ticket gli stessi log diagnostici usati per i crash report su Firebase.
Per i bug puoi spiegare che i log tecnici verranno inclusi automaticamente
quando l'utente confermerà l'invio della segnalazione.
Quando il ticket è pronto per l'invio e l'utente ha confermato, includi [SUBMIT] nel messaggio.
""".trimIndent()
    }
}

private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / sample > maxDim * 2 || height / sample > maxDim * 2) {
        sample *= 2
    }
    return sample
}

private fun scaleBitmapToMax(source: Bitmap, maxDim: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxDim && h <= maxDim) return source
    val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
    val nw = max(1, (w * scale).roundToInt())
    val nh = max(1, (h * scale).roundToInt())
    return Bitmap.createScaledBitmap(source, nw, nh, true)
}
