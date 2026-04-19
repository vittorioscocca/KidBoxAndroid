package it.vittorioscocca.kidbox.ui.screens.notes

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NoteSection(val label: String) {
    PINNED("In evidenza"),
    LAST_7("Ultimi 7 giorni"),
    LAST_30("Ultimi 30 giorni"),
    OLDER("Più vecchie"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesHomeScreen(
    familyId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: NotesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("kidbox_notes_prefs", Context.MODE_PRIVATE) }
    var searchQuery by remember(familyId) { mutableStateOf("") }
    var isSelecting by remember(familyId) { mutableStateOf(false) }
    var selectedIds by remember(familyId) { mutableStateOf(setOf<String>()) }
    var pinnedIds by remember(familyId) { mutableStateOf(setOf<String>()) }
    val allVisibleIds = remember(state.notes, searchQuery) {
        state.notes.filter { note ->
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            if (q.isBlank()) true else {
                note.title.lowercase(Locale.getDefault()).contains(q) ||
                    note.body.htmlToPlainText().lowercase(Locale.getDefault()).contains(q)
            }
        }.map { it.id }
    }

    LaunchedEffect(familyId) { viewModel.bind(familyId) }
    LaunchedEffect(familyId) {
        pinnedIds = prefs.getStringSet("pinned_$familyId", emptySet()).orEmpty()
    }

    fun persistPinned(ids: Set<String>) {
        pinnedIds = ids
        prefs.edit().putStringSet("pinned_$familyId", ids).apply()
    }

    val sectioned = remember(state.notes, searchQuery, pinnedIds) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        val filtered = state.notes.filter {
            if (q.isBlank()) true else {
                it.title.lowercase(Locale.getDefault()).contains(q) ||
                    it.body.htmlToPlainText().lowercase(Locale.getDefault()).contains(q)
            }
        }.sortedByDescending { it.updatedAtEpochMillis }
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val pinned = filtered.filter { pinnedIds.contains(it.id) }
        val unpinned = filtered.filterNot { pinnedIds.contains(it.id) }
        val week7 = unpinned.filter { now - it.updatedAtEpochMillis <= 7 * dayMs }
        val days30 = unpinned.filter { now - it.updatedAtEpochMillis in (7 * dayMs + 1)..(30 * dayMs) }
        val older = unpinned.filter { now - it.updatedAtEpochMillis > 30 * dayMs }
        buildList {
            if (pinned.isNotEmpty()) add(NoteSection.PINNED to pinned)
            if (week7.isNotEmpty()) add(NoteSection.LAST_7 to week7)
            if (days30.isNotEmpty()) add(NoteSection.LAST_30 to days30)
            if (older.isNotEmpty()) add(NoteSection.OLDER to older)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NoteHeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSelecting) "Fine" else "Seleziona",
                    color = kb.subtitle,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable {
                            isSelecting = !isSelecting
                            if (!isSelecting) selectedIds = emptySet()
                        },
                )
                NoteHeaderCircleButton(
                    icon = Icons.Default.Add,
                    onClick = {
                        viewModel.createEmptyNote { noteId ->
                            onNavigate(AppDestination.NoteDetail.createRoute(familyId = familyId, noteId = noteId))
                        }
                    },
                )
            }
        }

        Text(
            text = "Note",
            color = kb.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cerca nelle note") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = kb.card,
                unfocusedContainerColor = kb.card,
                disabledContainerColor = kb.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        if (isSelecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (selectedIds.isEmpty()) "Seleziona note" else "${selectedIds.size} selezionate",
                    color = kb.subtitle,
                    fontSize = 13.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectedIds.size == allVisibleIds.size && allVisibleIds.isNotEmpty()) "Deseleziona tutto" else "Seleziona tutto",
                        color = kb.subtitle,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            selectedIds = if (selectedIds.size == allVisibleIds.size) emptySet() else allVisibleIds.toSet()
                        },
                    )
                    IconButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = {
                            viewModel.deleteNotes(selectedIds)
                            selectedIds = emptySet()
                            isSelecting = false
                        },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Elimina selezione",
                            tint = if (selectedIds.isEmpty()) kb.subtitle else Color(0xFFD62828),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Caricamento note...", color = kb.subtitle)
                }
            }

            state.notes.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessuna nota. Tocca + per crearne una.", color = kb.subtitle)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sectioned.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Nessun risultato", color = kb.subtitle)
                            }
                        }
                    } else {
                        sectioned.forEach { (section, notes) ->
                            item(key = "section-${section.name}") {
                                Text(
                                    text = section.label.uppercase(Locale.getDefault()),
                                    color = kb.subtitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }
                            items(notes, key = { it.id }) { note ->
                                val isPinned = pinnedIds.contains(note.id)
                                val isSelected = selectedIds.contains(note.id)
                                val cardColor = animatedCardColor(isSelected, kb.card)
                                val cardElevation = animatedCardElevation(isSelected)
                                val selectionTint = animatedSelectionTint(isSelected, kb.subtitle)
                                val selectionScale = animatedSelectionScale(isSelected)
                                val pinTint = animatedPinTint(isPinned, kb.subtitle)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isSelecting = true
                                                selectedIds = selectedIds + note.id
                                            },
                                            onClick = {
                                                if (isSelecting) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedIds = if (isSelected) selectedIds - note.id else selectedIds + note.id
                                                } else {
                                                    onNavigate(AppDestination.NoteDetail.createRoute(familyId = familyId, noteId = note.id))
                                                }
                                            },
                                        ),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isSelecting) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = selectionTint,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(end = 6.dp)
                                                        .graphicsLayer {
                                                            scaleX = selectionScale
                                                            scaleY = selectionScale
                                                        },
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isPinned) Icons.Default.PinDrop else Icons.AutoMirrored.Filled.Note,
                                                contentDescription = null,
                                                tint = pinTint,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .padding(end = 6.dp),
                                            )
                                            Text(
                                                text = note.title.ifBlank { "Senza titolo" },
                                                color = kb.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (!isSelecting) {
                                                IconButton(
                                                    modifier = Modifier
                                                        .size(30.dp),
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        persistPinned(
                                                            if (isPinned) pinnedIds - note.id else pinnedIds + note.id,
                                                        )
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.Default.PinDrop,
                                                        contentDescription = "Pin",
                                                        tint = pinTint,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                                IconButton(
                                                    modifier = Modifier.size(30.dp),
                                                    onClick = { viewModel.deleteNote(note.id) },
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Elimina",
                                                        tint = kb.subtitle,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = previewFor(note.body),
                                            color = kb.subtitle,
                                            maxLines = 2,
                                            fontSize = 14.sp,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = formatDate(note.updatedAtEpochMillis),
                                            color = kb.subtitle,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun previewFor(text: String): String {
    val plain = text.htmlToPlainText()
    if (plain.isBlank()) return "Nessun contenuto"
    return plain.replace('\n', ' ')
}

private fun formatDate(epochMillis: Long): String {
    val date = Date(epochMillis)
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.time = date
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)

        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 ->
            "Ieri"

        else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
    }
}

@Composable
private fun NoteHeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

@Composable
private fun animatedCardColor(
    selected: Boolean,
    base: Color,
): Color = animateColorAsState(
    targetValue = if (selected) base.copy(alpha = 0.75f) else base,
    label = "note_card_color",
).value

@Composable
private fun animatedSelectionTint(
    selected: Boolean,
    subtitle: Color,
): Color = animateColorAsState(
    targetValue = if (selected) Color(0xFFFF6B00) else subtitle,
    label = "note_selection_tint",
).value

@Composable
private fun animatedPinTint(
    pinned: Boolean,
    subtitle: Color,
): Color = animateColorAsState(
    targetValue = if (pinned) Color(0xFFFFA000) else subtitle,
    label = "note_pin_tint",
).value

@Composable
private fun animatedSelectionScale(
    selected: Boolean,
): Float = animateFloatAsState(
    targetValue = if (selected) 1f else 0.92f,
    label = "note_selection_scale",
).value

@Composable
private fun animatedCardElevation(
    selected: Boolean,
): androidx.compose.ui.unit.Dp = androidx.compose.animation.core.animateDpAsState(
    targetValue = if (selected) 3.dp else 1.dp,
    label = "note_card_elevation",
).value
