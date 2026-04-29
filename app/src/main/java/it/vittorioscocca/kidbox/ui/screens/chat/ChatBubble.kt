@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.text.format.Formatter
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onMediaGroupTap: (urls: List<String>, types: List<String>, startIndex: Int) -> Unit,
    onAudioPlaybackStateChange: (Boolean) -> Unit,
    isHighlighted: Boolean,
    isTranscriptionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isOwn = message.senderId == currentUid
    val bubbleColor = messageBubbleColor(isOwn)
    val textColor = if (isOwn) Color.White else MaterialTheme.kidBoxColors.title
    val subtitleColor = if (isOwn) Color.White.copy(alpha = 0.74f) else MaterialTheme.kidBoxColors.subtitle
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.80f).coerceAtMost(360.dp)
    val maxMediaWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.65f).coerceAtMost(260.dp)
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
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
                        .padding(horizontal = 2.dp, vertical = 12.dp)
                        .size(18.dp),
                )
            }
            // Photo / video / mediaGroup / location without a reply context render
            // edge-to-edge — the media IS the bubble, no surrounding background or
            // padding (parity with iOS).
            val isMediaOnly = !message.isDeletedForEveryone &&
                message.replyToId == null &&
                (message.type == ChatMessageType.PHOTO ||
                    message.type == ChatMessageType.VIDEO ||
                    message.type == ChatMessageType.MEDIA_GROUP ||
                    message.type == ChatMessageType.LOCATION)

            val gestureModifier = Modifier
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                // Media-only bubbles use a narrower fixed width (65 % of screen, max
                // 260 dp) so photos/videos/maps don't dominate the thread visually.
                // Text bubbles keep the wider max so long messages still wrap nicely.
                .let { if (isMediaOnly) it.width(maxMediaWidth) else it.widthIn(max = maxBubbleWidth) }
                .clip(bubbleShape)
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

            if (isMediaOnly) {
                Box(
                    modifier = gestureModifier
                        .background(if (isHighlighted) highlightColor else Color.Transparent),
                ) {
                    when (message.type) {
                        ChatMessageType.PHOTO -> MediaContent(message, isVideo = false, onMediaTap = onMediaTap, onLongPress = { onLongPress(message) })
                        ChatMessageType.VIDEO -> MediaContent(message, isVideo = true, onMediaTap = onMediaTap, onLongPress = { onLongPress(message) })
                        ChatMessageType.MEDIA_GROUP -> MediaGroupContent(message, onMediaGroupTap = onMediaGroupTap, onLongPress = { onLongPress(message) })
                        ChatMessageType.LOCATION -> LocationContent(message)
                        else -> Unit
                    }
                    // Floating time/checks pill so the timestamp stays legible over the image
                    // without re-introducing a surrounding bubble background.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        if (message.editedAtMillis != null) {
                            Text(
                                text = "Modificato",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                        Text(
                            text = message.timeLabel,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        if (isOwn) {
                            val read = message.readBy.any { it != currentUid }
                            Text(
                                text = if (read) "✓✓" else "✓",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = gestureModifier
                        .background(if (isHighlighted) highlightColor else bubbleColor)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
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
                            ChatMessageType.TEXT -> TextContent(message, textColor, subtitleColor, isOwn)
                            ChatMessageType.PHOTO -> MediaContent(message, isVideo = false, onMediaTap = onMediaTap, onLongPress = { onLongPress(message) })
                            ChatMessageType.VIDEO -> MediaContent(message, isVideo = true, onMediaTap = onMediaTap, onLongPress = { onLongPress(message) })
                            ChatMessageType.MEDIA_GROUP -> MediaGroupContent(message, onMediaGroupTap = onMediaGroupTap, onLongPress = { onLongPress(message) })
                            ChatMessageType.LOCATION -> LocationContent(message)
                            ChatMessageType.CONTACT -> ContactContent(message, textColor, subtitleColor, isOwn)
                            ChatMessageType.DOCUMENT -> DocumentContent(message, textColor, subtitleColor)
                            ChatMessageType.AUDIO -> AudioContent(
                                message = message,
                                textColor = textColor,
                                subtitleColor = subtitleColor,
                                onAudioPlaybackStateChange = onAudioPlaybackStateChange,
                                isOwn = isOwn,
                                isTranscriptionEnabled = isTranscriptionEnabled,
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))
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
            }
            if (isOwn) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = replyHintAlpha),
                    modifier = Modifier
                        .padding(horizontal = 2.dp, vertical = 12.dp)
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
                            .background(MaterialTheme.kidBoxColors.surfaceOverlay)
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
                color = if (isOwn) Color.White.copy(alpha = 0.78f) else MaterialTheme.kidBoxColors.subtitle,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Suppress("DEPRECATION") // ClickableText is the most stable multi-version solution
@Composable
private fun TextContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
    isOwn: Boolean,
) {
    val rawText = message.text.orEmpty()
    val context = LocalContext.current
    val urls = remember(rawText) { extractUrls(rawText) }
    val linkColor = if (isOwn) Color.White else Color(0xFF1A73E8)

    // Build AnnotatedString with URL spans highlighted
    val annotated = remember(rawText, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            val matcher = android.util.Patterns.WEB_URL.matcher(rawText)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                // Text before this URL
                if (cursor < start) append(rawText, cursor, start)
                // The URL itself — annotated + styled
                val url = matcher.group() ?: continue
                val resolved = if (url.startsWith("http")) url else "https://$url"
                pushStringAnnotation(tag = "URL", annotation = resolved)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()
                cursor = end
            }
            // Remaining plain text
            if (cursor < rawText.length) append(rawText, cursor, rawText.length)
        }
    }

    ClickableText(
        text = annotated,
        style = TextStyle(color = textColor, fontSize = 15.sp),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { annotation ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    runCatching { context.startActivity(intent) }
                }
        },
    )

    // Show preview card for the first URL found
    val firstUrl = urls.firstOrNull()
    if (firstUrl != null) {
        Spacer(Modifier.height(6.dp))
        LinkPreviewCard(
            url = firstUrl,
            isOwn = isOwn,
            subtitleColor = subtitleColor,
            textColor = textColor,
        )
    }
}

@Composable
private fun LinkPreviewCard(
    url: String,
    isOwn: Boolean,
    subtitleColor: Color,
    textColor: Color,
) {
    val context = LocalContext.current
    val preview by produceState<LinkPreviewData?>(initialValue = null, key1 = url) {
        value = LinkPreviewFetcher.fetch(url)
    }

    // Don't show anything while loading — card appears once data is ready
    val data = preview ?: return

    val accentColor = if (isOwn) Color.White.copy(alpha = 0.8f) else Color(0xFFFF6B00)
    val cardBg = if (isOwn) Color.White.copy(alpha = 0.14f) else MaterialTheme.kidBoxColors.surfaceOverlay

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .combinedClickable(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                onLongClick = {},
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left accent stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (data.imageUrl != null) 80.dp else 52.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Domain
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = data.displayUrl,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Title
            if (!data.title.isNullOrBlank()) {
                Text(
                    text = data.title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Description
            if (!data.description.isNullOrBlank()) {
                Text(
                    text = data.description,
                    color = subtitleColor,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Thumbnail
        if (!data.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(data.imageUrl)
                    .memoryCacheKey(data.imageUrl)
                    .diskCacheKey(data.imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun MediaContent(
    message: UiChatMessage,
    isVideo: Boolean,
    onMediaTap: (url: String, isVideo: Boolean) -> Unit,
    onLongPress: () -> Unit = {},
) {
    val mediaUrl = message.mediaUrl
    val context = LocalContext.current
    Box(
        modifier = Modifier
            // Fill the surrounding bubble width and use a fixed 4:3 aspect so the
            // thumbnail looks natural across phone widths instead of being pinned
            // to a tiny 220×160 frame.
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(MaterialTheme.kidBoxColors.surfaceOverlay),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaUrl != null) {
            // Stable cache key tied to the message id so Coil keeps hitting its memory
            // / disk cache even when mediaUrl flips from the Firebase Storage https URL
            // to the local file:// URI once the asset finishes hydrating in Room.
            val cacheKey = "msg_${message.id}"
            if (isVideo) {
                val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = message.id) {
                    value = VideoThumbnailLoader.load(mediaUrl, context, cacheKey = "vid_${message.id}")
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    // Fallback while thumbnail is loading — Coil handles images natively
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUrl)
                            .memoryCacheKey(cacheKey)
                            .diskCacheKey(cacheKey)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
                    model = ImageRequest.Builder(context)
                        .data(mediaUrl)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Transparent overlay — captures tap (fullscreen) and long press (action menu).
            // onLongClick must be wired explicitly: an empty lambda would swallow the gesture
            // without propagating it to the parent bubble's gestureModifier.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { onMediaTap(mediaUrl, isVideo) },
                        onLongClick = onLongPress,
                    ),
            )
        }
    }
}

@Composable
private fun MediaGroupContent(
    message: UiChatMessage,
    onMediaGroupTap: (urls: List<String>, types: List<String>, startIndex: Int) -> Unit,
    onLongPress: () -> Unit = {},
) {
    val totalCount = message.mediaGroupUrls.size
    // At most 6 tiles; the 6th shows the actual image behind a "+N" overlay
    val displayCount = minOf(6, totalCount)
    // Items hidden behind the "+N" overlay: everything after the 6th
    val overflowCount = (totalCount - 6).coerceAtLeast(0)

    // Row structure — each Int is the number of cells in that row:
    //   2 → [2]        4 → [2,2]
    //   3 → [3]        5 → [2,3]
    //   6+ → [3,3]
    val rowLayout = mediaGridLayout(displayCount)
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var tileIndex = 0
        rowLayout.forEach { cellsInRow ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(cellsInRow) {
                    val idx = tileIndex++
                    val url = message.mediaGroupUrls[idx]
                    val isVideo = message.mediaGroupTypes.getOrNull(idx) == "video"
                    // The last tile (index 5) gets the "+N" scrim when there are more items
                    val isOverflowTile = idx == 5 && overflowCount > 0
                    val tileKey = "msg_${message.id}_$idx"

                    // Load video thumbnail asynchronously; photos are handled by Coil
                    val videoBmp by if (isVideo) {
                        produceState<android.graphics.Bitmap?>(initialValue = null, key1 = tileKey) {
                            value = VideoThumbnailLoader.load(url, context, cacheKey = tileKey)
                        }
                    } else {
                        produceState<android.graphics.Bitmap?>(initialValue = null) {}
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.kidBoxColors.surfaceOverlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        // ── Thumbnail ─────────────────────────────────────────
                        if (isVideo && videoBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = videoBmp!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCacheKey(tileKey)
                                    .diskCacheKey(tileKey)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        // ── "+N" overflow overlay on the 6th tile ─────────────
                        if (isOverflowTile) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+$overflowCount",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // ── Play icon for video tiles (skip the overflow cell) ─
                        if (isVideo && !isOverflowTile) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                                    .padding(4.dp),
                            )
                        }

                        // ── Transparent tap / long-press overlay ──────────────
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        onMediaGroupTap(
                                            message.mediaGroupUrls,
                                            message.mediaGroupTypes,
                                            idx,
                                        )
                                    },
                                    onLongClick = onLongPress,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns the grid row structure for [count] displayed tiles (1–6).
 * Each element is the number of cells in that row.
 *
 *   1 → [1]      3 → [3]      5 → [2, 3]
 *   2 → [2]      4 → [2, 2]   6+ → [3, 3]
 */
private fun mediaGridLayout(count: Int): List<Int> = when (count) {
    1    -> listOf(1)
    2    -> listOf(2)
    3    -> listOf(3)
    4    -> listOf(2, 2)
    5    -> listOf(2, 3)
    else -> listOf(3, 3)   // 6 tiles shown; extras hidden behind the "+N" overlay
}

@Composable
private fun LocationContent(message: UiChatMessage) {
    val context = LocalContext.current
    val lat = message.latitude ?: return
    val lon = message.longitude ?: return

    // A full GoogleMap inside a LazyColumn is extremely expensive — each instance
    // allocates a WebView-backed surface, starts the Maps SDK renderer, and issues
    // tile network requests. With several location messages visible at once this
    // tanks scroll performance and causes the initial "a scatti" jank.
    //
    // Instead we render a lightweight static preview that opens the system Maps app
    // on tap. The user gets the same geo-context at a fraction of the cost.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF78909C)) // blue-grey placeholder
            .combinedClickable(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:$lat,$lon?q=$lat,$lon(Posizione)"),
                    )
                    context.startActivity(intent)
                },
                onLongClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "Apri in Maps",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "%.5f, %.5f".format(lat, lon),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ContactContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
    isOwn: Boolean,
) {
    val payload = message.contactPayload
    val context = LocalContext.current
    var showDetail by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = MaterialTheme.colorScheme.primary
    val avatarBg = if (isOwn) Color.White.copy(alpha = 0.22f) else accent.copy(alpha = 0.12f)
    val avatarTint = if (isOwn) Color.White else accent
    val dividerColor = if (isOwn) Color.White.copy(alpha = 0.22f) else MaterialTheme.kidBoxColors.divider
    val actionColor = if (isOwn) Color.White.copy(alpha = 0.95f) else accent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.kidBoxColors.surfaceOverlay)
            .combinedClickable(onClick = { showDetail = true }, onLongClick = {})
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // ── Avatar + name + phone row ──────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = avatarTint,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payload?.fullName ?: "Contatto",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = payload?.primaryPhone ?: "Nessun numero",
                    color = subtitleColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Divider ────────────────────────────────────────────────────────
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        // ── Visualizza button ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Visualizza",
                color = actionColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = actionColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    // ── Detail bottom sheet ────────────────────────────────────────────────
    if (showDetail && payload != null) {
        ModalBottomSheet(
            onDismissRequest = { showDetail = false },
            sheetState = sheetState,
        ) {
            ContactDetailSheet(payload = payload, context = context, onDismiss = { showDetail = false })
        }
    }
}

@Composable
private fun ContactDetailSheet(
    payload: it.vittorioscocca.kidbox.data.chat.model.ContactPayload,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        // ── Header: avatar + name ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = payload.fullName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.kidBoxColors.title,
            )
            if (!payload.primaryPhone.isNullOrBlank()) {
                Text(
                    text = payload.primaryPhone!!,
                    fontSize = 14.sp,
                    color = MaterialTheme.kidBoxColors.subtitle,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.kidBoxColors.divider)

        // ── Phone numbers ──────────────────────────────────────────────────
        if (payload.phoneNumbers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Numeri di telefono",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            payload.phoneNumbers.forEach { phone ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                val cleaned = phone.value.filter { "0123456789+".contains(it) }
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
                                context.startActivity(intent)
                            },
                            onLongClick = {},
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = phone.value,
                            fontSize = 15.sp,
                            color = MaterialTheme.kidBoxColors.title,
                        )
                        if (phone.label.isNotBlank()) {
                            Text(
                                text = phone.label,
                                fontSize = 12.sp,
                                color = MaterialTheme.kidBoxColors.subtitle,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.kidBoxColors.subtitle,
                        modifier = Modifier.size(16.dp),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp),
                    color = MaterialTheme.kidBoxColors.divider,
                )
            }
        }

        // ── Email addresses ────────────────────────────────────────────────
        if (payload.emailAddresses.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Email",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            payload.emailAddresses.forEach { email ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = email.value,
                            fontSize = 15.sp,
                            color = MaterialTheme.kidBoxColors.title,
                        )
                        if (email.label.isNotBlank()) {
                            Text(
                                text = email.label,
                                fontSize = 12.sp,
                                color = MaterialTheme.kidBoxColors.subtitle,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp),
                    color = MaterialTheme.kidBoxColors.divider,
                )
            }
        }

        // ── Aggiungi ai contatti ───────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.10f))
                .combinedClickable(
                    onClick = {
                        val intent = Intent(android.provider.ContactsContract.Intents.Insert.ACTION).apply {
                            type = android.provider.ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(android.provider.ContactsContract.Intents.Insert.NAME, payload.fullName)
                            payload.primaryPhone?.let {
                                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, it)
                            }
                        }
                        context.startActivity(intent)
                    },
                    onLongClick = {},
                )
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aggiungi ai contatti",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun DocumentContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by rememberSaveable(message.id) { mutableStateOf<String?>(null) }

    val onTap = {
        val url = message.mediaUrl
        if (!isDownloading && url != null) {
            scope.launch {
                isDownloading = true
                downloadError = null
                runCatching {
                    val fileName = message.text?.takeIf { it.isNotBlank() } ?: "documento"
                    val cacheDir = File(context.cacheDir, "kb_docs").also { it.mkdirs() }
                    val dest = File(cacheDir, fileName.replace('/', '_'))
                    withContext(Dispatchers.IO) {
                        URL(url).openStream().use { src -> dest.outputStream().use { dst -> src.copyTo(dst) } }
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                    val mime = mimeFromFileName(fileName)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Apri con"))
                }.onFailure { err ->
                    downloadError = err.message ?: "Errore download"
                }
                isDownloading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.kidBoxColors.surfaceOverlay)
            .combinedClickable(onClick = onTap, onLongClick = {})
            .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = textColor,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column {
                Text(
                    text = message.text ?: "Documento",
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subLabel = when {
                    message.mediaUrl == null -> "In caricamento…"
                    isDownloading -> "Download in corso…"
                    else -> "Tocca per aprire"
                }
                Text(text = subLabel, color = subtitleColor, fontSize = 11.sp)
            }
        }
        message.mediaFileSize?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = Formatter.formatShortFileSize(context, it),
                color = subtitleColor,
                fontSize = 11.sp,
            )
        }
        if (downloadError != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = downloadError!!,
                color = Color(0xFFFF3B30),
                fontSize = 11.sp,
            )
        }
    }
}

private fun mimeFromFileName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/x-m4a"
        else -> "application/octet-stream"
    }
}

@Composable
private fun AudioContent(
    message: UiChatMessage,
    textColor: Color,
    subtitleColor: Color,
    onAudioPlaybackStateChange: (Boolean) -> Unit,
    isOwn: Boolean,
    isTranscriptionEnabled: Boolean = true,
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

    // Lazy player: do NOT create or call prepareAsync() during composition.
    // Eagerly preparing every visible audio bubble on first render launches N concurrent
    // MediaMetadataRetriever / HTTP operations and blocks the audio focus subsystem —
    // this is the main cause of "a scatti" jank when the chat first loads.
    // The player is created and prepared only when the user taps the play button.
    var player by remember(mediaUrl) { mutableStateOf<MediaPlayer?>(null) }

    // Returns the existing player or creates+prepares a new one on first call.
    // Safe to call from click handlers (main thread).
    fun getOrCreatePlayer(): MediaPlayer? {
        if (mediaUrl.isNullOrBlank()) return null
        player?.let { return it }
        val p = MediaPlayer()
        runCatching {
            val uri = mediaUrl.toUri()
            when (uri.scheme?.lowercase()) {
                "http", "https" -> p.setDataSource(mediaUrl)
                else -> p.setDataSource(context, uri)
            }
            p.setOnPreparedListener { mp ->
                isPrepared = true
                if (mp.duration > 0) durationMs = mp.duration
                // Auto-start once prepared (user already tapped play)
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                    mp.start()
                }
                isPlaying = true
                onAudioPlaybackStateChange(true)
            }
            p.setOnCompletionListener {
                isPlaying = false
                currentMs = durationMs
                onAudioPlaybackStateChange(false)
            }
            p.prepareAsync()
        }.onFailure { p.release() ; return null }
        player = p
        return p
    }

    LaunchedEffect(isPlaying, player) {
        val p = player ?: return@LaunchedEffect
        while (isPlaying) {
            currentMs = runCatching { p.currentPosition }.getOrDefault(currentMs)
            kotlinx.coroutines.delay(120)
        }
    }
    DisposableEffect(mediaUrl) {
        onDispose {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
            onAudioPlaybackStateChange(false)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    // Pause immediately — player must already exist
                    runCatching { player?.pause() }
                    isPlaying = false
                    onAudioPlaybackStateChange(false)
                } else {
                    val p = getOrCreatePlayer() ?: return@IconButton
                    if (!isPrepared) {
                        // prepareAsync() is running; onPreparedListener will auto-start
                        return@IconButton
                    }
                    if (currentMs >= durationMs && durationMs > 0) {
                        runCatching { p.seekTo(0) }
                        currentMs = 0
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
                    }
                    runCatching { p.start() }
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
                .background(if (isOwn) Color.White.copy(alpha = 0.24f) else MaterialTheme.kidBoxColors.surfaceOverlay)
                .border(
                    width = 1.dp,
                    color = if (isOwn) Color.White.copy(alpha = 0.12f) else MaterialTheme.kidBoxColors.title.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                )
                .combinedClickable(
                    onClick = {
                        speedIndex = (speedIndex + 1) % speedValues.size
                        val nextSpeed = speedValues[speedIndex]
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && isPrepared) {
                            runCatching {
                                val p = player ?: return@runCatching
                                p.playbackParams = p.playbackParams.setSpeed(nextSpeed)
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

    // ── Transcript section — shown only for incoming messages when the feature is on ──
    if (isTranscriptionEnabled && !isOwn) {
        val transcriptStatus = message.transcriptStatusRaw   // "none" | "processing" | "completed" | "failed"
        val transcriptText   = message.transcriptText?.trim().orEmpty()

        when {
            // ① Text available → show it
            transcriptText.isNotBlank() -> {
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
                    text = transcriptText,
                    color = if (isOwn) Color.White.copy(alpha = 0.92f)
                            else MaterialTheme.kidBoxColors.title,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            // ② In corso su questo dispositivo → spinner
            transcriptStatus == "processing" -> {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = subtitleColor,
                    )
                    Text(
                        text = "Trascrizione in corso…",
                        color = subtitleColor,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }

            // ③ Fallito o completato senza testo → "non disponibile"
            transcriptStatus == "failed" ||
            (transcriptStatus == "completed" && transcriptText.isBlank()) -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Trascrizione non disponibile",
                    color = subtitleColor,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }

            // ④ "none" → audio non ancora elaborato, non mostrare nulla
        }
    }
}

private fun formatAudioTime(ms: Int): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val mm = (sec / 60).toString().padStart(2, '0')
    val ss = (sec % 60).toString().padStart(2, '0')
    return "$mm:$ss"
}
