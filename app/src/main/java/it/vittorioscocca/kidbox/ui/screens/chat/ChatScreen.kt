@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.asImageBitmap
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.collectLatest
import android.media.MediaMetadataRetriever
import it.vittorioscocca.kidbox.util.VideoCompressor
import it.vittorioscocca.kidbox.util.fixVideoFrameOrientation
import java.io.ByteArrayOutputStream

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var actionTarget by remember { mutableStateOf<UiChatMessage?>(null) }
    var fullScreenMediaUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenIsVideo by remember { mutableStateOf(false) }
    // Media-group gallery: triple of (urls, types, startIndex)
    var mediaGroupGallery by remember { mutableStateOf<Triple<List<String>, List<String>, Int>?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    // (Uri, isVideo) items staged for review before sending
    var pendingMedia by remember { mutableStateOf<List<Pair<Uri, Boolean>>>(emptyList()) }
    // True while bytes are being read from the staged URIs (before isSending kicks in).
    // Keeps the tray visible + spinner showing during the I/O gap.
    var isSendingPendingMedia by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<UiChatMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var locationPickerLatLng by remember { mutableStateOf<LatLng?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val recorderManager = remember { AudioRecorderManager(context) }
    val proximityManager = remember { ProximitySensorManager(context) }
    val inputBarViewModel = remember { ChatInputBarViewModel() }
    val inputBarState by inputBarViewModel.uiState.collectAsStateWithLifecycle()
    var previousMessagesCount by remember { mutableStateOf(0) }
    var didInitialBottomScroll by remember { mutableStateOf(false) }
    var pendingOlderAnchorIndex by remember { mutableStateOf<Int?>(null) }
    var pendingOlderAnchorOffset by remember { mutableStateOf<Int?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // derivedStateOf reads LazyListState snapshot state internally — no outer remember keys
    // needed. Adding message size / index as remember keys would recreate the derivedState
    // object on every list change instead of letting it evolve naturally, causing extra work.
    val showScrollToBottomButton by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf false
            // Show when user is not near the bottom (accounts for day separators too).
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < (totalItems - 3).coerceAtLeast(0)
        }
    }
    val shouldShowScrollToBottomFab by remember {
        derivedStateOf {
            showScrollToBottomButton && state.inputText.isBlank()
        }
    }

    // Built once per messages snapshot so the initial-scroll LaunchedEffect and the
    // dispose handler can resolve message ids ↔︎ absolute LazyColumn indices without
    // duplicating work inside the column body. When a search is active we filter the
    // list down to messages whose text matches the query (case-insensitive); empty
    // day groups are dropped so the list stays compact.
    val visibleMessages = remember(state.messages, state.isSearchActive, state.searchQuery) {
        val q = state.searchQuery.trim()
        if (!state.isSearchActive || q.isEmpty()) {
            state.messages
        } else {
            state.messages.filter { msg ->
                msg.text?.contains(q, ignoreCase = true) == true ||
                    msg.transcriptText?.contains(q, ignoreCase = true) == true ||
                    msg.senderName.contains(q, ignoreCase = true)
            }
        }
    }
    val grouped = remember(visibleMessages) { visibleMessages.groupBy { it.dayLabel } }
    val flatItems = remember(grouped) {
        buildList {
            grouped.forEach { (label, messages) ->
                add(ChatListItem.Separator(label))
                messages.forEach { add(ChatListItem.Message(it)) }
            }
        }
    }
    // O(1) reply-context lookup. Without this every ChatBubble that has a replyToId does a
    // linear scan through all messages — O(n) per bubble, O(n²) total per frame on large histories.
    val messagesById = remember(state.messages) {
        state.messages.associateBy { it.id }
    }

    @SuppressLint("MissingPermission")
    fun startRecordingSafely() {
        val started = runCatching { recorderManager.start() }.getOrDefault(false)
        if (started) {
            inputBarViewModel.onRecordingStarted()
            proximityManager.setRecordingActive(true)
        } else {
            inputBarViewModel.onRecordingStopped()
            Toast.makeText(context, "Impossibile avviare la registrazione", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        proximityManager.start()
        onDispose {
            proximityManager.stop()
            recorderManager.stop(save = false)
            inputBarViewModel.onRecordingStopped()
        }
    }

    val flatItemsState = rememberUpdatedState(flatItems)
    val familyIdForAnchor = state.familyId
    DisposableEffect(familyIdForAnchor) {
        onDispose {
            // Persist the topmost visible message id so the next reopen restores
            // the same reading position. When the user is near the bottom we clear
            // the anchor — reopen falls back to bottom (the desired default).
            // We capture the familyId locally so a family switch saves under the
            // family the user was actually viewing, not the one we just switched to.
            val items = flatItemsState.value
            if (familyIdForAnchor.isBlank() || items.isEmpty()) {
                viewModel.saveScrollAnchorFor(familyIdForAnchor, null, 0)
                return@onDispose
            }
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearBottom = totalItems > 0 && lastVisible >= (totalItems - 3).coerceAtLeast(0)
            if (isNearBottom) {
                viewModel.saveScrollAnchorFor(familyIdForAnchor, null, 0)
                return@onDispose
            }
            val firstIndex = listState.firstVisibleItemIndex
            val anchorMessageId = generateSequence(firstIndex) { it + 1 }
                .takeWhile { it < items.size }
                .map { items[it] }
                .firstOrNull { it is ChatListItem.Message }
                ?.let { (it as ChatListItem.Message).message.id }
            viewModel.saveScrollAnchorFor(
                familyIdForAnchor,
                anchorMessageId,
                if (anchorMessageId != null) listState.firstVisibleItemScrollOffset else 0,
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                val location = getLastKnownLocation(locationClient)
                locationPickerLatLng = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    LatLng(41.9028, 12.4964) // fallback Roma
                }
                showLocationPicker = true
            }
        }
    }

    // Multi-select photo picker — stages items into the preview tray.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            pendingMedia = (pendingMedia + uris.take(10).map { it to false }).take(10)
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            scope.launch {
                val bytes = bitmapToJpegBytes(bmp)
                if (bytes != null) viewModel.sendMediaAttachment(bytes, isVideo = false)
            }
        }
    }
    // Multi-select video picker — stages items into the preview tray.
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            pendingMedia = (pendingMedia + uris.take(10).map { it to true }).take(10)
        }
    }
    // Combined photo+video multi-select — resolves MIME then stages into tray.
    val mediaGroupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val items = withContext(Dispatchers.IO) {
                    uris.take(10).map { uri ->
                        val mime = context.contentResolver.getType(uri) ?: ""
                        uri to mime.startsWith("video/")
                    }
                }
                pendingMedia = (pendingMedia + items).take(10)
            }
        }
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = readUriBytes(context, uri)
                if (bytes != null) {
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "documento"
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    viewModel.sendDocumentAttachment(fileName = fileName, mimeType = mime, bytes = bytes)
                }
            }
        }
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            scope.launch {
                val contact = readContact(context, uri)
                if (contact != null) {
                    viewModel.sendContactAttachment(
                        fullName = contact.first,
                        phone = contact.second,
                    )
                }
            }
        }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) contactPicker.launch(null)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            inputBarViewModel.onRecordingStopped()
            Toast.makeText(context, "Permesso microfono negato", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            inputBarViewModel.onRecordingStopped()
            Toast.makeText(context, "Permesso microfono negato", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        startRecordingSafely()
    }

    LaunchedEffect(state.messages.size, state.isLoadingOlder, state.familyId) {
        val currentCount = state.messages.size
        if (currentCount <= 0) {
            previousMessagesCount = 0
            return@LaunchedEffect
        }

        if (!didInitialBottomScroll) {
            // Always open at the bottom — the most recent messages are what the user expects
            // to see first. The LazyColumn is invisible (alpha 0) until this jump completes,
            // so there is no visual flash.
            listState.scrollToItem(flatItems.lastIndex.coerceAtLeast(0))
            didInitialBottomScroll = true
            previousMessagesCount = currentCount
            viewModel.markVisibleAsRead(state.messages.takeLast(20).map { it.id })
            return@LaunchedEffect
        }

        val countDelta = currentCount - previousMessagesCount
        if (countDelta > 0 && !state.isLoadingOlder) {
            val pendingIndex = pendingOlderAnchorIndex
            val pendingOffset = pendingOlderAnchorOffset
            if (pendingIndex != null && pendingOffset != null) {
                // Older messages loaded: restore position shifted by the new count.
                val target = (pendingIndex + countDelta)
                    .coerceAtMost(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
                listState.scrollToItem(target, pendingOffset)
                pendingOlderAnchorIndex = null
                pendingOlderAnchorOffset = null
            } else {
                // New message arrived (sent or received).
                // Use flatItems.lastIndex — the correct absolute index in the LazyColumn
                // (which includes day-separator items), not messages.lastIndex.
                val lastFlatIndex = flatItems.lastIndex.coerceAtLeast(0)

                // Always scroll to bottom for own messages (user just sent, they're already
                // at the bottom). For incoming messages scroll only when near the bottom.
                val isOwnMessage = state.messages.lastOrNull()?.senderId == state.currentUid
                if (isOwnMessage) {
                    // Instant jump — no animation lag between tap and seeing the sent bubble.
                    listState.scrollToItem(lastFlatIndex)
                } else {
                    // Incoming: scroll only when the user was already near the bottom so we
                    // don't yank them away from an older message they're reading.
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    // Use the *previous* totalItems - countDelta as reference because the
                    // layout may not have updated yet after the state change.
                    val wasNearBottom = totalItems <= 0 ||
                        lastVisible >= (totalItems - countDelta - 2).coerceAtLeast(0)
                    if (wasNearBottom) {
                        listState.scrollToItem(lastFlatIndex)
                    }
                }
            }
            viewModel.markVisibleAsRead(state.messages.takeLast(20).map { it.id })
        }
        previousMessagesCount = currentCount
    }
    // Use snapshotFlow instead of a LaunchedEffect with scroll-index as a key.
    // LaunchedEffect(listState.firstVisibleItemIndex, ...) would cancel+relaunch the
    // coroutine on every scroll step, which is wasteful. snapshotFlow coalesces rapid
    // scroll updates and only emits when the observed state has actually changed.
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                state.isLoadingOlder,
                state.messages.size,
            )
        }.collect { (firstIndex, isLoadingOlder, msgCount) ->
            if (firstIndex <= 3 && msgCount > 0) {
                if (!isLoadingOlder) {
                    pendingOlderAnchorIndex = listState.firstVisibleItemIndex
                    pendingOlderAnchorOffset = listState.firstVisibleItemScrollOffset
                }
                viewModel.loadOlderMessages()
            }
        }
    }
    LaunchedEffect(state.highlightedMessageId) {
        val targetId = state.highlightedMessageId ?: return@LaunchedEffect
        val idx = state.messages.indexOfFirst { it.id == targetId }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LaunchedEffect(Unit) {
        viewModel.reloadMessageSettings()
    }
    LaunchedEffect(Unit) {
        snapshotFlow { inputBarState.isRecording }.collectLatest { recording ->
            if (!recording) return@collectLatest
            while (currentCoroutineContext().isActive) {
                inputBarViewModel.onAmplitudeSample(recorderManager.currentAmplitude01())
                kotlinx.coroutines.delay(90)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Header(
                onBack = onBack,
                onSearchToggle = { viewModel.setSearchActive(!state.isSearchActive) },
                onClearChat = { showClearConfirm = true },
                isSearchActive = state.isSearchActive,
                searchQuery = state.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                matchCount = if (state.isSearchActive && state.searchQuery.isNotBlank()) visibleMessages.size else 0,
            )
            // ── List area + FAB — the FAB lives inside this Box so it is
            // geometrically constrained above the composer bar and can never overlap it.
            Box(modifier = Modifier.weight(1f)) {
                if (state.isLoading && state.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!state.errorText.isNullOrBlank()) {
                            Text(
                                text = state.errorText ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        LazyColumn(
                            state = listState,
                            // Stay invisible until the initial scroll position has been applied so
                            // the user doesn't see a brief flash at the top of the history before
                            // we jump to the saved anchor (or the bottom).
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (didInitialBottomScroll) 1f else 0f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (state.isLoadingOlder) {
                                item {
                                    LoadingOlderShimmer()
                                }
                            }
                            itemsIndexed(
                                items = flatItems,
                                key = { index, item ->
                                    when (item) {
                                        is ChatListItem.Separator -> "sep_${item.label}_$index"
                                        is ChatListItem.Message -> "msg_${item.message.id}"
                                    }
                                },
                            ) { _, item ->
                                when (item) {
                                    is ChatListItem.Separator -> DaySeparator(item.label)
                                    is ChatListItem.Message -> {
                                        // O(1) lookup via pre-built map instead of O(n) firstOrNull scan.
                                        val replied = item.message.replyToId?.let { messagesById[it] }
                                        ChatBubble(
                                            message = item.message,
                                            repliedTo = replied,
                                            currentUid = state.currentUid,
                                            onLongPress = { actionTarget = it },
                                            onReplyTap = { viewModel.startReply(it.id) },
                                            onReactionTap = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
                                            onReplyContextTap = { msgId -> viewModel.highlightMessage(msgId) },
                                            onMediaTap = { url, isVideo ->
                                                fullScreenMediaUrl = url
                                                fullScreenIsVideo = isVideo
                                            },
                                            onMediaGroupTap = { urls, types, startIndex ->
                                                mediaGroupGallery = Triple(urls, types, startIndex)
                                            },
                                            onAudioPlaybackStateChange = { active ->
                                                proximityManager.setPlaybackActive(active)
                                            },
                                            isHighlighted = state.highlightedMessageId == item.message.id,
                                            isTranscriptionEnabled = state.isAudioTranscriptionEnabled,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Scroll-to-bottom FAB — extracted to a private composable so
                // AnimatedVisibility is resolved without any ColumnScope in the implicit
                // receiver chain (calling it directly inside Column>Box triggers the
                // ColumnScope.AnimatedVisibility overload which the Compose compiler rejects).
                ScrollToBottomFab(
                    visible = shouldShowScrollToBottomFab,
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        scope.launch {
                            val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            if (target > 0) listState.scrollToItem(target)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                )
            }

            ReplyComposerBar(
                state = state,
                inputBarState = inputBarState,
                inputBarTimeLabel = inputBarViewModel.timeLabel(),
                inputBarWaveBars = inputBarViewModel.waveformBars(),
                pendingMedia = pendingMedia,
                isSendingPendingMedia = isSendingPendingMedia,
                onTextChange = viewModel::onInputTextChange,
                onSend = viewModel::sendText,
                onCancelReply = viewModel::cancelReply,
                onRemovePendingMedia = { idx -> pendingMedia = pendingMedia.toMutableList().also { it.removeAt(idx) } },
                onSendPendingMedia = {
                    val items = pendingMedia
                    if (items.isEmpty() || isSendingPendingMedia) return@ReplyComposerBar
                    // Show spinner in the tray immediately; don't clear pendingMedia yet so
                    // the tray stays visible during the I/O phase (reading bytes from URIs).
                    isSendingPendingMedia = true
                    scope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            items.mapNotNull { (uri, isVideo) ->
                                var bytes = readUriBytes(context, uri) ?: return@mapNotNull null
                                if (isVideo) bytes = VideoCompressor.compressIfNeeded(bytes, context)
                                bytes to isVideo
                            }
                        }
                        // Bytes are ready — dismiss the tray and hand off to ViewModel.
                        pendingMedia = emptyList()
                        isSendingPendingMedia = false
                        when {
                            loaded.isEmpty() -> Unit
                            loaded.size == 1 -> viewModel.sendMediaAttachment(loaded[0].first, isVideo = loaded[0].second)
                            else -> viewModel.sendMediaGroup(loaded)
                        }
                    }
                },
                onOpenAttachments = { showAttachmentSheet = true },
                onStartRecording = {
                    Log.w("KB_Transcription", "ui_start_recording_tap")
                    val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    if (permission != PackageManager.PERMISSION_GRANTED) {
                        Log.w("KB_Transcription", "ui_start_recording_permission_missing")
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@ReplyComposerBar
                    }
                    startRecordingSafely()
                },
                onStopRecording = {
                    Log.w("KB_Transcription", "ui_stop_recording_tap")
                    val recorded = recorderManager.stop(save = true)
                    proximityManager.setRecordingActive(false)
                    inputBarViewModel.onRecordingStopped()
                    if (recorded != null) {
                        scope.launch {
                            Log.w("KB_Transcription", "skip_send_side: transcription disabled for outgoing audio")
                            val bytes = withContext(Dispatchers.IO) { recorded.file.readBytes() }
                            viewModel.sendAudioAttachment(
                                fileName = recorded.file.name,
                                mimeType = recorded.mimeType,
                                bytes = bytes,
                                durationSeconds = recorded.durationSeconds,
                                transcriptText = null,
                            )
                            withContext(Dispatchers.IO) { recorded.file.delete() }
                        }
                    } else {
                        Log.w("KB_Transcription", "skip: recorder returned null on stop(save=true)")
                        Toast.makeText(
                            context,
                            "Registrazione non valida: impossibile trascrivere",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onCancelRecording = {
                    recorderManager.stop(save = false)
                    proximityManager.setRecordingActive(false)
                    inputBarViewModel.onRecordingStopped()
                },
                onLockRecording = {
                    inputBarViewModel.onLockRecording()
                },
                onPauseRecording = {
                    recorderManager.pause()
                    inputBarViewModel.onPauseRecording()
                },
                onResumeRecording = {
                    recorderManager.resume()
                    inputBarViewModel.onResumeRecording()
                },
                replyPreview = state.replyingToId?.let { id -> state.messages.firstOrNull { it.id == id } },
            )
        }
    }

    if (actionTarget != null) {
        val target = actionTarget!!
        val isSaveable = !target.isDeletedForEveryone &&
            target.mediaUrl != null &&
            target.type in setOf(
                ChatMessageType.PHOTO,
                ChatMessageType.VIDEO,
                ChatMessageType.AUDIO,
                ChatMessageType.DOCUMENT,
            )
        MessageActionDialog(
            message = target,
            canEdit = viewModel.canDeleteForEveryone(target),
            canDeleteForEveryone = viewModel.canDeleteForEveryone(target),
            onDismiss = { actionTarget = null },
            onPickReaction = { emoji ->
                viewModel.toggleReaction(target, emoji)
                actionTarget = null
            },
            onReply = {
                viewModel.startReply(target.id)
                actionTarget = null
            },
            onEdit = {
                editTarget = target
                editDraft = target.text.orEmpty()
                actionTarget = null
            },
            onSave = if (isSaveable) {
                {
                    val msg = target
                    scope.launch {
                        runCatching { saveMediaToDevice(context, msg) }
                            .onSuccess {
                                Toast.makeText(context, "Salvato", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "Salvataggio non riuscito", Toast.LENGTH_SHORT).show()
                            }
                    }
                    actionTarget = null
                }
            } else null,
            onDeleteForMe = {
                viewModel.deleteForMe(target.id)
                actionTarget = null
            },
            onDeleteForEveryone = {
                viewModel.deleteForEveryone(target.id)
                actionTarget = null
            },
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Svuota chat") },
            text = { Text("Vuoi davvero eliminare tutti i messaggi della chat? L'azione è irreversibile.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearChat()
                    },
                ) {
                    Text(
                        "Elimina tutti",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Annulla") }
            },
        )
    }
    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Modifica messaggio") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = editTarget?.id ?: return@TextButton
                        val newText = editDraft.trim()
                        if (newText.isNotBlank()) viewModel.updateMessageText(id, newText)
                        editTarget = null
                    },
                ) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Annulla") }
            },
        )
    }
    if (showAttachmentSheet) {
        ComposerAttachmentSheet(
            onDismiss = { showAttachmentSheet = false },
            onPickCamera = {
                showAttachmentSheet = false
                cameraPicker.launch(null)
            },
            onPickImage = {
                showAttachmentSheet = false
                imagePicker.launch("image/*")
            },
            onPickVideo = {
                showAttachmentSheet = false
                videoPicker.launch("video/*")
            },
            onPickMediaGroup = {
                showAttachmentSheet = false
                mediaGroupPicker.launch(arrayOf("image/*", "video/*"))
            },
            onPickDocument = {
                showAttachmentSheet = false
                docPicker.launch(arrayOf("*/*"))
            },
            onPickLocation = {
                showAttachmentSheet = false
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            onPickContact = {
                showAttachmentSheet = false
                val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                if (permission == PackageManager.PERMISSION_GRANTED) {
                    contactPicker.launch(null)
                } else {
                    contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
        )
    }
    if (fullScreenMediaUrl != null) {
        MediaFullscreenDialog(
            url = fullScreenMediaUrl ?: "",
            isVideo = fullScreenIsVideo,
            onDismiss = { fullScreenMediaUrl = null },
        )
    }
    mediaGroupGallery?.let { (urls, types, startIndex) ->
        MediaGroupGalleryDialog(
            urls = urls,
            types = types,
            initialIndex = startIndex,
            onDismiss = { mediaGroupGallery = null },
        )
    }
    if (showLocationPicker && locationPickerLatLng != null) {
        LocationPickerSheet(
            initialLatLng = locationPickerLatLng!!,
            onDismiss = { showLocationPicker = false },
            onConfirm = { lat, lon ->
                showLocationPicker = false
                viewModel.sendLocationAttachment(lat, lon)
            },
        )
    }
}

@Composable
private fun Header(
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onClearChat: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    matchCount: Int = 0,
) {
    val isDark = isSystemInDarkTheme()
    val pillBg = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    val subtitleColor = MaterialTheme.kidBoxColors.subtitle

    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back / close
        IconButton(onClick = if (isSearchActive) onSearchToggle else onBack) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Close
                              else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isSearchActive) "Chiudi ricerca" else "Indietro",
                tint = MaterialTheme.kidBoxColors.title,
            )
        }

        if (isSearchActive) {
            // ── Pill search field — takes remaining width between the close icon and edge
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(pillBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.kidBoxColors.title,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = subtitleColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Cerca nei messaggi…",
                                        color = subtitleColor,
                                        fontSize = 16.sp,
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "$matchCount",
                                    color = subtitleColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancella testo",
                                    tint = subtitleColor,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onSearchQueryChange("") },
                                )
                            }
                        }
                    },
                )
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        } else {
            // ── Normal title (centred) + menu ──────────────────────────────
            // Use a Box that fills the remaining width so the Text can sit at
            // true centre even when the back-button and menu have different widths.
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chat di Famiglia",
                    color = MaterialTheme.kidBoxColors.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Altre azioni",
                        tint = MaterialTheme.kidBoxColors.title,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Cerca") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onSearchToggle()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Svuota chat") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onClearChat()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.kidBoxColors.subtitle,
            modifier = Modifier
                .background(MaterialTheme.kidBoxColors.surfaceOverlay, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ReplyComposerBar(
    state: ChatUiState,
    inputBarState: ChatInputBarUiState,
    inputBarTimeLabel: String,
    inputBarWaveBars: List<Int>,
    pendingMedia: List<Pair<Uri, Boolean>>,
    isSendingPendingMedia: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    onRemovePendingMedia: (Int) -> Unit,
    onSendPendingMedia: () -> Unit,
    onOpenAttachments: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onLockRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    replyPreview: UiChatMessage?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.kidBoxColors.card)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        AnimatedVisibility(
            visible = state.typingUsers.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            Text(
                text = when (state.typingUsers.size) {
                    1 -> "${state.typingUsers.first()} sta scrivendo..."
                    2 -> "${state.typingUsers[0]} e ${state.typingUsers[1]} stanno scrivendo..."
                    else -> "Più persone stanno scrivendo..."
                },
                color = MaterialTheme.kidBoxColors.subtitle,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = replyPreview != null,
            enter = fadeIn(tween(160)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(120)) + slideOutVertically(targetOffsetY = { it }),
        ) {
            if (replyPreview != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.kidBoxColors.card.copy(alpha = 0.6f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Accent bar (iOS-style)
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(52.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))

                    // Thumbnail / icon for media types
                    val thumbSize = 42.dp
                    when (replyPreview.type) {
                        ChatMessageType.PHOTO -> {
                            val url = replyPreview.mediaUrl
                            if (url != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(url)
                                        .memoryCacheKey("msg_${replyPreview.id}")
                                        .diskCacheKey("msg_${replyPreview.id}")
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(thumbSize)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        ChatMessageType.VIDEO -> {
                            val url = replyPreview.mediaUrl
                            if (url != null) {
                                var videoBitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
                                LaunchedEffect(url) {
                                    videoBitmap = VideoThumbnailLoader.load(url, "reply_${replyPreview.id}")
                                }
                                val bmp = videoBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(thumbSize)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(thumbSize)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.VideoFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.kidBoxColors.subtitle,
                                        )
                                    }
                                }
                            }
                        }
                        ChatMessageType.AUDIO ->
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(thumbSize),
                            )
                        ChatMessageType.DOCUMENT ->
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(thumbSize),
                            )
                        ChatMessageType.LOCATION ->
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(thumbSize),
                            )
                        ChatMessageType.CONTACT ->
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(thumbSize),
                            )
                        else -> Spacer(Modifier.width(4.dp))
                    }

                    Spacer(Modifier.width(8.dp))

                    // Sender + preview text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = replyPreview.senderName,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = replyPreview.previewText(),
                            color = MaterialTheme.kidBoxColors.subtitle,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }

                    // Close button
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Annulla risposta",
                            tint = MaterialTheme.kidBoxColors.subtitle,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ── Media preview tray ─────────────────────────────────────────────
        // Stays visible while items are staged OR while bytes are being read
        // (isSendingPendingMedia), so the spinner is always shown during the I/O gap.
        AnimatedVisibility(
            visible = pendingMedia.isNotEmpty() || isSendingPendingMedia,
            enter = fadeIn(tween(160)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(120)) + slideOutVertically(targetOffsetY = { it }),
        ) {
            MediaPreviewTray(
                items = pendingMedia,
                onRemove = onRemovePendingMedia,
                onSend = onSendPendingMedia,
                isSending = state.isSending || isSendingPendingMedia,
            )
        }

        ChatInputBar(
            text = state.inputText,
            isSending = state.isSending,
            recordingState = inputBarState,
            recordingTimeLabel = inputBarTimeLabel,
            recordingWaveformBars = inputBarWaveBars,
            onTextChange = onTextChange,
            onOpenAttachments = onOpenAttachments,
            onSendText = onSend,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onCancelRecording = onCancelRecording,
            onLockRecording = onLockRecording,
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
        )
    }
}

@Composable
private fun ComposerAttachmentSheet(
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickMediaGroup: () -> Unit,
    onPickDocument: () -> Unit,
    onPickLocation: () -> Unit,
    onPickContact: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Allega", color = MaterialTheme.kidBoxColors.title, fontWeight = FontWeight.SemiBold)
            ActionSheetRow(
                icon = Icons.Default.PhotoCamera,
                text = "Fotocamera",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickCamera,
            )
            ActionSheetRow(
                icon = Icons.Default.Image,
                text = "Foto (max 10)",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickImage,
            )
            ActionSheetRow(
                icon = Icons.Default.VideoFile,
                text = "Video (max 10)",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickVideo,
            )
            ActionSheetRow(
                icon = Icons.Default.Image,
                text = "Foto e Video (max 10)",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickMediaGroup,
            )
            ActionSheetRow(
                icon = Icons.Default.AttachFile,
                text = "Documento",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickDocument,
            )
            ActionSheetRow(
                icon = Icons.Default.Place,
                text = "Posizione",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickLocation,
            )
            ActionSheetRow(
                icon = Icons.Default.Person,
                text = "Contatto",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickContact,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MessageActionDialog(
    message: UiChatMessage,
    canEdit: Boolean,
    canDeleteForEveryone: Boolean,
    onDismiss: () -> Unit,
    onPickReaction: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onSave: (() -> Unit)?,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dividerColor = MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.15f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp),
        ) {
            // ── Emoji reactions ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                emojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable { onPickReaction(emoji) },
                    )
                }
            }

            HorizontalDivider(color = dividerColor)
            Spacer(Modifier.height(6.dp))

            // ── Azioni principali ────────────────────────────────────────────
            // Rispondi — per tutti i tipi non eliminati
            if (!message.isDeletedForEveryone) {
                ActionSheetRow(
                    icon = Icons.Default.Reply,
                    text = "Rispondi",
                    textColor = MaterialTheme.kidBoxColors.title,
                    onClick = onReply,
                )
            }

            // Modifica — solo testo, solo l'autore, entro 5 min
            if (canEdit && message.type == ChatMessageType.TEXT) {
                ActionSheetRow(
                    icon = Icons.Default.ModeEdit,
                    text = "Modifica",
                    textColor = MaterialTheme.colorScheme.primary,
                    onClick = onEdit,
                )
            }

            // Salva — foto, video, audio, documento con media disponibile
            if (onSave != null) {
                val saveLabel = when (message.type) {
                    ChatMessageType.PHOTO -> "Salva foto"
                    ChatMessageType.VIDEO -> "Salva video"
                    ChatMessageType.AUDIO -> "Salva audio"
                    ChatMessageType.DOCUMENT -> "Salva documento"
                    else -> "Salva"
                }
                ActionSheetRow(
                    icon = Icons.Default.SaveAlt,
                    text = saveLabel,
                    textColor = MaterialTheme.kidBoxColors.title,
                    onClick = onSave,
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = dividerColor)
            Spacer(Modifier.height(6.dp))

            // ── Elimina ──────────────────────────────────────────────────────
            ActionSheetRow(
                icon = Icons.Default.DeleteOutline,
                text = "Elimina per me",
                textColor = MaterialTheme.colorScheme.error,
                onClick = onDeleteForMe,
            )
            if (canDeleteForEveryone) {
                ActionSheetRow(
                    icon = Icons.Default.DeleteOutline,
                    text = "Elimina per tutti",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = onDeleteForEveryone,
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = dividerColor)

            // ── Chiudi ───────────────────────────────────────────────────────
            Text(
                text = "Chiudi",
                color = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

// Extracted so AnimatedVisibility is resolved in a clean @Composable context —
// no ColumnScope in the implicit receiver chain that would trigger the
// ColumnScope.AnimatedVisibility overload and break compilation.
@Composable
private fun ScrollToBottomFab(
    visible: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.86f),
        exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.86f),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isDarkTheme) Color(0xFF2A2A2A).copy(alpha = 0.72f)
                    else Color.White.copy(alpha = 0.88f),
                ),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Vai in fondo",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.kidBoxColors.title.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun LoadingOlderShimmer() {
    val transition = rememberInfiniteTransition(label = "chat_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chat_shimmer_alpha",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(12.dp)
                .alpha(alpha)
                .background(MaterialTheme.kidBoxColors.title.copy(alpha = 0.12f), RoundedCornerShape(999.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(10.dp)
                .alpha(alpha)
                .background(MaterialTheme.kidBoxColors.title.copy(alpha = 0.09f), RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun MediaGroupGalleryDialog(
    urls: List<String>,
    types: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0)),
        pageCount = { urls.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Force the dialog window to fill the entire screen (height + width).
        // DialogProperties(usePlatformDefaultWidth = false) only removes the width cap;
        // the platform still imposes a max-height. This SideEffect must live INSIDE
        // the Dialog content so that LocalView.current returns the dialog's own view
        // (whose parent is a DialogWindowProvider), not the activity's root view.
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { it },
            ) { page ->
                val url = urls.getOrNull(page) ?: return@HorizontalPager
                val isVideo = types.getOrNull(page) == "video"

                // Reset playback state whenever the user swipes away from this page.
                var isPlaying by remember { mutableStateOf(false) }
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != page) isPlaying = false
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isVideo) {
                        if (isPlaying) {
                            // ── Active player ────────────────────────────────
                            AndroidView(
                                factory = { ctx -> VideoView(ctx) },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    if (view.tag != url) {
                                        view.tag = url
                                        view.setVideoURI(Uri.parse(url))
                                        view.setOnPreparedListener { mp ->
                                            mp.isLooping = false
                                            view.start()
                                        }
                                    }
                                },
                            )
                        } else {
                            // ── Thumbnail + play button ───────────────────────
                            val thumbnail by produceState<android.graphics.Bitmap?>(
                                initialValue = null,
                                key1 = url,
                            ) {
                                value = VideoThumbnailLoader.load(url, cacheKey = "gallery_vid_$page")
                            }
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail!!.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            // Play button — centered over thumbnail (or black bg while loading)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { isPlaying = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Riproduci",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Page counter pill — "2 / 6"
            if (urls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 8.dp, top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Chiudi",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MediaFullscreenDialog(
    url: String,
    isVideo: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (isVideo) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx)
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (view.tag != url) {
                            view.tag = url
                            view.setVideoURI(Uri.parse(url))
                            view.setOnPreparedListener {
                                it.isLooping = true
                                view.start()
                            }
                        }
                    },
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * iOS-style horizontal tray showing thumbnails of media selected from the gallery,
 * letting the user remove individual items before hitting send.
 */
@Composable
private fun MediaPreviewTray(
    items: List<Pair<Uri, Boolean>>,
    onRemove: (Int) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.kidBoxColors.card)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            itemsIndexed(items, key = { i, _ -> i }) { index, (uri, isVideo) ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.kidBoxColors.rowBackground),
                ) {
                    // ── Thumbnail ────────────────────────────────────────
                    if (isVideo) {
                        val thumb by produceState<android.graphics.Bitmap?>(null, uri.toString()) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    val retriever = MediaMetadataRetriever()
                                    retriever.setDataSource(context, uri)
                                    val frame = retriever.getFrameAtTime(
                                        0,
                                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    )?.let { fixVideoFrameOrientation(it, retriever) }
                                    retriever.release()
                                    frame
                                }.getOrNull()
                            }
                        }
                        if (thumb != null) {
                            Image(
                                bitmap = thumb!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        // Video badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .size(18.dp)
                                .background(Color.Black.copy(alpha = 0.50f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    } else {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    // ── ✕ remove button ──────────────────────────────────
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .clickable { onRemove(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Rimuovi",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        // ── Send button ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF6B00))
                .clickable(enabled = !isSending, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Invia",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "${items.size}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = textColor)
        Text(text = text, color = textColor)
    }
}

@Composable
private fun LocationPickerSheet(
    initialLatLng: LatLng,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var marker by remember(initialLatLng) { mutableStateOf(initialLatLng) }
    val cameraState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(initialLatLng, 15f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Scegli posizione",
                color = MaterialTheme.kidBoxColors.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp)),
                cameraPositionState = cameraState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                ),
                onMapClick = { latLng ->
                    marker = latLng
                },
            ) {
                Marker(
                    state = MarkerState(position = marker),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
                TextButton(onClick = { onConfirm(marker.latitude, marker.longitude) }) { Text("Invia") }
            }
            Spacer(Modifier.height(18.dp))
        }
}
}

private suspend fun readUriBytes(
    context: android.content.Context,
    uri: Uri,
): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        }
    }.getOrNull()
}

@SuppressLint("MissingPermission")
private suspend fun getLastKnownLocation(
    client: FusedLocationProviderClient,
): android.location.Location? = withContext(Dispatchers.IO) {
    runCatching { client.lastLocation.await() }.getOrNull()
}

private suspend fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
    }.getOrNull()
}

private suspend fun readContact(
    context: android.content.Context,
    uri: Uri,
): Pair<String, String>? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        var name = ""
        var phone = ""
        resolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val contactId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                if (contactId.isNotBlank()) {
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null,
                    )?.use { p ->
                        if (p.moveToFirst()) {
                            val numIdx = p.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            phone = if (numIdx >= 0) p.getString(numIdx).orEmpty() else ""
                        }
                    }
                }
            }
        }
        if (name.isBlank()) null else name to phone
    }.getOrNull()
}

/**
 * Salva il media del messaggio nella raccolta pubblica del dispositivo.
 *
 * - PHOTO  → Galleria (MediaStore Images / DCIM/KidBox)
 * - VIDEO  → Film     (MediaStore Video / Movies/KidBox)
 * - AUDIO  → Musica   (MediaStore Audio / Music/KidBox)
 * - DOCUMENT → Download (MediaStore Downloads / Download/KidBox)
 *
 * Supporta sia URL remoti (https://) sia URI locali (file://).
 */
private suspend fun saveMediaToDevice(
    context: android.content.Context,
    message: UiChatMessage,
) = withContext(Dispatchers.IO) {
    val url = message.mediaUrl ?: error("Nessun media disponibile")

    // Legge i byte — preferisce il file locale già in cache.
    val bytes: ByteArray = if (url.startsWith("file://")) {
        java.io.File(Uri.parse(url).path!!).readBytes()
    } else {
        java.net.URL(url).openStream().use { it.readBytes() }
    }

    val ts = System.currentTimeMillis()
    val resolver = context.contentResolver

    when (message.type) {
        ChatMessageType.PHOTO -> {
            val name = "KidBox_$ts.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KidBox")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
        ChatMessageType.VIDEO -> {
            val name = "KidBox_$ts.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/KidBox")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
        ChatMessageType.AUDIO -> {
            val name = "KidBox_audio_$ts.m4a"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/KidBox")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
                resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    .also { it.mkdirs() }
                java.io.File(dir, name).writeBytes(bytes)
            }
        }
        ChatMessageType.DOCUMENT -> {
            // Cerca di mantenere il nome file originale dall'URL.
            val rawName = url.substringAfterLast('/').takeIf { it.isNotBlank() && it.length <= 200 }
                ?: "documento_$ts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, rawName)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KidBox")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .also { it.mkdirs() }
                java.io.File(dir, rawName).writeBytes(bytes)
            }
        }
        else -> {}
    }
}

private sealed class ChatListItem {
    data class Separator(val label: String) : ChatListItem()
    data class Message(val message: UiChatMessage) : ChatListItem()
}
