package it.vittorioscocca.kidbox.ui.screens.news

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Orange = Color(0xFFFF6B00)
private val DeleteRed = Color(0xFFD93A3A)

@HiltViewModel
class NewsSavedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val store: NewsSavedStore,
) : ViewModel() {
    init {
        store.start()
    }

    fun remove(ids: Set<String>) = store.remove(ids)

    fun restore(saved: NewsSavedItem) = store.restore(saved)

    fun removeAll() = store.removeAll()

    fun opened(saved: NewsSavedItem) =
        AppAnalytics.newsItemOpened(context, "saved", saved.item.category, saved.item.level)
}

/**
 * Le notizie salvate col segnalibro (dall'icona in alto nella scheda Notizie).
 * Dalla più recente; si aprono come nella scheda, si eliminano scorrendo verso
 * sinistra (con «Annulla»), a gruppi tenendo premuto o da «Seleziona», o tutte
 * insieme. Sono di chi le salva ([NewsSavedStore]), sincronizzate fra i suoi
 * dispositivi. Stessa schermata di iOS (`NewsSavedView.swift`).
 */
@Composable
fun NewsSavedScreen(
    onBack: () -> Unit,
    viewModel: NewsSavedViewModel = hiltViewModel(),
) {
    val state by viewModel.store.state.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    /** null = non si sta selezionando. */
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    // Tolte da un altro dispositivo: fuori anche dalla selezione.
    LaunchedEffect(state.ids) {
        selection = selection?.intersect(state.ids)?.takeIf { state.items.isNotEmpty() }
    }
    BackHandler(enabled = selection != null) { selection = null }

    fun open(saved: NewsSavedItem) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(saved.item.url))) }
        viewModel.opened(saved)
    }

    fun deleteWithUndo(saved: NewsSavedItem) {
        viewModel.remove(setOf(saved.id))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.news_saved_deleted),
                actionLabel = context.getString(R.string.news_saved_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(saved)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val current = selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 16.dp, end = 8.dp),
            ) {
                if (current == null) {
                    KBBackButton(onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    if (state.items.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.news_saved_more), tint = kb.title)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.news_saved_select)) },
                                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        selection = emptySet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.news_saved_delete_all), color = DeleteRed) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = DeleteRed) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDeleteAll = true
                                    },
                                )
                            }
                        }
                    }
                } else {
                    IconButton(onClick = { selection = null }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.news_saved_close_selection), tint = kb.title)
                    }
                    Text(
                        pluralStringResource(R.plurals.news_saved_selected, current.size, current.size),
                        color = kb.title,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    val allSelected = current.size == state.items.size
                    TextButton(onClick = { selection = if (allSelected) emptySet() else state.ids }) {
                        Text(
                            stringResource(if (allSelected) R.string.news_saved_deselect_all else R.string.news_saved_select_all),
                            color = Orange,
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.remove(current)
                            selection = null
                        },
                        enabled = current.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.news_saved_delete_selected),
                            tint = if (current.isNotEmpty()) DeleteRed else kb.subtitle,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.news_saved_title),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )

            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Orange, modifier = Modifier.size(24.dp))
                }
                state.items.isEmpty() -> EmptySaved()
                else -> {
                    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + bottom),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.id }) { saved ->
                            SavedRow(
                                saved = saved,
                                selecting = current != null,
                                selected = current?.contains(saved.id) == true,
                                onOpen = { open(saved) },
                                onToggleSelected = {
                                    selection = current?.let { if (saved.id in it) it - saved.id else it + saved.id }
                                },
                                onStartSelection = { selection = setOf(saved.id) },
                                onDelete = { deleteWithUndo(saved) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.news_saved_delete_all_title)) },
            text = { Text(stringResource(R.string.news_saved_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAll()
                    confirmDeleteAll = false
                    selection = null
                }) { Text(stringResource(R.string.news_saved_delete_all), color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.news_saved_cancel)) }
            },
        )
    }
}

/**
 * Una notizia salvata. Fuori dalla selezione: tocco = apri, pressione lunga =
 * selezione, scorrimento verso sinistra = elimina. Nella selezione il tocco
 * sceglie la notizia e lo scorrimento è spento.
 */
@Composable
private fun SavedRow(
    saved: NewsSavedItem,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelected: () -> Unit,
    onStartSelection: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    if (selecting) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Orange else kb.subtitle,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                NewsItemCard(item = saved.item, placeName = saved.placeName, onOpen = onToggleSelected)
            }
        }
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeleteRed)
                        .padding(end = 24.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.news_saved_delete), tint = Color.White)
                }
            }
        },
    ) {
        NewsItemCard(
            item = saved.item,
            placeName = saved.placeName,
            onOpen = onOpen,
            onLongClick = onStartSelection,
        )
    }
}

@Composable
private fun EmptySaved() {
    val kb = MaterialTheme.kidBoxColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 80.dp),
    ) {
        Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.news_saved_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = kb.title,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.news_saved_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = kb.subtitle,
            textAlign = TextAlign.Center,
        )
    }
}
