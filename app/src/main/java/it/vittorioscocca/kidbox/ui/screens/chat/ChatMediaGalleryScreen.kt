@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Accent colour (matches the rest of KidBox)
// ─────────────────────────────────────────────────────────────────────────────
private val AccentOrange = Color(0xFFFF6B00)

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

/** A single media item (photo or video) extracted from the message list. */
data class GalleryMediaItem(
    val id: String,               // messageId, or "messageId_idx" for media-group entries
    val messageId: String,
    val url: String,
    val isVideo: Boolean,
    val senderName: String,
    val createdAtMillis: Long,
)

data class GalleryLinkItem(
    val messageId: String,
    val url: String,
    val host: String,
    val snippet: String?,
    val senderName: String,
    val createdAtMillis: Long,
)

data class GalleryDocItem(
    val messageId: String,
    val url: String,
    val fileName: String,
    val fileSizeBytes: Long?,
    val senderName: String,
    val createdAtMillis: Long,
)

private enum class GalleryTab(val label: String) {
    MEDIA("Media"), LINKS("Link"), DOCS("Documenti")
}

// ─────────────────────────────────────────────────────────────────────────────
// Population helpers
// ─────────────────────────────────────────────────────────────────────────────

private val urlRegex = Regex("https?://[^\\s]+")

private fun buildMediaItems(messages: List<UiChatMessage>): List<GalleryMediaItem> =
    messages.flatMap { msg ->
        when (msg.type) {
            ChatMessageType.PHOTO -> {
                val url = msg.mediaUrl ?: return@flatMap emptyList()
                listOf(
                    GalleryMediaItem(
                        id = msg.id,
                        messageId = msg.id,
                        url = url,
                        isVideo = false,
                        senderName = msg.senderName,
                        createdAtMillis = msg.createdAtMillis,
                    ),
                )
            }
            ChatMessageType.VIDEO -> {
                val url = msg.mediaUrl ?: return@flatMap emptyList()
                listOf(
                    GalleryMediaItem(
                        id = msg.id,
                        messageId = msg.id,
                        url = url,
                        isVideo = true,
                        senderName = msg.senderName,
                        createdAtMillis = msg.createdAtMillis,
                    ),
                )
            }
            ChatMessageType.MEDIA_GROUP -> {
                msg.mediaGroupUrls.mapIndexedNotNull { idx, url ->
                    if (url.isBlank()) return@mapIndexedNotNull null
                    GalleryMediaItem(
                        id = "${msg.id}_$idx",
                        messageId = msg.id,
                        url = url,
                        isVideo = msg.mediaGroupTypes.getOrNull(idx) == "video",
                        senderName = msg.senderName,
                        createdAtMillis = msg.createdAtMillis,
                    )
                }
            }
            else -> emptyList()
        }
    }

private fun buildLinkItems(messages: List<UiChatMessage>): List<GalleryLinkItem> =
    messages.mapNotNull { msg ->
        if (msg.type != ChatMessageType.TEXT) return@mapNotNull null
        val text = msg.text ?: return@mapNotNull null
        val url = urlRegex.find(text)?.value ?: return@mapNotNull null
        val host = runCatching { java.net.URL(url).host.removePrefix("www.") }.getOrDefault(url)
        GalleryLinkItem(
            messageId = msg.id,
            url = url,
            host = host,
            snippet = text.takeIf { it.isNotBlank() },
            senderName = msg.senderName,
            createdAtMillis = msg.createdAtMillis,
        )
    }

private fun buildDocItems(messages: List<UiChatMessage>): List<GalleryDocItem> =
    messages.mapNotNull { msg ->
        if (msg.type != ChatMessageType.DOCUMENT) return@mapNotNull null
        val url = msg.mediaUrl ?: return@mapNotNull null
        GalleryDocItem(
            messageId = msg.id,
            url = url,
            fileName = msg.text?.takeIf { it.isNotBlank() } ?: "Documento",
            fileSizeBytes = msg.mediaFileSize,
            senderName = msg.senderName,
            createdAtMillis = msg.createdAtMillis,
        )
    }

private fun Long.toGalleryDate(): String =
    SimpleDateFormat("dd MMM yyyy", Locale.ITALY).format(Date(this))

private fun Long.formatFileSize(): String {
    val kb = this / 1024
    val mb = kb / 1024
    return if (mb > 0) "$mb MB" else "$kb KB"
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ChatMediaGallerySheet(
    messages: List<UiChatMessage>,
    onDismiss: () -> Unit,
    onGoToMessage: (messageId: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Pre-compute items once per messages snapshot
    val mediaItems = remember(messages) { buildMediaItems(messages) }
    val linkItems  = remember(messages) { buildLinkItems(messages) }
    val docItems   = remember(messages) { buildDocItems(messages) }

    var selectedTab by remember { mutableStateOf(GalleryTab.MEDIA) }
    var searchQuery by remember { mutableStateOf("") }

    // Fullscreen viewer: null = hidden, non-null = start index within filteredMedia
    var fullscreenStartIndex by remember { mutableStateOf<Int?>(null) }

    // Recompute search filter whenever tab or query changes
    val filteredMedia = remember(mediaItems, searchQuery) {
        if (searchQuery.isBlank()) mediaItems
        else mediaItems.filter {
            it.senderName.contains(searchQuery, ignoreCase = true) ||
                it.createdAtMillis.toGalleryDate().contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredLinks = remember(linkItems, searchQuery) {
        if (searchQuery.isBlank()) linkItems
        else linkItems.filter {
            it.url.contains(searchQuery, ignoreCase = true) ||
                it.senderName.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredDocs = remember(docItems, searchQuery) {
        if (searchQuery.isBlank()) docItems
        else docItems.filter {
            it.fileName.contains(searchQuery, ignoreCase = true) ||
                it.senderName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Reset search when switching tabs
    LaunchedEffect(selectedTab) { searchQuery = "" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),   // occupies ~92 % of the screen height
        ) {
            // ── Handle + title ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.kidBoxColors.divider),
                )
            }
            Text(
                text = "Media, link e documenti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.kidBoxColors.title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ── Tab chips ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GalleryTab.entries.forEach { tab ->
                    val isActive = tab == selectedTab
                    Text(
                        text = tab.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isActive) AccentOrange.copy(alpha = 0.13f)
                                else MaterialTheme.kidBoxColors.card,
                            )
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isActive) AccentOrange else MaterialTheme.kidBoxColors.subtitle,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            val placeholder = when (selectedTab) {
                GalleryTab.MEDIA -> "Cerca media o mittente"
                GalleryTab.LINKS -> "Cerca link o sito"
                GalleryTab.DOCS  -> "Cerca documento o mittente"
            }
            val kb = MaterialTheme.kidBoxColors
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text(placeholder, fontSize = 13.sp, color = kb.subtitle) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella", tint = kb.subtitle, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = kb.card,
                    unfocusedContainerColor = kb.card,
                    focusedBorderColor = AccentOrange.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = kb.title,
                    unfocusedTextColor = kb.title,
                ),
            )

            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(top = 4.dp))

            // ── Tab content ───────────────────────────────────────────────────
            when (selectedTab) {
                GalleryTab.MEDIA -> MediaTab(
                    items = filteredMedia,
                    onThumbTap = { idx -> fullscreenStartIndex = idx },
                    onGoToMessage = onGoToMessage,
                )
                GalleryTab.LINKS -> LinksTab(
                    items = filteredLinks,
                    onGoToMessage = onGoToMessage,
                    onDismiss = onDismiss,
                )
                GalleryTab.DOCS  -> DocsTab(
                    items = filteredDocs,
                    onGoToMessage = onGoToMessage,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    // ── Fullscreen viewer — rendered above the sheet ─────────────────────────
    val startIdx = fullscreenStartIndex
    if (startIdx != null) {
        GalleryFullscreenViewer(
            items = filteredMedia,
            startIndex = startIdx,
            onClose = { fullscreenStartIndex = null },
            onGoToMessage = { msgId ->
                fullscreenStartIndex = null
                onDismiss()
                onGoToMessage(msgId)
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Media tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaTab(
    items: List<GalleryMediaItem>,
    onThumbTap: (index: Int) -> Unit,
    onGoToMessage: (messageId: String) -> Unit,
) {
    if (items.isEmpty()) {
        GalleryEmptyState(Icons.Default.PhotoLibrary, "Nessun media condiviso")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val idx = items.indexOf(item)
            MediaThumbCell(
                item = item,
                onClick = { onThumbTap(idx) },
                onGoToMessage = onGoToMessage,
            )
        }
    }
}

@Composable
private fun MediaThumbCell(
    item: GalleryMediaItem,
    onClick: () -> Unit,
    onGoToMessage: (messageId: String) -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.kidBoxColors.surfaceOverlay)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.url)
                .memoryCacheKey(item.url)
                .diskCacheKey(item.url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.isVideo) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.40f), CircleShape)
                    .padding(4.dp),
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Vai al messaggio") },
                onClick = {
                    showMenu = false
                    onGoToMessage(item.messageId)
                },
            )
            DropdownMenuItem(
                text = { Text("Elimina per me", color = Color(0xFFD32F2F)) },
                onClick = { showMenu = false /* no-op: destructive action requires ViewModel */ },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Links tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LinksTab(
    items: List<GalleryLinkItem>,
    onGoToMessage: (messageId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) {
        GalleryEmptyState(Icons.Default.Link, "Nessun link condiviso")
        return
    }

    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.messageId + it.url }) { item ->
            GalleryLinkRow(
                item = item,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                    }
                },
                onGoToMessage = {
                    onDismiss()
                    onGoToMessage(item.messageId)
                },
                onCopyLink = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("link", item.url),
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.kidBoxColors.divider,
                modifier = Modifier.padding(start = 60.dp),
            )
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun GalleryLinkRow(
    item: GalleryLinkItem,
    onClick: () -> Unit,
    onGoToMessage: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
        }

        // Text column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.host,
                color = kb.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.snippet.isNullOrBlank()) {
                Text(
                    text = item.snippet,
                    color = kb.subtitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${item.createdAtMillis.toGalleryDate()} · ${item.senderName}",
                color = kb.subtitle,
                fontSize = 11.sp,
            )
        }

        Box {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Copia link") },
                    onClick = { showMenu = false; onCopyLink() },
                )
                DropdownMenuItem(
                    text = { Text("Vai al messaggio") },
                    onClick = { showMenu = false; onGoToMessage() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Documents tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DocsTab(
    items: List<GalleryDocItem>,
    onGoToMessage: (messageId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) {
        GalleryEmptyState(Icons.Default.Description, "Nessun documento condiviso")
        return
    }

    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.messageId }) { item ->
            GalleryDocRow(
                item = item,
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Apri con"))
                    }
                },
                onGoToMessage = {
                    onDismiss()
                    onGoToMessage(item.messageId)
                },
            )
            HorizontalDivider(
                color = MaterialTheme.kidBoxColors.divider,
                modifier = Modifier.padding(start = 60.dp),
            )
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun GalleryDocRow(
    item: GalleryDocItem,
    onClick: () -> Unit,
    onGoToMessage: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var showMenu by remember { mutableStateOf(false) }
    val docBlue = Color(0xFF5B8FDE)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(docBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = docBlue, modifier = Modifier.size(22.dp))
        }

        // Text column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                color = kb.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                item.fileSizeBytes?.let { append(it.formatFileSize()); append(" · ") }
                append(item.createdAtMillis.toGalleryDate())
                append(" · ")
                append(item.senderName)
            }
            Text(text = sub, color = kb.subtitle, fontSize = 11.sp)
        }

        Box {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Vai al messaggio") },
                    onClick = { showMenu = false; onGoToMessage() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen viewer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GalleryFullscreenViewer(
    items: List<GalleryMediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onGoToMessage: (messageId: String) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var dragAccum by remember { mutableFloatStateOf(0f) }
    val swipeDownThresholdPx = 300f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black)
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragAccum > swipeDownThresholdPx) onClose()
                        dragAccum = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0) dragAccum += dragAmount
                    },
                )
            },
    ) {
        // ── Pager ─────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items.getOrNull(it)?.id ?: it },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager

            var isPlaying by remember { mutableStateOf(false) }
            // Reset playback when swiping away
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) isPlaying = false
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (item.isVideo) {
                    if (isPlaying) {
                        AndroidView(
                            factory = { ctx -> VideoView(ctx) },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                if (view.tag != item.url) {
                                    view.tag = item.url
                                    view.setVideoURI(Uri.parse(item.url))
                                    view.setOnPreparedListener { it.start() }
                                }
                            },
                        )
                    } else {
                        // Thumbnail while not playing
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.url)
                                .memoryCacheKey(item.url)
                                .diskCacheKey(item.url)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Play button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { isPlaying = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.url)
                            .memoryCacheKey(item.url)
                            .diskCacheKey(item.url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        val current = items.getOrNull(pagerState.currentPage)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                if (current != null) {
                    Text(
                        text = current.senderName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current.createdAtMillis.toGalleryDate(),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
            }
            // Page counter
            Text(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val msgId = items.getOrNull(pagerState.currentPage)?.messageId
                    if (msgId != null) onGoToMessage(msgId)
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Vai al messaggio", color = Color.White, fontSize = 13.sp)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GalleryEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.45f),
                modifier = Modifier.size(52.dp),
            )
            Text(
                text = message,
                color = MaterialTheme.kidBoxColors.subtitle,
                fontSize = 14.sp,
            )
        }
    }
}
