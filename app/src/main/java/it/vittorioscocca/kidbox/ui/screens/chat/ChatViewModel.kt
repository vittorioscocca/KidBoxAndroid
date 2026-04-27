package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.data.local.MessageSettingsPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.ChatRepository
import org.json.JSONObject
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val familyId: String = "",
    val currentUid: String = "",
    val messages: List<UiChatMessage> = emptyList(),
    val inputText: String = "",
    val typingUsers: List<String> = emptyList(),
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val highlightedMessageId: String? = null,
    val replyingToId: String? = null,
    val isSending: Boolean = false,
    val isAudioTranscriptionEnabled: Boolean = true,
    val errorText: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val chatRepository: ChatRepository,
    private val badgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
    private val messageSettingsPreferences: MessageSettingsPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState(currentUid = auth.currentUser?.uid.orEmpty()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var typingRegistration: ListenerRegistration? = null
    private var oldestCursor: DocumentSnapshot? = null
    private var hasBoundFamily = false
    private var typingJob: Job? = null
    private var activeSendCount: Int = 0

    init {
        _uiState.value = _uiState.value.copy(
            isAudioTranscriptionEnabled = messageSettingsPreferences.isAudioTranscriptionEnabled(),
        )
        observeFamily()
    }

    fun reloadMessageSettings() {
        _uiState.value = _uiState.value.copy(
            isAudioTranscriptionEnabled = messageSettingsPreferences.isAudioTranscriptionEnabled(),
        )
    }

    private fun observeFamily() {
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                val familyId = families.firstOrNull()?.id.orEmpty()
                if (familyId.isBlank()) {
                    stopRealtime()
                    _uiState.value = ChatUiState(
                        isLoading = false,
                        currentUid = auth.currentUser?.uid.orEmpty(),
                        errorText = "Nessuna famiglia attiva",
                    )
                    return@collectLatest
                }
                if (hasBoundFamily && _uiState.value.familyId == familyId) return@collectLatest
                hasBoundFamily = true
                bindFamily(familyId)
            }
        }
    }

    private fun bindFamily(familyId: String) {
        oldestCursor = null
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            familyId = familyId,
            hasMoreOlder = true,
            isLoadingOlder = false,
            errorText = null,
        )

        viewModelScope.launch {
            badgeManager.clearLocal(CounterField.CHAT)
            runCatching { badgeManager.resetRemote(familyId, CounterField.CHAT) }
        }

        chatRepository.startRealtime(
            familyId = familyId,
            limit = 50,
            onOldestDocument = { snapshot ->
                oldestCursor = snapshot
            },
            onError = { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Errore sincronizzazione chat")
            },
        )

        typingRegistration?.remove()
        typingRegistration = chatRepository.listenTyping(familyId) { names ->
            _uiState.value = _uiState.value.copy(typingUsers = names)
        }

        viewModelScope.launch {
            chatRepository.observeMessages(familyId).collectLatest { messages ->
                chatRepository.scheduleLocalMediaCacheCleanup(familyId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    messages = messages
                        .map { it.toUi() }
                        .filterNot { it.isDeleted && it.type != ChatMessageType.TEXT },
                )
            }
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        triggerTypingSignal()
    }

    fun sendText() {
        val state = _uiState.value
        val familyId = state.familyId
        val text = state.inputText.trim()
        if (familyId.isBlank() || text.isBlank()) return

        val replyToId = state.replyingToId
        _uiState.value = state.copy(inputText = "", replyingToId = null)

        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = ChatMessageType.TEXT,
                    text = text,
                    replyToId = replyToId,
                )
                chatRepository.setTyping(familyId, false)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    fun sendMediaAttachment(bytes: ByteArray, isVideo: Boolean) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank() || bytes.isEmpty()) return
        val replyToId = state.replyingToId
        _uiState.value = state.copy(replyingToId = null)
        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = if (isVideo) ChatMessageType.VIDEO else ChatMessageType.PHOTO,
                    mediaBytes = bytes,
                    replyToId = replyToId,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio allegato non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    fun sendDocumentAttachment(fileName: String, mimeType: String, bytes: ByteArray) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank() || bytes.isEmpty()) return
        val replyToId = state.replyingToId
        _uiState.value = state.copy(replyingToId = null)
        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = ChatMessageType.DOCUMENT,
                    text = fileName,
                    mediaBytes = bytes,
                    fileName = fileName,
                    mimeType = mimeType,
                    replyToId = replyToId,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio documento non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    fun sendAudioAttachment(fileName: String, mimeType: String, bytes: ByteArray, transcriptText: String? = null) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank() || bytes.isEmpty()) return
        val replyToId = state.replyingToId
        _uiState.value = state.copy(replyingToId = null)
        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = ChatMessageType.AUDIO,
                    text = transcriptText,
                    mediaBytes = bytes,
                    fileName = fileName,
                    mimeType = mimeType,
                    replyToId = replyToId,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio vocale non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    fun sendLocationAttachment(latitude: Double, longitude: Double) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank()) return
        val replyToId = state.replyingToId
        _uiState.value = state.copy(replyingToId = null)
        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = ChatMessageType.LOCATION,
                    latitude = latitude,
                    longitude = longitude,
                    replyToId = replyToId,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio posizione non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    fun sendContactAttachment(fullName: String, phone: String, avatarURL: String? = null) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank() || fullName.isBlank()) return
        val replyToId = state.replyingToId
        _uiState.value = state.copy(replyingToId = null)
        val payload = JSONObject().apply {
            put("fullName", fullName)
            put("phoneNumber", phone)
            if (!avatarURL.isNullOrBlank()) put("avatarURL", avatarURL)
        }.toString()
        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = ChatMessageType.CONTACT,
                    text = fullName,
                    contactPayloadJSON = payload,
                    replyToId = replyToId,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio contatto non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    fun startReply(messageId: String) {
        _uiState.value = _uiState.value.copy(replyingToId = messageId)
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingToId = null)
    }

    fun toggleReaction(message: UiChatMessage, emoji: String) {
        val uid = _uiState.value.currentUid
        if (uid.isBlank()) return
        val updated = message.reactions.toMutableMap()
        val current = updated[emoji].orEmpty().toMutableList()
        if (current.contains(uid)) {
            current.remove(uid)
            if (current.isEmpty()) updated.remove(emoji) else updated[emoji] = current
        } else {
            current.add(uid)
            updated[emoji] = current
        }
        viewModelScope.launch {
            chatRepository.updateReactions(
                familyId = message.familyId,
                messageId = message.id,
                reactionsJSON = updated.toJsonStringOrNull(),
            )
        }
    }

    fun updateMessageText(messageId: String, newText: String) {
        val state = _uiState.value
        if (state.familyId.isBlank() || newText.isBlank()) return
        viewModelScope.launch {
            chatRepository.updateMessageText(
                familyId = state.familyId,
                messageId = messageId,
                text = newText,
            )
        }
    }

    fun deleteForEveryone(messageId: String) {
        val state = _uiState.value
        if (state.familyId.isBlank()) return
        viewModelScope.launch {
            chatRepository.softDelete(
                familyId = state.familyId,
                messageId = messageId,
            )
        }
    }

    fun deleteForMe(messageId: String) {
        val state = _uiState.value
        if (state.familyId.isBlank()) return
        viewModelScope.launch {
            chatRepository.deleteForMe(
                familyId = state.familyId,
                messageId = messageId,
            )
        }
    }

    fun canDeleteForEveryone(message: UiChatMessage): Boolean {
        val uid = _uiState.value.currentUid
        return message.userCanEditOrDeleteForEveryone(uid = uid, nowMs = System.currentTimeMillis())
    }

    fun markVisibleAsRead(visibleIds: List<String>) {
        val state = _uiState.value
        if (state.familyId.isBlank() || visibleIds.isEmpty()) return
        viewModelScope.launch {
            chatRepository.markAsRead(
                familyId = state.familyId,
                messageIds = visibleIds,
            )
        }
    }

    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.familyId.isBlank() || state.isLoadingOlder || !state.hasMoreOlder) return
        val cursor = oldestCursor ?: run {
            _uiState.value = state.copy(hasMoreOlder = false)
            return
        }
        _uiState.value = state.copy(isLoadingOlder = true)
        viewModelScope.launch {
            runCatching {
                val (nextCursor, count) = chatRepository.fetchOlderMessages(
                    familyId = state.familyId,
                    cursor = cursor,
                    limit = 50,
                )
                oldestCursor = nextCursor
                _uiState.value = _uiState.value.copy(
                    isLoadingOlder = false,
                    hasMoreOlder = count > 0 && nextCursor != null,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoadingOlder = false,
                    errorText = err.message ?: "Errore caricamento cronologia",
                )
            }
        }
    }

    fun highlightMessage(messageId: String) {
        _uiState.value = _uiState.value.copy(highlightedMessageId = messageId)
        viewModelScope.launch {
            delay(900)
            if (_uiState.value.highlightedMessageId == messageId) {
                _uiState.value = _uiState.value.copy(highlightedMessageId = null)
            }
        }
    }

    private fun triggerTypingSignal() {
        val state = _uiState.value
        if (state.familyId.isBlank()) return
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            chatRepository.setTyping(state.familyId, true)
            delay(1200)
            chatRepository.setTyping(state.familyId, false)
        }
    }

    private fun stopRealtime() {
        typingJob?.cancel()
        typingRegistration?.remove()
        typingRegistration = null
        chatRepository.stopRealtime()
    }

    override fun onCleared() {
        stopRealtime()
        super.onCleared()
    }

    private fun setSending(isStart: Boolean) {
        activeSendCount = if (isStart) activeSendCount + 1 else (activeSendCount - 1).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(isSending = activeSendCount > 0)
    }
}
