@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.VideoView
import android.widget.Toast
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.collectLatest
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
    var showAttachmentSheet by remember { mutableStateOf(false) }
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
    val showScrollToBottomButton by remember(state.messages.size, listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size) {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf false
            // Show when user is not near the bottom (accounts for day separators too).
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < (totalItems - 3).coerceAtLeast(0)
        }
    }
    val shouldShowScrollToBottomFab by remember(showScrollToBottomButton, state.inputText) {
        derivedStateOf {
            showScrollToBottomButton && state.inputText.isBlank()
        }
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = readUriBytes(context, uri)
                if (bytes != null) viewModel.sendMediaAttachment(bytes, isVideo = false)
            }
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
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = readUriBytes(context, uri)
                if (bytes != null) viewModel.sendMediaAttachment(bytes, isVideo = true)
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

    LaunchedEffect(state.messages.size, state.isLoadingOlder) {
        val currentCount = state.messages.size
        if (currentCount <= 0) {
            previousMessagesCount = 0
            return@LaunchedEffect
        }

        if (!didInitialBottomScroll) {
            listState.scrollToItem(currentCount - 1)
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
                val target = (pendingIndex + countDelta).coerceAtMost(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
                listState.scrollToItem(target, pendingOffset)
                pendingOlderAnchorIndex = null
                pendingOlderAnchorOffset = null
            } else {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val isNearBottom = totalItems <= 0 || lastVisible >= (totalItems - 4).coerceAtLeast(0)
                if (isNearBottom) {
                    listState.animateScrollToItem(currentCount - 1)
                }
            }
            viewModel.markVisibleAsRead(state.messages.takeLast(20).map { it.id })
        }
        previousMessagesCount = currentCount
    }
    LaunchedEffect(listState.firstVisibleItemIndex, state.messages.size) {
        if (listState.firstVisibleItemIndex <= 3 && state.messages.isNotEmpty()) {
            if (!state.isLoadingOlder) {
                pendingOlderAnchorIndex = listState.firstVisibleItemIndex
                pendingOlderAnchorOffset = listState.firstVisibleItemScrollOffset
            }
            viewModel.loadOlderMessages()
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
            .background(MaterialTheme.kidBoxColors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Header(
                onBack = onBack,
            )
            if (state.isLoading && state.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (!state.errorText.isNullOrBlank()) {
                Text(
                    text = state.errorText ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            val grouped = remember(state.messages) { state.messages.groupBy { it.dayLabel } }
            val flatItems = remember(grouped) {
                buildList {
                    grouped.forEach { (label, messages) ->
                        add(ChatListItem.Separator(label))
                        messages.forEach { add(ChatListItem.Message(it)) }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            val replied = item.message.replyToId?.let { replyId ->
                                state.messages.firstOrNull { it.id == replyId }
                            }
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
                                onAudioPlaybackStateChange = { active ->
                                    proximityManager.setPlaybackActive(active)
                                },
                                isHighlighted = state.highlightedMessageId == item.message.id,
                            )
                        }
                    }
                }
            }

            ReplyComposerBar(
                state = state,
                inputBarState = inputBarState,
                inputBarTimeLabel = inputBarViewModel.timeLabel(),
                inputBarWaveBars = inputBarViewModel.waveformBars(),
                onTextChange = viewModel::onInputTextChange,
                onSend = viewModel::sendText,
                onCancelReply = viewModel::cancelReply,
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
        AnimatedVisibility(
            visible = shouldShowScrollToBottomFab,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 136.dp),
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.86f),
            exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.86f),
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (state.messages.isNotEmpty()) {
                            listState.animateScrollToItem(state.messages.lastIndex)
                        }
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDarkTheme) {
                            Color(0xFF2A2A2A).copy(alpha = 0.72f)
                        } else {
                            Color.White.copy(alpha = 0.78f)
                        },
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

    if (actionTarget != null) {
        MessageActionDialog(
            message = actionTarget!!,
            canEdit = viewModel.canDeleteForEveryone(actionTarget!!),
            canDeleteForEveryone = viewModel.canDeleteForEveryone(actionTarget!!),
            onDismiss = { actionTarget = null },
            onPickReaction = { emoji ->
                actionTarget?.let { viewModel.toggleReaction(it, emoji) }
                actionTarget = null
            },
            onEdit = {
                val target = actionTarget ?: return@MessageActionDialog
                editTarget = target
                editDraft = target.text.orEmpty()
                actionTarget = null
            },
            onDeleteForMe = {
                actionTarget?.let { viewModel.deleteForMe(it.id) }
                actionTarget = null
            },
            onDeleteForEveryone = {
                actionTarget?.let { viewModel.deleteForEveryone(it.id) }
                actionTarget = null
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
        Text(
            text = "Chat famiglia",
            color = MaterialTheme.kidBoxColors.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
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
                .background(Color(0x13000000), RoundedCornerShape(999.dp))
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
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x12000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (replyPreview != null) {
                        "Risposta a ${replyPreview.senderName}: ${replyPreview.previewText()}"
                    } else {
                        ""
                    },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.kidBoxColors.subtitle,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                TextButton(onClick = onCancelReply) { Text("Annulla") }
            }
            Spacer(Modifier.height(8.dp))
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
                text = "Foto",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickImage,
            )
            ActionSheetRow(
                icon = Icons.Default.VideoFile,
                text = "Video",
                textColor = MaterialTheme.kidBoxColors.title,
                onClick = onPickVideo,
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
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
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
            Text("Azioni messaggio", color = MaterialTheme.kidBoxColors.title, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                emojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 30.sp,
                        modifier = Modifier.padding(2.dp).clickable { onPickReaction(emoji) },
                    )
                }
            }
            if (canEdit && message.type == it.vittorioscocca.kidbox.data.chat.model.ChatMessageType.TEXT) {
                ActionSheetRow(
                    icon = Icons.Default.ModeEdit,
                    text = "Modifica",
                    textColor = MaterialTheme.colorScheme.primary,
                    onClick = onEdit,
                )
            }
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
            Text(
                text = "Chiudi",
                color = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
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
                .background(Color(0x22000000), RoundedCornerShape(999.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(10.dp)
                .alpha(alpha)
                .background(Color(0x1A000000), RoundedCornerShape(999.dp)),
        )
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
                coil.compose.AsyncImage(
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

private sealed class ChatListItem {
    data class Separator(val label: String) : ChatListItem()
    data class Message(val message: UiChatMessage) : ChatListItem()
}
