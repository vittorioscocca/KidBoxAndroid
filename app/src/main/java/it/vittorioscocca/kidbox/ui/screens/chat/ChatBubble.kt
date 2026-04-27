@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
internal fun ChatBubble(
    message: UiChatMessage,
    repliedTo: UiChatMessage?,
    currentUid: String,
    onLongPress: (UiChatMessage) -> Unit,
    onReplyTap: (UiChatMessage) -> Unit,
    onReactionTap: (UiChatMessage, String) -> Unit,
    onReplyContextTap: (String) -> Unit,
    onMediaTap: (url: String, isVideo: Boolean) -> Unit,
    onAudioPlaybackStateChange: (Boolean) -> Unit,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val isOwn = message.senderId == currentUid
    val bubbleColor = messageBubbleColor(isOwn)
    val textColor = if (isOwn) Color.White else MaterialTheme.kidBoxColors.title
    val subtitleColor = if (isOwn) Color.White.copy(alpha = 0.74f) else MaterialTheme.kidBoxColors.subtitle
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.78f).coerceAtMost(380.dp)
    val bubbleShape = RoundedCornerShape(
        topStart = if (isOwn) 18.dp else 6.dp,
        topEnd = if (isOwn) 6.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp,
    )
    val highlightColor = if (isOwn) Color.White.copy(alpha = 0.12f) else Color(0x12FF6B00)
    val dragOffsetX = remember(message.id) { Animatable(0f) }
    val uiScope = rememberCoroutineScope()
    val swipeThresholdPx = 86f
    val replyHintAlpha = (abs(dragOffsetX.value) / swipeThresholdPx).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (isOwn) "Tu" else message.senderName,
            color = MaterialTheme.kidBoxColors.subtitle,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        ) {
            if (!isOwn) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = replyHintAlpha),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                    .widthIn(max = maxBubbleWidth)
                    .clip(bubbleShape)
                    .background(if (isHighlighted) highlightColor else bubbleColor)
                    .pointerInput(message.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val shouldReply = if (isOwn) {
                                    dragOffsetX.value <= -swipeThresholdPx
                                } else {
                                    dragOffsetX.value >= swipeThresholdPx
                                }
                                uiScope.launch {
                                    if (shouldReply) {
                                        onReplyTap(message)
                                        val snapDirection = if (isOwn) -1f else 1f
                                        dragOffsetX.animateTo(
                                            targetValue = snapDirection * 28f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        )
                                    }
                                    dragOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val next = dragOffsetX.value + dragAmount
                                val bounded = if (isOwn) {
                                    next.coerceIn(-120f, 0f)
                                } else {
                                    next.coerceIn(0f, 120f)
                                }
                                uiScope.launch {
                                    dragOffsetX.snapTo(bounded)
                                }
                            },
                        )
                    }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress(message) },
                    )
                    .padding(10.dp),
            ) {
                if (message.replyToId != null) {
                    ReplyContextHeader(
                        repliedTo = repliedTo,
                        isOwn = isOwn,
                        onTap = { message.replyToId?.let(onReplyContextTap) },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (message.isDeletedForEveryone) {
                    Text(
                        text = "Messaggio eliminato",
                        color = subtitleColor,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontSize = 14.sp,
                    )
                } else {
                    when (message.type) {
                        ChatMessageType.TEXT -> TextContent(message, textColor)
                        ChatMessageType.PHOTO -> MediaContent(message, isVideo = false, onMediaTap = onMediaTap)
                        ChatMessageType.VIDEO -> MediaContent(message, isVideo = true, onMediaTap = onMediaTap)
                        ChatMessageType.MEDIA_GROUP -> MediaGroupContent(message, onMediaTap = onMediaTap)
                        ChatMessageType.LOCATION -> LocationContent(message)
                        ChatMessageType.CONTACT -> ContactContent(message, textColor, subtitleColor)
                        ChatMessageType.DOCUMENT -> DocumentContent(message, textColor, subtitleColor)
                        ChatMessageType.AUDIO -> AudioContent(
                            message = message,
                            textColor = textColor,
                            subtitleColor = subtitleColor,
                            onAudioPlaybackStateChange = onAudioPlaybackStateChange,
                            isOwn = isOwn,
                        )
                    }
                }

                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (message.editedAtMillis != null) {
                        Text("Modificato", fontSize = 10.sp, color = subtitleColor)
                    }
                    Text(message.timeLabel, fontSize = 10.sp, color = subtitleColor)
                    if (isOwn) {
                        val read = message.readBy.any { it != currentUid }
                        Text(if (read) "✓✓" else "✓", fontSize = 11.sp, color = subtitleColor)
                    }
                }
            }
            if (isOwn) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = replyHintAlpha),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .size(18.dp),
                )
            }
        }

        if (message.reactions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                message.reactions.entries.sortedBy { it.key }.forEach { (emoji, users) ->
                    Text(
                        text = if (users.size > 1) "$emoji ${users.size}" else emoji,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x15000000))
                            .combinedClickable(
                                onClick = { onReactionTap(message, emoji) },
                                onLongClick = { onLongPress(message) },
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.kidBoxColors.title,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyContextHeader(
    repliedTo: UiChatMessage?,
    isOwn: Boolean,
    onTap: () -> Unit,
) {
    val accent = if (isOwn) Color.White.copy(alpha = 0.8f) else Color(0xFFFF6B00)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isOwn) Color.White.copy(alpha = 0.16f) else Color(0x11FF6B00))
            .combinedClickable(onClick = onTap, onLongClick = onTap)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = repliedTo?.senderName ?: "Messaggio",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = repliedTo?.previewText() ?: "Messaggio non disponibile",
                color = if (isOwn) Color.White.copy(alpha = 0.78f) else Color(0xFF5D6470),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TextContent(message: UiChatMessage, textColor: Color) {
    Text(
        text = message.text.orEmpty(),
        color = textColor,
        fontSize = 15.sp,
    )
}

@Composable
private fun MediaContent(
    message: UiChatMessage,
    isVideo: Boolean,
    onMediaTap: (url: String, isVideo: Boolean) -> Unit,
) {
    val mediaUrl = message.mediaUrl
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaUrl != null) {
            if (isVideo) {
                val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = mediaUrl) {
                    value = VideoThumbnailLoader.load(mediaUrl)
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = mediaUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp),
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaUrl)
                        .memoryCacheKey(mediaUrl)
                        .diskCacheKey(mediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { onMediaTap(mediaUrl, isVideo) },
                        onLongClick = {},
                    ),
            )
        }
    }
}

@Composable
private fun MediaGroupContent(
    message: UiChatMessage,
    onMediaTap: (url: String, isVideo: Boolean) -> Unit,
) {
    val visibleCount = minOf(10, message.mediaGroupUrls.size)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        message.mediaGroupUrls.take(visibleCount).chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { colIndex, url ->
                    val absoluteIndex = rowIndex * 3 + colIndex
                    val isVideo = message.mediaGroupTypes.getOrNull(absoluteIndex) == "video"
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x12000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .memoryCacheKey(url)
                                .diskCacheKey(url)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { onMediaTap(url, isVideo) },
                                    onLongClick = {},
                                ),
                        )
                        if (isVideo) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationContent(message: UiChatMessage) {
    val context = LocalContext.current
    val lat = message.latitude
    val lon = message.longitude
    if (lat == null || lon == null) return
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 160.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                mapToolbarEnabled = false,
            ),
            properties = MapProperties(),
            onMapClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:$lat,$lon?q=$lat,$lon(Posizione)"),
                )
                context.startActivity(intent)
            },
        ) {
            Marker(state = MarkerState(position = LatLng(lat, lon)))
        }
    }
}

@Composable
private fun ContactContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
) {
    val payload = message.contactPayload
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x19000000))
            .padding(10.dp),
    ) {
        Text(
            text = payload?.fullName ?: "Contatto",
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Text(
            text = payload?.primaryPhone ?: "Numero non disponibile",
            color = subtitleColor,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DocumentContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x17000000))
            .padding(10.dp),
    ) {
        Text(
            text = message.text ?: "Documento",
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        message.mediaFileSize?.let {
            Text(
                text = android.text.format.Formatter.formatShortFileSize(LocalContext.current, it),
                color = subtitleColor,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun AudioContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
    onAudioPlaybackStateChange: (Boolean) -> Unit,
    isOwn: Boolean,
) {
    val context = LocalContext.current
    val mediaUrl = message.mediaUrl
    var isPlaying by remember(mediaUrl) { mutableStateOf(false) }
    var durationMs by remember(mediaUrl) { mutableStateOf((message.mediaDurationSeconds ?: 0) * 1000) }
    var currentMs by remember(mediaUrl) { mutableStateOf(0) }
    var isPrepared by remember(mediaUrl) { mutableStateOf(false) }
    var speedIndex by remember(mediaUrl) { mutableStateOf(0) }
    val speedValues = remember { listOf(1.0f, 1.5f, 2.0f) }
    val speed = speedValues[speedIndex]
    val player = remember(mediaUrl) {
        if (mediaUrl.isNullOrBlank()) null else MediaPlayer().apply {
            runCatching {
                val uri = mediaUrl.toUri()
                when (uri.scheme?.lowercase()) {
                    "http", "https" -> setDataSource(mediaUrl)
                    else -> setDataSource(context, uri)
                }
                prepareAsync()
            }
            setOnPreparedListener {
                isPrepared = true
                if (it.duration > 0) durationMs = it.duration
            }
            setOnCompletionListener {
                isPlaying = false
                currentMs = durationMs
                onAudioPlaybackStateChange(false)
            }
        }
    }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying && player != null) {
            currentMs = runCatching { player.currentPosition }.getOrDefault(currentMs)
            kotlinx.coroutines.delay(120)
        }
    }
    DisposableEffect(mediaUrl) {
        onDispose {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            onAudioPlaybackStateChange(false)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = {
                if (player == null) return@IconButton
                if (isPlaying) {
                    runCatching { player.pause() }
                    isPlaying = false
                    onAudioPlaybackStateChange(false)
                } else {
                    if (currentMs >= durationMs && durationMs > 0) {
                        runCatching { player.seekTo(0) }
                        currentMs = 0
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && isPrepared) {
                        runCatching {
                            val params = player.playbackParams
                            player.playbackParams = params.setSpeed(speed)
                        }
                    }
                    runCatching { player.start() }
                    isPlaying = true
                    onAudioPlaybackStateChange(true)
                }
            },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = textColor,
            )
        }
        AdaptiveRecordingWaveformView(
            samples = List(14) { idx -> if (idx % 2 == 0) 8 else 14 },
            color = textColor.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "${speed}x".replace(".0x", "x"),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isOwn) Color.White.copy(alpha = 0.24f) else Color(0xFFDAECFF))
                .border(
                    width = 1.dp,
                    color = if (isOwn) Color.White.copy(alpha = 0.12f) else Color(0x22000000),
                    shape = RoundedCornerShape(999.dp),
                )
                .combinedClickable(
                    onClick = {
                        speedIndex = (speedIndex + 1) % speedValues.size
                        val nextSpeed = speedValues[speedIndex]
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && isPrepared) {
                            runCatching {
                                val params = player?.playbackParams ?: return@runCatching
                                player.playbackParams = params.setSpeed(nextSpeed)
                            }
                        }
                    },
                    onLongClick = {},
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = formatAudioTime(durationMs),
        color = subtitleColor,
        fontSize = 11.sp,
    )
    val hasTranscript = !message.transcriptText.isNullOrBlank() || !message.text.isNullOrBlank()
    if (!isOwn || hasTranscript) {
        val transcriptionText = (message.transcriptText ?: message.text).orEmpty().trim()
            .ifBlank { "Trascrizione non disponibile" }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = subtitleColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Trascrizione automatica",
                color = subtitleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = transcriptionText,
            color = if (isOwn) Color.White.copy(alpha = 0.92f) else MaterialTheme.kidBoxColors.title,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

private fun formatAudioTime(ms: Int): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val mm = (sec / 60).toString().padStart(2, '0')
    val ss = (sec % 60).toString().padStart(2, '0')
    return "$mm:$ss"
}
