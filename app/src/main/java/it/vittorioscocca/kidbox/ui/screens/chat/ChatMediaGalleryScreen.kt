@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

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

private enum class GalleryTab(@androidx.annotation.StringRes val labelRes: Int) {
    MEDIA(R.string.chat_media), LINKS(R.string.chat_links), DOCS(R.string.chat_documents)
}

// ─────────────────────────────────────────────────────────────────────────────
// Population helpers
// ─────────────────────────────────────────────────────────────────────────────

private val urlRegex = Regex("https?://[^\\s]+")

internal fun buildMediaItems(messages: List<UiChatMessage>): List<GalleryMediaItem> =
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

private fun buildDocItems(context: android.content.Context, messages: List<UiChatMessage>): List<GalleryDocItem> =
    messages.mapNotNull { msg ->
        if (msg.type != ChatMessageType.DOCUMENT) return@mapNotNull null
        val url = msg.mediaUrl ?: return@mapNotNull null
        GalleryDocItem(
            messageId = msg.id,
            url = url,
            fileName = msg.text?.takeIf { it.isNotBlank() } ?: context.getString(R.string.chat_document),
            fileSizeBytes = msg.mediaFileSize,
            senderName = msg.senderName,
            createdAtMillis = msg.createdAtMillis,
        )
    }

private fun Long.toGalleryDate(): String =
    SimpleDateFormat("dd MMM yyyy", KBLocale.current()).format(Date(this))

private fun Long.formatFileSize(): String {
    val kb = this / 1024
    val mb = kb / 1024
    return if (mb > 0) "$mb MB" else "$kb KB"
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMediaGalleryScreen(
    messages: List<UiChatMessage>,
    onDismiss: () -> Unit,
    onGoToMessage: (messageId: String) -> Unit,
    onReply: ((messageId: String) -> Unit)? = null,
    onDelete: ((messageId: String, forEveryone: Boolean) -> Unit)? = null,
    canDeleteForEveryone: (messageId: String) -> Boolean = { false },
) {
    val context = LocalContext.current
    // Pre-compute items once per messages snapshot
    val mediaItems = remember(messages) { buildMediaItems(messages) }
    val linkItems  = remember(messages) { buildLinkItems(messages) }
    val docItems   = remember(messages) { buildDocItems(context, messages) }

    var selectedTab by remember { mutableStateOf(GalleryTab.MEDIA) }
    var searchQuery by remember { mutableStateOf("") }

    // Fullscreen viewer: null = hidden, non-null = start index within filteredMedia
    var fullscreenStartIndex by remember { mutableStateOf<Int?>(null) }

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

    LaunchedEffect(selectedTab) { searchQuery = "" }

    Scaffold(
        containerColor = MaterialTheme.kidBoxColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.chat_media_links_docs),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.kidBoxColors.title,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back),
                            tint = MaterialTheme.kidBoxColors.title,
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.kidBoxColors.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Tab chips ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GalleryTab.entries.forEach { tab ->
                        val isActive = tab == selectedTab
                        Text(
                            text = stringResource(tab.labelRes),
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

                // ── Search bar ────────────────────────────────────────────────
                val placeholder = when (selectedTab) {
                    GalleryTab.MEDIA -> stringResource(R.string.chat_search_media)
                    GalleryTab.LINKS -> stringResource(R.string.chat_search_link)
                    GalleryTab.DOCS  -> stringResource(R.string.chat_search_doc)
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
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_clear), tint = kb.subtitle, modifier = Modifier.size(16.dp))
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

                HorizontalDivider(color = MaterialTheme.kidBoxColors.divider, modifier = Modifier.padding(top = 4.dp))

                // ── Tab content ───────────────────────────────────────────────
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

            // ── Fullscreen viewer — covers the entire screen via zIndex(10f) ──
            val startIdx = fullscreenStartIndex
            if (startIdx != null) {
                GalleryFullscreenViewer(
                    initialItems = filteredMedia,
                    startIndex = startIdx,
                    onClose = { fullscreenStartIndex = null },
                    onGoToMessage = { msgId ->
                        fullscreenStartIndex = null
                        onGoToMessage(msgId)
                    },
                    onReply = onReply?.let { reply ->
                        { msgId ->
                            fullscreenStartIndex = null
                            reply(msgId)
                        }
                    },
                    onDelete = onDelete,
                    canDeleteForEveryone = canDeleteForEveryone,
                )
            }
        }
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
        GalleryEmptyState(Icons.Default.PhotoLibrary, stringResource(R.string.chat_no_media))
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
                text = { Text(stringResource(R.string.chat_go_to_message)) },
                onClick = {
                    showMenu = false
                    onGoToMessage(item.messageId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_delete_for_me), color = Color(0xFFD32F2F)) },
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
        GalleryEmptyState(Icons.Default.Link, stringResource(R.string.chat_no_links))
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
                    runCatching {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("link", item.url),
                        )
                    }
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
                    text = { Text(stringResource(R.string.chat_copy_link)) },
                    onClick = { showMenu = false; onCopyLink() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_go_to_message)) },
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
        GalleryEmptyState(Icons.Default.Description, stringResource(R.string.chat_no_docs))
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
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.chat_open_with)))
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
                    text = { Text(stringResource(R.string.chat_go_to_message)) },
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
internal fun GalleryFullscreenViewer(
    initialItems: List<GalleryMediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onGoToMessage: (messageId: String) -> Unit,
    // Azioni come su iOS: senza callback il pulsante non compare.
    onReply: ((messageId: String) -> Unit)? = null,
    onDelete: ((messageId: String, forEveryone: Boolean) -> Unit)? = null,
    canDeleteForEveryone: (messageId: String) -> Boolean = { false },
) {
    // Copia locale: eliminando un media la pagina sparisce subito dal visore.
    val items = remember(initialItems) { initialItems.toMutableStateList() }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSharing by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GalleryMediaItem?>(null) }

    var dragAccum by remember { mutableFloatStateOf(0f) }
    val swipeDownThresholdPx = 300f

    // Indietro chiude il visore, non la schermata sotto.
    androidx.activity.compose.BackHandler(onBack = onClose)

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
                        // Miniatura dal primo fotogramma. Mai l'URL del video a Coil:
                        // non lo decodifica, ma prima lo scarica per intero.
                        val thumbKey = "gallery_full_${item.id}"
                        val thumbnail by produceState(
                            initialValue = VideoThumbnailLoader.peek(thumbKey),
                            key1 = item.url,
                        ) {
                            if (value == null) {
                                value = VideoThumbnailLoader.load(item.url, context, cacheKey = thumbKey, maxSide = Int.MAX_VALUE)
                            }
                        }
                        thumbnail?.let { bmp ->
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // Play button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { isPlaying = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.chat_play), tint = Color.White, modifier = Modifier.size(40.dp))
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
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_close), tint = Color.White)
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

        // ── Bottom bar: striscia di miniature (come su iOS) + azioni ─────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                )
                .navigationBarsPadding(),
        ) {
            if (items.size > 1) {
                GalleryThumbStrip(
                    items = items,
                    currentPage = pagerState.currentPage,
                    onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            }
            // Barra azioni come su iOS: Condividi · Rispondi · Messaggio · Elimina.
            val currentItem = items.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerToolbarButton(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.chat_share),
                    enabled = !isSharing && currentItem != null,
                    busy = isSharing,
                ) {
                    val item = currentItem ?: return@ViewerToolbarButton
                    scope.launch {
                        isSharing = true
                        val ok = shareGalleryMedia(context, item)
                        isSharing = false
                        if (!ok) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.chat_share_failed),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                if (onReply != null) {
                    ViewerToolbarButton(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        label = stringResource(R.string.chat_reply),
                    ) {
                        currentItem?.let { onReply(it.messageId) }
                    }
                }
                ViewerToolbarButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = stringResource(R.string.chat_message),
                ) {
                    currentItem?.let { onGoToMessage(it.messageId) }
                }
                if (onDelete != null) {
                    ViewerToolbarButton(
                        icon = Icons.Default.DeleteOutline,
                        label = stringResource(R.string.chat_delete),
                        tint = Color(0xFFFF453A),
                    ) {
                        deleteTarget = currentItem
                    }
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null && onDelete != null) {
        val forEveryoneAllowed = remember(target.messageId) { canDeleteForEveryone(target.messageId) }
        // Su Android si elimina il messaggio intero: per un gruppo lo diciamo.
        val groupSize = items.count { it.messageId == target.messageId }
        fun confirm(forEveryone: Boolean) {
            deleteTarget = null
            onDelete(target.messageId, forEveryone)
            items.removeAll { it.messageId == target.messageId }
            if (items.isEmpty()) onClose()
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_media_delete_q)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (forEveryoneAllowed) R.string.chat_media_delete_choice else R.string.chat_media_delete_me,
                        ),
                    )
                    if (groupSize > 1) {
                        Text(stringResource(R.string.chat_media_delete_group, groupSize))
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { confirm(forEveryone = false) }) {
                        Text(stringResource(R.string.chat_delete_for_me), color = MaterialTheme.colorScheme.error)
                    }
                    if (forEveryoneAllowed) {
                        TextButton(onClick = { confirm(forEveryone = true) }) {
                            Text(stringResource(R.string.chat_delete_for_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ViewerToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = tint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, color = tint.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * Scarica il media in cache e apre il foglio di condivisione di sistema.
 * `false` se il download o l'apertura non riescono.
 */
private suspend fun shareGalleryMedia(context: android.content.Context, item: GalleryMediaItem): Boolean {
    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "chat_share").apply { mkdirs() }
            // Via i file delle condivisioni precedenti: il foglio di sistema li ha già letti.
            dir.listFiles()?.forEach { it.delete() }
            val out = java.io.File(dir, "${java.util.UUID.randomUUID()}.${if (item.isVideo) "mp4" else "jpg"}")
            java.net.URL(item.url).openStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out.takeIf { it.length() > 0 }
        }.getOrNull()
    } ?: return false
    return runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/mp4" else "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(null, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, null))
    }.isSuccess
}

private val ThumbStripSize = 52.dp

/**
 * Striscia orizzontale di miniature sotto il visore, come su iOS: la corrente
 * ha il bordo bianco, le altre sono attenuate; segue la pagina e un tocco ci salta.
 */
@Composable
private fun GalleryThumbStrip(
    items: List<GalleryMediaItem>,
    currentPage: Int,
    onSelect: (Int) -> Unit,
) {
    val stripState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (currentPage - 3).coerceAtLeast(0),
    )
    // Tiene la miniatura corrente AL CENTRO della striscia (salvo ai bordi della
    // lista, dove non c'è spazio per centrarla). Si misura la posizione vera
    // dell'elemento: stimare dal numero di visibili lo lasciava scivolare fuori.
    LaunchedEffect(currentPage) {
        if (stripState.layoutInfo.visibleItemsInfo.none { it.index == currentPage }) {
            // Fuori vista (apertura su un media lontano, salto lungo): prima ci si porta
            // lì senza animazione, poi al prossimo frame la misura è aggiornata.
            stripState.scrollToItem(currentPage)
            withFrameNanos { }
        }
        val info = stripState.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == currentPage } ?: return@LaunchedEffect
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val delta = target.offset + target.size / 2 - viewportCenter
        if (delta != 0) stripState.animateScrollBy(delta.toFloat())
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = stripState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(items.size, key = { items[it].id }) { idx ->
            GalleryThumbCell(
                item = items[idx],
                isSelected = idx == currentPage,
                onClick = { onSelect(idx) },
            )
        }
    }
}

@Composable
private fun GalleryThumbCell(
    item: GalleryMediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(ThumbStripSize)
            .alpha(if (isSelected) 1f else 0.55f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(2.dp, if (isSelected) Color.White else Color.Transparent, shape)
            .clickable(onClick = onClick),
    ) {
        if (item.isVideo) {
            // Mai l'URL del video a Coil: miniatura dal primo fotogramma, piccola.
            val key = "strip_${item.id}"
            val thumb by produceState(initialValue = VideoThumbnailLoader.peek(key), key1 = item.url) {
                if (value == null) value = VideoThumbnailLoader.load(item.url, context, cacheKey = key, maxSide = 200)
            }
            thumb?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .size(14.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(2.dp),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.url)
                    .size(200)
                    .memoryCacheKey("strip_${item.id}")
                    .diskCacheKey(item.url)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
