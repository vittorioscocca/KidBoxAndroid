package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.chat.model.ChatMention
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.data.chat.model.toMentionsJsonOrNull
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.MessageSettingsPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.ChatRepository
import org.json.JSONObject
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository

/**
 * Membri della famiglia disponibili come destinatari di una @menzione nella chat.
 *
 * Lo stato è esposto dal ViewModel come lista di [ChatMentionCandidate]:
 * il composer mostra il picker solo quando ce ne sono almeno due (i.e. la chat
 * ha più di due partecipanti, sender incluso). La lista è sempre escluso il
 * `currentUid` perché citarsi da soli non ha senso.
 */
data class ChatMentionCandidate(
    val uid: String,
    val displayName: String,
    val photoURL: String?,
)

data class ChatUiState(
    val isLoading: Boolean = true,
    val familyId: String = "",
    val currentUid: String = "",
    val messages: List<UiChatMessage> = emptyList(),
    /**
     * Messaggi citati da [messages] ma fuori dalla finestra caricata, indicizzati per id.
     * Alimenta le anteprime di risposta ai messaggi vecchi.
     */
    val replyContexts: Map<String, UiChatMessage> = emptyMap(),
    val typingUsers: List<String> = emptyList(),
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val highlightedMessageId: String? = null,
    val replyingToId: String? = null,
    val isSending: Boolean = false,
    val isAudioTranscriptionEnabled: Boolean = true,
    val errorText: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val mentionCandidates: List<ChatMentionCandidate> = emptyList(),
    /** Membri attivi della famiglia, per capire se la chat ha davvero un destinatario. */
    val familyMemberCount: Int = 0,
) {
    /** Con un solo membro non c'è nessuno con cui chattare: la chat resta inerte. */
    val isSoloFamily: Boolean get() = familyMemberCount in 1..1

    /** `true` se la chat ha più di due partecipanti (sender incluso). */
    val canMention: Boolean get() = mentionCandidates.size >= 2
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val familySessionPreferences: FamilySessionPreferences,
    private val chatRepository: ChatRepository,
    private val badgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
    private val messageSettingsPreferences: MessageSettingsPreferences,
    private val photoVideoRepository: PhotoVideoRepository,
    private val photoDao: it.vittorioscocca.kidbox.data.local.dao.KBFamilyPhotoDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState(currentUid = auth.currentUser?.uid.orEmpty()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * Testo del composer, tenuto **fuori** da [ChatUiState] di proposito.
     *
     * Stando nello stato unico, ogni carattere digitato emetteva un nuovo ChatUiState e
     * ricomponeva l'intero ChatScreen (2000+ righe, item provider della LazyColumn incluso).
     * Con un flow dedicato solo il composer si ricompone mentre si scrive.
     */
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /**
     * Quanti messaggi tenere in memoria dal DB locale. Cresce quando l'utente pagina
     * all'indietro; senza questo Room riemetteva l'intera cronologia ad ogni scrittura.
     */
    private val messageWindow = MutableStateFlow(INITIAL_MESSAGE_WINDOW)

    private var typingRegistration: ListenerRegistration? = null
    private var oldestCursor: DocumentSnapshot? = null
    private var hasBoundFamily = false
    private var typingJob: Job? = null
    /** True quando abbiamo già segnalato "sta scrivendo" e non serve ripeterlo. */
    private var isTypingSignalled = false
    private var activeSendCount: Int = 0
    /** Job che osserva i family member per la famiglia attiva (per il picker @menzioni). */
    private var mentionsJob: Job? = null
    /** Candidati confermati dall'utente nel composer, in attesa di essere inviati. */
    private val pendingMentions = mutableListOf<ChatMentionCandidate>()

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

    fun loadScrollAnchor(): Pair<String, Int>? {
        val familyId = _uiState.value.familyId
        return messageSettingsPreferences.getChatScrollAnchor(familyId)
    }

    fun saveScrollAnchor(messageId: String?, offset: Int) {
        val familyId = _uiState.value.familyId
        messageSettingsPreferences.setChatScrollAnchor(familyId, messageId, offset)
    }

    fun saveScrollAnchorFor(familyId: String, messageId: String?, offset: Int) {
        messageSettingsPreferences.setChatScrollAnchor(familyId, messageId, offset)
    }

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (active) _uiState.value.searchQuery else "",
        )
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearChat() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching { chatRepository.clearAllMessages(familyId) }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorText = err.message ?: "Impossibile svuotare la chat",
                    )
                }
        }
    }

    private fun observeFamily() {
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                val familyId = ActiveFamilyResolver.resolveFamilyId(
                    families,
                    familySessionPreferences.getActiveFamilyId(),
                )
                if (familyId.isBlank()) {
                    stopRealtime()
                    hasBoundFamily = false
                    _uiState.value = ChatUiState(
                        isLoading = false,
                        currentUid = auth.currentUser?.uid.orEmpty(),
                        errorText = "Nessuna famiglia attiva",
                    )
                    return@collectLatest
                }
                val previousId = _uiState.value.familyId
                if (hasBoundFamily && previousId == familyId) return@collectLatest
                if (hasBoundFamily && previousId != familyId) {
                    stopRealtime()
                    hasBoundFamily = false
                }
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

        // Background hydration: download and cache media for messages that have a Firebase
        // Storage path but no local file yet. Errors are swallowed per-message inside the
        // repository so this never surfaces to the user.
        viewModelScope.launch {
            chatRepository.hydrateMissingMedia(familyId)
        }

        typingRegistration?.remove()
        typingRegistration = chatRepository.listenTyping(familyId) { names ->
            _uiState.value = _uiState.value.copy(typingUsers = names)
        }

        // Osserva i membri attivi della famiglia per popolare i candidati delle
        // @menzioni (escluso l'utente corrente). Quando la lista cambia (es. un
        // membro si unisce/lascia la famiglia) aggiorniamo lo stato UI.
        mentionsJob?.cancel()
        mentionsJob = viewModelScope.launch {
            familyMemberDao.observeActiveByFamilyId(familyId).collectLatest { members ->
                val myUid = auth.currentUser?.uid.orEmpty()
                val candidates = members
                    .asSequence()
                    .filter { it.userId.isNotBlank() && it.userId != myUid }
                    .mapNotNull { member ->
                        val name = member.displayName?.trim().orEmpty()
                        if (name.isBlank()) return@mapNotNull null
                        ChatMentionCandidate(
                            uid = member.userId,
                            displayName = name,
                            photoURL = member.photoURL,
                        )
                    }
                    .sortedBy { it.displayName.lowercase() }
                    .toList()
                _uiState.value = _uiState.value.copy(
                    mentionCandidates = candidates,
                    familyMemberCount = members.count { it.userId.isNotBlank() },
                )
            }
        }

        messageWindow.value = INITIAL_MESSAGE_WINDOW
        viewModelScope.launch {
            messageWindow
                .flatMapLatest { limit -> chatRepository.observeMessages(familyId, limit) }
                // Mapping KBChatMessage → UiChatMessage is CPU-only (JSON parsing, string ops)
                // but for 50+ messages it's measurable on the main thread. Run it on Default
                // so the UI thread stays free for rendering while the list is being prepared.
                .map { messages ->
                    withContext(Dispatchers.Default) {
                        // Un solo set di formatter per batch invece di due SimpleDateFormat
                        // per ogni lettura di label. Uso sequenziale: SimpleDateFormat non
                        // è thread-safe.
                        val formatters = ChatLabelFormatters()
                        messages
                            .map { it.toUi(formatters) }
                            // Hide messages deleted only for me (isDeleted=true, isDeletedForEveryone=false).
                            // Messages deleted for everyone (isDeletedForEveryone=true) are kept in the list
                            // so the bubble can render the "Messaggio eliminato" placeholder.
                            .filterNot { it.isDeleted && !it.isDeletedForEveryone }
                    }
                }
                .collectLatest { mappedMessages ->
                    chatRepository.scheduleLocalMediaCacheCleanup(familyId)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messages = mappedMessages,
                        replyContexts = loadMissingReplyContexts(mappedMessages),
                    )
                }
        }
    }

    /**
     * Carica i messaggi citati che non rientrano nella finestra osservata.
     *
     * Nel caso comune (si risponde a messaggi recenti) la lista di id mancanti è vuota e
     * non si tocca il DB, quindi il costo aggiuntivo è nullo.
     */
    private suspend fun loadMissingReplyContexts(
        messages: List<UiChatMessage>,
    ): Map<String, UiChatMessage> {
        val inWindow = messages.mapTo(HashSet(messages.size)) { it.id }
        val missing = messages.asSequence()
            .mapNotNull { it.replyToId }
            .filterNot { it in inWindow }
            .distinct()
            .toList()
        if (missing.isEmpty()) return emptyMap()
        val fetched = runCatching { chatRepository.getMessagesByIds(missing) }.getOrDefault(emptyList())
        if (fetched.isEmpty()) return emptyMap()
        return withContext(Dispatchers.Default) {
            val formatters = ChatLabelFormatters()
            fetched.associate { it.id to it.toUi(formatters) }
        }
    }

    fun onInputTextChange(text: String) {
        _inputText.value = text
        triggerTypingSignal()
    }

    fun sendText() {
        val state = _uiState.value
        val familyId = state.familyId
        val text = _inputText.value.trim()
        if (familyId.isBlank() || text.isBlank()) return

        val replyToId = state.replyingToId
        val mentions = resolveMentionsFor(text, state.mentionCandidates)
        val mentionsJson = mentions.toMentionsJsonOrNull()
        pendingMentions.clear()
        _inputText.value = ""
        _uiState.value = state.copy(replyingToId = null)

        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMessage(
                    familyId = familyId,
                    type = ChatMessageType.TEXT,
                    text = text,
                    replyToId = replyToId,
                    mentionsJSON = mentionsJson,
                )
                typingJob?.cancel()
                isTypingSignalled = false
                chatRepository.setTyping(familyId, false)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio non riuscito")
            }.also {
                setSending(false)
            }
        }
    }

    /**
     * Registra un candidato selezionato dall'utente nel picker @menzioni del
     * composer. Verrà consumato al successivo invio del messaggio (sendText).
     */
    fun registerMention(candidate: ChatMentionCandidate) {
        if (pendingMentions.none { it.uid == candidate.uid && it.displayName == candidate.displayName }) {
            pendingMentions.add(candidate)
        }
    }

    /**
     * Calcola la lista finale di menzioni da scrivere sul messaggio:
     *  1. I candidati confermati nel composer che compaiono ancora come
     *     `@<displayName>` nel testo (l'utente potrebbe averli cancellati).
     *  2. Eventuali `@DisplayName` digitati a mano che corrispondono a un
     *     membro presente in [candidates]. I displayName più lunghi vengono
     *     valutati prima per evitare collisioni "Mario" vs "Mario Rossi".
     */
    private fun resolveMentionsFor(
        text: String,
        candidates: List<ChatMentionCandidate>,
    ): List<ChatMention> {
        val resolved = LinkedHashMap<String, ChatMention>()
        pendingMentions.forEach { cand ->
            val token = "@${cand.displayName}"
            if (text.contains(token) && !resolved.containsKey(cand.uid)) {
                resolved[cand.uid] = ChatMention(uid = cand.uid, displayName = cand.displayName)
            }
        }
        candidates.sortedByDescending { it.displayName.length }.forEach { cand ->
            val token = "@${cand.displayName}"
            if (text.contains(token) && !resolved.containsKey(cand.uid)) {
                resolved[cand.uid] = ChatMention(uid = cand.uid, displayName = cand.displayName)
            }
        }
        return resolved.values.toList()
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

    /** Sends multiple photos/videos as a single MEDIA_GROUP message. Max 10 items. */
    /**
     * Risolve media della libreria KidBox in URI di file locali.
     *
     * NON invia: i media entrano nella stessa coda `pendingMedia` usata dalla
     * galleria del telefono, così anteprima, rimozione di un singolo elemento,
     * compressione video e scelta fra invio singolo e gruppo restano identici a
     * prescindere da dove arriva il media. Prima venivano spediti subito,
     * saltando tutto quel percorso.
     *
     * `preparePreviewFile` usa il file locale se c'è, altrimenti scarica e
     * decifra l'originale una volta sola.
     *
     * I media illeggibili vengono saltati: meglio portarne avanti cinque su sei
     * che perdere l'intera selezione.
     */
    suspend fun resolveKidBoxMedia(photoIds: List<String>): List<Pair<android.net.Uri, Boolean>> {
        if (photoIds.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            photoIds.mapNotNull { id ->
                runCatching {
                    val photo = photoDao.getById(id) ?: return@runCatching null
                    val file = photoVideoRepository.preparePreviewFile(photo)
                    android.net.Uri.fromFile(file) to photo.mimeType.startsWith("video/")
                }.getOrNull()
            }
        }
    }

    fun sendMediaGroup(items: List<Pair<ByteArray, Boolean>>) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank() || items.isEmpty()) return
        val replyToId = state.replyingToId
        _uiState.value = state.copy(replyingToId = null)
        viewModelScope.launch {
            setSending(true)
            runCatching {
                chatRepository.sendMediaGroupMessage(
                    familyId = familyId,
                    items = items,
                    replyToId = replyToId,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorText = err.message ?: "Invio allegati non riuscito")
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

    fun sendAudioAttachment(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        durationSeconds: Int? = null,
        transcriptText: String? = null,
    ) {
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
                    mediaDurationSeconds = durationSeconds?.takeIf { it > 0 },
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
        _uiState.value = state.copy(isLoadingOlder = true)
        viewModelScope.launch {
            // Prima allarga la finestra locale: se Room ha già righe più vecchie fuori
            // finestra (sessioni precedenti), bastano quelle e non serve toccare la rete.
            val widened = messageWindow.value + MESSAGE_WINDOW_PAGE
            messageWindow.value = widened
            val localTotal = runCatching { chatRepository.countMessages(state.familyId) }.getOrDefault(0)
            if (widened <= localTotal) {
                _uiState.value = _uiState.value.copy(isLoadingOlder = false)
                return@launch
            }

            val cursor = oldestCursor ?: run {
                _uiState.value = _uiState.value.copy(isLoadingOlder = false, hasMoreOlder = false)
                return@launch
            }
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

    /**
     * Segnala "sta scrivendo" con un debounce vero.
     *
     * La versione precedente lanciava `setTyping(true)` *prima* del delay e si affidava a
     * `typingJob.cancel()` per il throttling — ma il cancel arrivava quando la scrittura era
     * già partita, quindi ogni singolo tasto premuto produceva una scrittura su Firestore.
     * Qui il `true` parte una volta sola per raffica e solo il `false` è rimandato.
     */
    private fun triggerTypingSignal() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        if (!isTypingSignalled) {
            isTypingSignalled = true
            viewModelScope.launch { runCatching { chatRepository.setTyping(familyId, true) } }
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            isTypingSignalled = false
            runCatching { chatRepository.setTyping(familyId, false) }
        }
    }

    private fun stopRealtime() {
        typingJob?.cancel()
        isTypingSignalled = false
        mentionsJob?.cancel()
        mentionsJob = null
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

    private companion object {
        /** Messaggi tenuti in memoria all'apertura della chat. */
        const val INITIAL_MESSAGE_WINDOW = 60

        /** Di quanto cresce la finestra ad ogni paginazione all'indietro. */
        const val MESSAGE_WINDOW_PAGE = 50

        /** Inattività dopo la quale si segnala che l'utente ha smesso di scrivere. */
        const val TYPING_IDLE_MS = 1200L
    }
}
