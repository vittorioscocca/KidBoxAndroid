package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlin.math.max
import kotlin.math.min

/**
 * Pressione lunga su un messaggio, come WhatsApp e come su iOS
 * (`ChatMessageActionsOverlay.swift`): lo sfondo si scurisce lasciando nitida la
 * bolla, sopra compare la barra delle reazioni e sotto il menu. Se sotto non c'è
 * posto, menu e barra salgono sopra la bolla. «Azioni suggerite» si apre dentro
 * il menu stesso: un tocco esegue l'azione.
 *
 * [bubbleInWindow] è la cornice della bolla in coordinate di finestra.
 */
@Composable
internal fun ChatMessageActionsOverlay(
    message: UiChatMessage,
    bubbleInWindow: Rect,
    isOwn: Boolean,
    currentUid: String,
    canEdit: Boolean,
    canDeleteForEveryone: Boolean,
    suggestions: List<SuggestedChatAction>,
    onDismiss: () -> Unit,
    onPickReaction: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: (() -> Unit)?,
    onEdit: () -> Unit,
    onSuggestedAction: (SuggestedChatAction) -> Unit,
    onSave: (() -> Unit)?,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf<Offset?>(null) }
    var showingSuggestions by remember(message.id) { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(160), label = "overlay")
    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    LaunchedEffect(Unit) {
        appeared = true
    }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { this.alpha = alpha },
    ) {
        val o = origin ?: return@Box
        val bubble = bubbleInWindow.translate(-o.x, -o.y)
        val corner = with(density) { 18.dp.toPx() }

        // Sfondo scuro con la bolla ritagliata: il tocco fuori chiude.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            drawRect(Color.Black.copy(alpha = 0.45f))
            drawRoundRect(
                color = Color.Black,
                topLeft = bubble.topLeft,
                size = bubble.size,
                cornerRadius = CornerRadius(corner),
                blendMode = BlendMode.Clear,
            )
        }

        Layout(
            content = {
                // Barra delle reazioni
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(kb.card)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    emojis.forEach { emoji ->
                        val mine = message.reactions[emoji]?.contains(currentUid) == true
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (mine) kb.subtitle.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { onPickReaction(emoji) },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                }
                // Menu
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = kb.card,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(250.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (showingSuggestions) {
                            MenuRow(
                                text = stringResource(R.string.settings_suggested_actions_title),
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                bold = true,
                                onClick = { showingSuggestions = false },
                            )
                            suggestions.forEach { suggested ->
                                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                                MenuRow(
                                    text = stringResource(suggested.kind.labelRes),
                                    icon = suggested.kind.icon,
                                    iconTint = suggested.kind.tint,
                                    onClick = {
                                        SuggestedActionsPrefs.markUsed(context, suggested.kind)
                                        onSuggestedAction(suggested)
                                    },
                                )
                            }
                        } else {
                            val danger = MaterialTheme.colorScheme.error
                            val saveLabel = when (message.type) {
                                ChatMessageType.PHOTO -> stringResource(R.string.chat_save_photo)
                                ChatMessageType.VIDEO -> stringResource(R.string.chat_save_video)
                                ChatMessageType.AUDIO -> stringResource(R.string.chat_save_audio)
                                ChatMessageType.DOCUMENT -> stringResource(R.string.chat_save_document)
                                else -> stringResource(R.string.chat_save)
                            }
                            val rows = buildList<@Composable () -> Unit> {
                                if (!message.isDeletedForEveryone) {
                                    add { MenuRow(stringResource(R.string.chat_reply), Icons.Default.Reply, onClick = onReply) }
                                }
                                if (onCopy != null) {
                                    add { MenuRow(stringResource(R.string.chat_menu_copy), Icons.Default.ContentCopy, onClick = onCopy) }
                                }
                                if (canEdit && message.type == ChatMessageType.TEXT) {
                                    add { MenuRow(stringResource(R.string.chat_menu_edit), Icons.Default.ModeEdit, onClick = onEdit) }
                                }
                                if (suggestions.isNotEmpty()) {
                                    add {
                                        MenuRow(
                                            text = stringResource(R.string.settings_suggested_actions_title),
                                            icon = Icons.Default.AutoAwesome,
                                            trailingChevron = true,
                                            onClick = {
                                                // Compaiono ora: per i 30 giorni contano come mostrate.
                                                suggestions.map { it.kind }.toSet()
                                                    .forEach { SuggestedActionsPrefs.noteShown(context, it) }
                                                showingSuggestions = true
                                            },
                                        )
                                    }
                                }
                                if (onSave != null) {
                                    add { MenuRow(saveLabel, Icons.Default.SaveAlt, onClick = onSave) }
                                }
                                add {
                                    MenuRow(
                                        stringResource(R.string.chat_delete_for_me),
                                        Icons.Default.DeleteOutline,
                                        iconTint = danger,
                                        textColor = danger,
                                        onClick = onDeleteForMe,
                                    )
                                }
                                if (canDeleteForEveryone) {
                                    add {
                                        MenuRow(
                                            stringResource(R.string.chat_delete_for_all),
                                            Icons.Default.DeleteOutline,
                                            iconTint = danger,
                                            textColor = danger,
                                            onClick = onDeleteForEveryone,
                                        )
                                    }
                                }
                            }
                            rows.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                                row()
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val bar = measurables[0].measure(loose)
            val menu = measurables[1].measure(loose)
            val gap = 8.dp.roundToPx()
            val edge = 8.dp.roundToPx()
            val w = constraints.maxWidth
            val h = constraints.maxHeight

            // Allineati al bordo della bolla (a destra per i propri messaggi), dentro lo schermo.
            fun xFor(width: Int): Int {
                val x = if (isOwn) bubble.right.toInt() - width else bubble.left.toInt()
                return min(max(edge, x), w - width - edge)
            }

            val fitsBelow = bubble.bottom + gap + menu.height <= h - edge
            val menuTop = if (fitsBelow) {
                bubble.bottom.toInt() + gap
            } else {
                max(edge + bar.height + gap, bubble.top.toInt() - gap - menu.height)
            }
            val barTop = if (fitsBelow) {
                max(edge, bubble.top.toInt() - gap - bar.height)
            } else {
                max(edge, menuTop - gap - bar.height)
            }
            layout(w, h) {
                bar.place(xFor(bar.width), barTop)
                menu.place(xFor(menu.width), min(menuTop, h - menu.height - edge))
            }
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.kidBoxColors.title,
    textColor: Color = MaterialTheme.kidBoxColors.title,
    trailingChevron: Boolean = false,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (trailingChevron) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier.size(18.dp),
            )
        }
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(start = 8.dp).size(22.dp))
    }
}
