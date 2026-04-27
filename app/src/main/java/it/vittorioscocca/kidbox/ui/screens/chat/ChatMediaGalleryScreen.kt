package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private enum class GalleryTab(val label: String) { MEDIA("Media"), LINKS("Link"), DOCS("Documenti") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMediaGallerySheet(
    messages: List<UiChatMessage>,
    onDismiss: () -> Unit,
    onGoToMessage: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(GalleryTab.MEDIA) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val mediaItems = remember(messages) {
        messages.flatMap { msg ->
            when (msg.type) {
                ChatMessageType.PHOTO, ChatMessageType.VIDEO -> listOf(msg.id to (msg.mediaUrl ?: ""))
                ChatMessageType.MEDIA_GROUP -> msg.mediaGroupUrls.mapIndexed { idx, url -> "${msg.id}_$idx" to url }
                else -> emptyList()
            }
        }.filter { it.second.isNotBlank() }
    }
    val linkItems = remember(messages) {
        messages.filter { it.type == ChatMessageType.TEXT && (it.text ?: "").contains("http") }
    }
    val docItems = remember(messages) { messages.filter { it.type == ChatMessageType.DOCUMENT } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.background,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GalleryTab.entries.forEach { tab ->
                    val selectedTab = tab == selected
                    Text(
                        text = tab.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selectedTab) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.kidBoxColors.card)
                            .clickable { selected = tab }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (selectedTab) MaterialTheme.colorScheme.primary else MaterialTheme.kidBoxColors.subtitle,
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            when (selected) {
                GalleryTab.MEDIA -> {
                    if (mediaItems.isEmpty()) {
                        EmptyGallery("Nessun media")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(mediaItems, key = { it.first }) { (id, url) ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onDismiss()
                                            onGoToMessage(id.substringBefore("_"))
                                        },
                                )
                            }
                        }
                    }
                }

                GalleryTab.LINKS -> {
                    if (linkItems.isEmpty()) {
                        EmptyGallery("Nessun link")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(linkItems.size) { index ->
                                val item = linkItems[index]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onGoToMessage(item.id)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = item.text ?: "Link",
                                        color = MaterialTheme.kidBoxColors.title,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.senderName} · ${item.dayLabel}",
                                        color = MaterialTheme.kidBoxColors.subtitle,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                GalleryTab.DOCS -> {
                    if (docItems.isEmpty()) {
                        EmptyGallery("Nessun documento")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(docItems.size) { index ->
                                val item = docItems[index]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onGoToMessage(item.id)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = item.text ?: "Documento",
                                        color = MaterialTheme.kidBoxColors.title,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.senderName} · ${item.dayLabel}",
                                        color = MaterialTheme.kidBoxColors.subtitle,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun EmptyGallery(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.kidBoxColors.subtitle,
            fontSize = 13.sp,
        )
    }
}
