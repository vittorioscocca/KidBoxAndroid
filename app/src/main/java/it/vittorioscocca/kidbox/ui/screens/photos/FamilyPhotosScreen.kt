package it.vittorioscocca.kidbox.ui.screens.photos

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.media.MediaMetadataRetriever
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PhotosTab { LIBRARY, ALBUMS }
private enum class PhotoGroupMode { DAY, MONTH, YEAR }

@Composable
fun FamilyPhotosScreen(
    onBack: () -> Unit,
    viewModel: FamilyPhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(PhotosTab.LIBRARY) }
    var showCreateAlbum by remember { mutableStateOf(false) }
    var viewerPhotoId by remember { mutableStateOf<String?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAlbumActionPicker by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var longPressMenuPhoto by remember { mutableStateOf<KBFamilyPhotoEntity?>(null) }
    var groupMode by remember { mutableStateOf(PhotoGroupMode.DAY) }

    val multiMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importMediaBatch(uris, state.selectedAlbumId)
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (saved && uri != null) {
            viewModel.importMedia(uri, state.selectedAlbumId)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.filteredPhotos, viewerPhotoId) {
        val selectedId = viewerPhotoId ?: return@LaunchedEffect
        if (state.filteredPhotos.none { it.id == selectedId }) {
            viewerPhotoId = null
        }
    }
    LaunchedEffect(state.filteredPhotos, currentTab) {
        if (currentTab != PhotosTab.LIBRARY) {
            isSelectionMode = false
            selectedPhotoIds = emptySet()
            return@LaunchedEffect
        }
        val visibleIds = state.filteredPhotos.map { it.id }.toSet()
        selectedPhotoIds = selectedPhotoIds.intersect(visibleIds)
        if (selectedPhotoIds.isEmpty()) isSelectionMode = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TopHeader(
                tab = currentTab,
                isSelectionMode = isSelectionMode,
                groupMode = groupMode,
                onBack = onBack,
                onToggleSelection = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedPhotoIds = emptySet()
                    } else {
                        isSelectionMode = true
                    }
                },
                onGroupModeSelected = { groupMode = it },
                onCamera = {
                    val uri = createCaptureUri(context) ?: run {
                        Toast.makeText(context, "Impossibile aprire la fotocamera", Toast.LENGTH_LONG).show()
                        return@TopHeader
                    }
                    pendingCaptureUri = uri
                    takePictureLauncher.launch(uri)
                },
                onPlus = {
                    if (currentTab == PhotosTab.ALBUMS) {
                        showCreateAlbum = true
                    } else {
                        multiMediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Foto e Video",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.kidBoxColors.title,
            )

            Spacer(Modifier.height(8.dp))
            TabSwitcher(
                selectedTab = currentTab,
                onSelect = { currentTab = it },
            )

            if (currentTab == PhotosTab.LIBRARY && state.selectedAlbumId != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.selectAlbum(null) }) {
                    Text("Mostra tutta la libreria")
                }
            }

            if (currentTab == PhotosTab.LIBRARY && isSelectionMode) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Color(0xFF1E88E5),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = "${selectedPhotoIds.size} selezionati",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val visibleIds = state.filteredPhotos.map { it.id }.toSet()
                            selectedPhotoIds = if (selectedPhotoIds.size == visibleIds.size) emptySet() else visibleIds
                            if (selectedPhotoIds.isEmpty()) isSelectionMode = false
                        },
                    ) {
                        Text(if (selectedPhotoIds.size == state.filteredPhotos.size) "Deseleziona tutto" else "Seleziona tutto")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            when (currentTab) {
                PhotosTab.LIBRARY -> {
                    LibraryContent(
                        isLoading = state.isLoading,
                        photos = state.filteredPhotos,
                        groupMode = groupMode,
                        isSelectionMode = isSelectionMode,
                        selectedPhotoIds = selectedPhotoIds,
                        uploadingPhotoIds = state.uploadingPhotoIds,
                        onEmptyPick = {
                            multiMediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        onEmptyCamera = {
                            val uri = createCaptureUri(context) ?: run {
                                Toast.makeText(context, "Impossibile aprire la fotocamera", Toast.LENGTH_LONG).show()
                                return@LibraryContent
                            }
                            pendingCaptureUri = uri
                            takePictureLauncher.launch(uri)
                        },
                        onPhotoTap = { photo ->
                            if (isSelectionMode) {
                                selectedPhotoIds = if (selectedPhotoIds.contains(photo.id)) {
                                    selectedPhotoIds - photo.id
                                } else {
                                    selectedPhotoIds + photo.id
                                }
                                if (selectedPhotoIds.isEmpty()) isSelectionMode = false
                            } else {
                                viewerPhotoId = photo.id
                            }
                        },
                        onPhotoLongPress = { photo ->
                            longPressMenuPhoto = photo
                        },
                    )
                }

                PhotosTab.ALBUMS -> {
                    AlbumsContent(
                        isLoading = state.isLoading,
                        albums = state.albums,
                        allPhotos = state.photos,
                        onCreateAlbum = { showCreateAlbum = true },
                        onOpenAlbum = { album ->
                            viewModel.selectAlbum(album.id)
                            currentTab = PhotosTab.LIBRARY
                        },
                    )
                }
            }
        }

        if (isSelectionMode && currentTab == PhotosTab.LIBRARY) {
            SelectionActionBar(
                selectedCount = selectedPhotoIds.size,
                canSetCover = selectedPhotoIds.size == 1 && state.selectedAlbumId != null,
                canRemoveFromAlbum = selectedPhotoIds.isNotEmpty() && state.selectedAlbumId != null,
                onAdd = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        isMoveAction = false
                        showAlbumActionPicker = true
                    }
                },
                onMove = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        isMoveAction = true
                        showAlbumActionPicker = true
                    }
                },
                onRemove = {
                    viewModel.removePhotosFromCurrentAlbum(selectedPhotoIds)
                    selectedPhotoIds = emptySet()
                    isSelectionMode = false
                },
                onSetCover = {
                    selectedPhotoIds.firstOrNull()?.let { single ->
                        viewModel.setCurrentAlbumCover(single)
                        selectedPhotoIds = emptySet()
                        isSelectionMode = false
                    }
                },
                onDelete = {
                    viewModel.deletePhotos(selectedPhotoIds)
                    selectedPhotoIds = emptySet()
                    isSelectionMode = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }

    if (showCreateAlbum) {
        CreateAlbumDialog(
            onDismiss = { showCreateAlbum = false },
            onCreate = { title ->
                viewModel.createAlbum(title)
                showCreateAlbum = false
            },
        )
    }
    if (showAlbumActionPicker) {
        AlbumSelectionDialog(
            albums = state.albums,
            onDismiss = { showAlbumActionPicker = false },
            onSelectAlbum = { albumId ->
                if (isMoveAction) viewModel.movePhotosToAlbum(selectedPhotoIds, albumId)
                else viewModel.addPhotosToAlbum(selectedPhotoIds, albumId)
                selectedPhotoIds = emptySet()
                isSelectionMode = false
                showAlbumActionPicker = false
            },
        )
    }

    longPressMenuPhoto?.let { photo ->
        PhotoLongPressMenuDialog(
            onDismiss = { longPressMenuPhoto = null },
            onOpen = {
                longPressMenuPhoto = null
                viewerPhotoId = photo.id
            },
            onSelect = {
                longPressMenuPhoto = null
                isSelectionMode = true
                selectedPhotoIds = setOf(photo.id)
            },
            onDelete = {
                longPressMenuPhoto = null
                viewModel.deletePhoto(photo.id)
            },
        )
    }

    val startIndex = viewerPhotoId?.let { id -> state.filteredPhotos.indexOfFirst { it.id == id } } ?: -1
    if (startIndex >= 0) {
        FullscreenMediaViewer(
            photos = state.filteredPhotos,
            startIndex = startIndex,
            onDismiss = { viewerPhotoId = null },
            onDelete = { photo -> viewModel.deletePhoto(photo.id) },
            onOpenExternal = { photo, scope ->
                scope.launch {
                    runCatching {
                        val file = withContext(Dispatchers.IO) { viewModel.preparePreviewFile(photo) }
                        openMedia(context, photo.mimeType, file)
                    }.onFailure {
                        Toast.makeText(context, "Impossibile aprire il media", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSaveEditedCopy = { photo, jpegBytes ->
                viewModel.saveEditedPhotoCopy(photo, jpegBytes)
            },
            prepareFile = { photo -> viewModel.preparePreviewFile(photo) },
        )
    }
}

@Composable
private fun TopHeader(
    tab: PhotosTab,
    isSelectionMode: Boolean,
    groupMode: PhotoGroupMode,
    onBack: () -> Unit,
    onToggleSelection: () -> Unit,
    onGroupModeSelected: (PhotoGroupMode) -> Unit,
    onCamera: () -> Unit,
    onPlus: () -> Unit,
) {
    var showGroupingMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
        if (tab == PhotosTab.LIBRARY) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderCircleButton(
                    icon = if (isSelectionMode) Icons.Default.Done else Icons.Default.DoneAll,
                    onClick = onToggleSelection,
                )
                Box {
                    HeaderCircleButton(icon = Icons.Default.Tune, onClick = { showGroupingMenu = true })
                    DropdownMenu(
                        expanded = showGroupingMenu,
                        onDismissRequest = { showGroupingMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Giorno") },
                            onClick = {
                                onGroupModeSelected(PhotoGroupMode.DAY)
                                showGroupingMenu = false
                            },
                            leadingIcon = if (groupMode == PhotoGroupMode.DAY) {
                                { Icon(Icons.Default.Done, contentDescription = null) }
                            } else null,
                        )
                        DropdownMenuItem(
                            text = { Text("Mese") },
                            onClick = {
                                onGroupModeSelected(PhotoGroupMode.MONTH)
                                showGroupingMenu = false
                            },
                            leadingIcon = if (groupMode == PhotoGroupMode.MONTH) {
                                { Icon(Icons.Default.Done, contentDescription = null) }
                            } else null,
                        )
                        DropdownMenuItem(
                            text = { Text("Anno") },
                            onClick = {
                                onGroupModeSelected(PhotoGroupMode.YEAR)
                                showGroupingMenu = false
                            },
                            leadingIcon = if (groupMode == PhotoGroupMode.YEAR) {
                                { Icon(Icons.Default.Done, contentDescription = null) }
                            } else null,
                        )
                    }
                }
                HeaderCircleButton(icon = Icons.Default.CameraAlt, onClick = onCamera)
                HeaderCircleButton(icon = Icons.Default.Add, onClick = onPlus)
            }
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

@Composable
private fun TabSwitcher(
    selectedTab: PhotosTab,
    onSelect: (PhotosTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.kidBoxColors.divider,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            TabPill(
                label = "Libreria",
                selected = selectedTab == PhotosTab.LIBRARY,
                onClick = { onSelect(PhotosTab.LIBRARY) },
                modifier = Modifier.weight(1f),
            )
            TabPill(
                label = "Album",
                selected = selectedTab == PhotosTab.ALBUMS,
                onClick = { onSelect(PhotosTab.ALBUMS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LibraryContent(
    isLoading: Boolean,
    photos: List<KBFamilyPhotoEntity>,
    groupMode: PhotoGroupMode,
    isSelectionMode: Boolean,
    selectedPhotoIds: Set<String>,
    uploadingPhotoIds: Set<String>,
    onEmptyPick: () -> Unit,
    onEmptyCamera: () -> Unit,
    onPhotoTap: (KBFamilyPhotoEntity) -> Unit,
    onPhotoLongPress: (KBFamilyPhotoEntity) -> Unit,
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6B00))
        }
        return
    }
    if (photos.isEmpty()) {
        EmptyLibraryState(
            onPick = onEmptyPick,
            onCamera = onEmptyCamera,
        )
        return
    }
    val sections = remember(photos, groupMode) { buildPhotoSections(photos, groupMode) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            items(
                items = listOf(section),
                key = { "header_${it.title}" },
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = it.title,
                    color = MaterialTheme.kidBoxColors.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            items(section.photos, key = { it.id }) { photo ->
                PhotoGridItem(
                    photo = photo,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPhotoIds.contains(photo.id),
                    isUploading = uploadingPhotoIds.contains(photo.id) || KBSyncState.fromRaw(photo.syncStateRaw) == KBSyncState.PENDING_UPSERT,
                    onOpen = { onPhotoTap(photo) },
                    onLongPress = { onPhotoLongPress(photo) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsContent(
    isLoading: Boolean,
    albums: List<KBPhotoAlbumEntity>,
    allPhotos: List<KBFamilyPhotoEntity>,
    onCreateAlbum: () -> Unit,
    onOpenAlbum: (KBPhotoAlbumEntity) -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6B00))
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            onClick = onCreateAlbum,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.kidBoxColors.card,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 2.dp, top = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
                Text(
                    text = "Nuovo album",
                    color = MaterialTheme.kidBoxColors.title,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                val cover = allPhotos.firstOrNull { it.id == album.coverPhotoId }
                AlbumCard(
                    albumTitle = album.title,
                    coverPhoto = cover,
                    onClick = { onOpenAlbum(album) },
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    canSetCover: Boolean,
    canRemoveFromAlbum: Boolean,
    onAdd: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onSetCover: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.kidBoxColors.card,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionRow(
                label = "Aggiungi",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                enabled = selectedCount > 0,
                onClick = onAdd,
            )
            ActionRow(
                label = "Sposta",
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                enabled = selectedCount > 0,
                onClick = onMove,
            )
            ActionRow(
                label = "Rimuovi",
                icon = Icons.Default.RemoveCircleOutline,
                enabled = canRemoveFromAlbum,
                onClick = onRemove,
            )
            ActionRow(
                label = "Copertina",
                icon = Icons.Default.Image,
                enabled = canSetCover,
                onClick = onSetCover,
            )
            ActionRow(
                label = "Elimina",
                icon = Icons.Default.Delete,
                enabled = selectedCount > 0,
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onPick: () -> Unit,
    onCamera: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Nessuna foto",
            color = MaterialTheme.kidBoxColors.title,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Aggiungi le prime foto condivise della famiglia.",
            color = MaterialTheme.kidBoxColors.subtitle,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = onPick,
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFF2D6F),
        ) {
            Text(
                text = "Aggiungi foto e video",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onCamera,
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFF2D6F).copy(alpha = 0.15f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFFFF2D6F),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Scatta una foto",
                    color = Color(0xFFFF2D6F),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.kidBoxColors.card else Color.Transparent,
        modifier = modifier,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            color = MaterialTheme.kidBoxColors.title,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class PhotoSection(
    val title: String,
    val photos: List<KBFamilyPhotoEntity>,
)

private fun buildPhotoSections(
    photos: List<KBFamilyPhotoEntity>,
    mode: PhotoGroupMode,
): List<PhotoSection> {
    if (photos.isEmpty()) return emptyList()
    val locale = Locale.ITALIAN
    val keyFormat = when (mode) {
        PhotoGroupMode.DAY -> SimpleDateFormat("yyyy-MM-dd", locale)
        PhotoGroupMode.MONTH -> SimpleDateFormat("yyyy-MM", locale)
        PhotoGroupMode.YEAR -> SimpleDateFormat("yyyy", locale)
    }
    val titleFormat = when (mode) {
        PhotoGroupMode.DAY -> SimpleDateFormat("EEEE d MMMM yyyy", locale)
        PhotoGroupMode.MONTH -> SimpleDateFormat("MMMM yyyy", locale)
        PhotoGroupMode.YEAR -> SimpleDateFormat("yyyy", locale)
    }
    val grouped = LinkedHashMap<String, MutableList<KBFamilyPhotoEntity>>()
    photos.forEach { photo ->
        val date = Date(photo.takenAtEpochMillis)
        val key = keyFormat.format(date)
        grouped.getOrPut(key) { mutableListOf() }.add(photo)
    }
    return grouped.entries.map { (key, list) ->
        val titleDate = keyFormat.parse(key) ?: Date(list.first().takenAtEpochMillis)
        PhotoSection(
            title = titleFormat.format(titleDate).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
            },
            photos = list,
        )
    }
}

@Composable
private fun AlbumCard(
    albumTitle: String,
    coverPhoto: KBFamilyPhotoEntity?,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            val thumbBytes = coverPhoto?.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            if (thumbBytes != null) {
                AsyncImage(
                    model = thumbBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.kidBoxColors.rowBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle)
                }
            }
        }
        Text(
            text = albumTitle,
            color = MaterialTheme.kidBoxColors.title,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreateAlbumCard(
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.kidBoxColors.rowBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle)
        }
        Text(
            text = "Nuovo album",
            color = MaterialTheme.kidBoxColors.title,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    photo: KBFamilyPhotoEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isUploading: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumbBytes = remember(photo.thumbnailBase64) {
                photo.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            }
            if (thumbBytes != null) {
                AsyncImage(
                    model = thumbBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.kidBoxColors.rowBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle)
                }
            }

            if (photo.mimeType.startsWith("video/")) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatDuration(photo.videoDurationSeconds),
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
            }

            if (isSelectionMode) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        text = if (isSelected) "✓" else "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoLongPressMenuDialog(
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Azioni foto") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onOpen,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.kidBoxColors.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Apri",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = MaterialTheme.kidBoxColors.title,
                    )
                }
                Surface(
                    onClick = onSelect,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.kidBoxColors.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Seleziona",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = MaterialTheme.kidBoxColors.title,
                    )
                }
                Surface(
                    onClick = onDelete,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Elimina",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Color(0xFFE35156),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}

@Composable
private fun ActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = when {
            !enabled -> MaterialTheme.kidBoxColors.divider
            destructive -> Color(0xFFFFEBEE)
            else -> MaterialTheme.kidBoxColors.rowBackground
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.kidBoxColors.subtitle
                    destructive -> Color(0xFFE35156)
                    else -> MaterialTheme.kidBoxColors.title
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = when {
                    !enabled -> MaterialTheme.kidBoxColors.subtitle
                    destructive -> Color(0xFFE35156)
                    else -> MaterialTheme.kidBoxColors.title
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AlbumSelectionDialog(
    albums: List<KBPhotoAlbumEntity>,
    onDismiss: () -> Unit,
    onSelectAlbum: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                albums.forEach { album ->
                    Surface(
                        onClick = { onSelectAlbum(album.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.kidBoxColors.card,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = album.title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = MaterialTheme.kidBoxColors.title,
                        )
                    }
                }
                if (albums.isEmpty()) {
                    Text("Nessun album disponibile", color = MaterialTheme.kidBoxColors.subtitle)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}

@Composable
private fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo album") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nome album") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim()) },
                enabled = title.isNotBlank(),
            ) { Text("Crea") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}

private fun formatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null || durationSeconds <= 0.0) return "00:00"
    val total = durationSeconds.toInt()
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenMediaViewer(
    photos: List<KBFamilyPhotoEntity>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (KBFamilyPhotoEntity) -> Unit,
    onOpenExternal: (KBFamilyPhotoEntity, CoroutineScope) -> Unit,
    onSaveEditedCopy: (KBFamilyPhotoEntity, ByteArray) -> Unit,
    prepareFile: suspend (KBFamilyPhotoEntity) -> File,
) {
    FullscreenSystemBarsEffect()
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preparedFiles = remember { mutableStateMapOf<String, File>() }
    val loadingIds = remember { mutableStateMapOf<String, Boolean>() }
    var showEditorForPhoto by remember { mutableStateOf<KBFamilyPhotoEntity?>(null) }
    var editorBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val photo = photos[page]
                val localFile: File? = preparedFiles[photo.id]?.takeIf { file -> file.exists() }
                    ?: photo.localPath?.let { path -> File(path) }?.takeIf { file -> file.exists() }
                val videoFrameBitmap = remember(photo.id, localFile?.absolutePath) {
                    if (!photo.mimeType.startsWith("video/")) {
                        null
                    } else {
                        localFile?.let(::extractVideoFrameBitmap)
                    }
                }
                LaunchedEffect(photo.id) {
                    if (localFile == null && loadingIds[photo.id] != true) {
                        loadingIds[photo.id] = true
                        runCatching { withContext(Dispatchers.IO) { prepareFile(photo) } }
                            .onSuccess { preparedFiles[photo.id] = it }
                            .onFailure {
                                Toast.makeText(context, "Errore caricamento media", Toast.LENGTH_SHORT).show()
                            }
                        loadingIds[photo.id] = false
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (photo.mimeType.startsWith("video/")) {
                        AsyncImage(
                            model = videoFrameBitmap ?: photo.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() } ?: localFile,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            onClick = { onOpenExternal(photo, scope) },
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.18f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Text(
                                    text = "Apri video",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = localFile ?: photo.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() },
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (loadingIds[photo.id] == true) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1}/${photos.size}",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = currentPhoto?.let { formatHeaderDate(it.takenAtEpochMillis) }.orEmpty(),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        val isSelected = currentPhoto?.id == photo.id
                        val thumb = photo.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
                        Surface(
                            onClick = {
                                val target = photos.indexOfFirst { it.id == photo.id }
                                if (target >= 0) scope.launch { pagerState.animateScrollToPage(target) }
                            },
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = if (isSelected) 4.dp else 0.dp,
                            color = if (isSelected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.White) else null,
                        ) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                modifier = Modifier.size(58.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionLabel(
                        label = "Condividi",
                        onClick = {
                            currentPhoto?.let { photo ->
                                scope.launch {
                                    runCatching {
                                        val file = withContext(Dispatchers.IO) { prepareFile(photo) }
                                        shareMedia(context, photo.mimeType, file)
                                    }.onFailure {
                                        Toast.makeText(context, "Impossibile condividere il file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                    ActionLabel(
                        label = "Modifica",
                        enabled = currentPhoto?.mimeType?.startsWith("image/") == true,
                        onClick = {
                            currentPhoto?.let { photo ->
                                scope.launch {
                                    runCatching {
                                        val file = withContext(Dispatchers.IO) { prepareFile(photo) }
                                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                            ?: error("Anteprima non disponibile")
                                        editorBitmap = bitmap
                                        showEditorForPhoto = photo
                                    }.onFailure {
                                        Toast.makeText(context, "Impossibile aprire editor foto", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                    ActionLabel(
                        label = "Elimina",
                        destructive = true,
                        onClick = { currentPhoto?.let { onDelete(it) } },
                    )
                }
                currentPhoto?.takeIf { it.mimeType.startsWith("video/") }?.let { video ->
                    Text(
                        text = formatDuration(video.videoDurationSeconds),
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 6.dp, end = 4.dp),
                    )
                }
            }
        }
    }

    val editingPhoto = showEditorForPhoto
    val editingBitmap = editorBitmap
    if (editingPhoto != null && editingBitmap != null) {
        PhotoAdjustEditorDialog(
            sourceBitmap = editingBitmap,
            onDismiss = {
                showEditorForPhoto = null
                editorBitmap = null
            },
            onSaveCopy = { bytes ->
                onSaveEditedCopy(editingPhoto, bytes)
                showEditorForPhoto = null
                editorBitmap = null
            },
        )
    }
}

@Composable
private fun ActionLabel(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Text(
        text = label,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = when {
            !enabled -> Color.White.copy(alpha = 0.4f)
            destructive -> Color(0xFFFF5A5F)
            else -> Color.White
        },
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun PhotoAdjustEditorDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onSaveCopy: (ByteArray) -> Unit,
) {
    FullscreenSystemBarsEffect(restoreOnDispose = false)
    var selectedTool by remember { mutableStateOf(EditorTool.ADJUST) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var saturation by remember { mutableStateOf(1f) }
    var warmth by remember { mutableStateOf(1f) }
    var cropPreset by remember { mutableStateOf(CropPreset.ORIGINAL) }
    var filterPreset by remember { mutableStateOf(PhotoFilterPreset.NONE) }
    var textDraft by remember { mutableStateOf("") }
    var textOverlays by remember { mutableStateOf<List<String>>(emptyList()) }
    var stickerOverlays by remember { mutableStateOf<List<String>>(emptyList()) }

    val editedBitmap = remember(
        sourceBitmap,
        brightness,
        contrast,
        saturation,
        warmth,
        cropPreset,
        filterPreset,
        textOverlays,
        stickerOverlays,
    ) {
        renderEditedBitmap(
            source = sourceBitmap,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            warmth = warmth,
            cropPreset = cropPreset,
            filterPreset = filterPreset,
            textOverlays = textOverlays,
            stickerOverlays = stickerOverlays,
        )
    }

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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Annulla", color = Color.White) }
                    TextButton(
                        onClick = {
                            val out = java.io.ByteArrayOutputStream()
                            editedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            onSaveCopy(out.toByteArray())
                        },
                    ) { Text("Salva copia") }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = editedBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorTool.values().forEach { tool ->
                        ToolPill(
                            label = tool.label,
                            selected = selectedTool == tool,
                            onClick = { selectedTool = tool },
                        )
                    }
                }
                when (selectedTool) {
                    EditorTool.CROP -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CropPreset.values().forEach { preset ->
                                ToolPill(
                                    label = preset.label,
                                    selected = cropPreset == preset,
                                    onClick = { cropPreset = preset },
                                )
                            }
                        }
                    }
                    EditorTool.ADJUST -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EditSlider("Luminosita", brightness, -100f..100f) { brightness = it }
                            EditSlider("Contrasto", contrast, 0.2f..2.2f) { contrast = it }
                            EditSlider("Saturazione", saturation, 0f..2f) { saturation = it }
                            EditSlider("Calore", warmth, 0.4f..1.8f) { warmth = it }
                        }
                    }
                    EditorTool.FILTERS -> {
                        val filterPreviewBitmaps = remember(
                            sourceBitmap,
                            cropPreset,
                            brightness,
                            contrast,
                            saturation,
                            warmth,
                        ) {
                            val basePreview = createPreviewBitmap(centerCrop(sourceBitmap, cropPreset), maxSide = 260)
                            PhotoFilterPreset.values().associateWith { preset ->
                                applyFilterAndAdjustments(
                                    source = basePreview,
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    warmth = warmth,
                                    filterPreset = preset,
                                )
                            }
                        }
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(PhotoFilterPreset.values().toList(), key = { it.name }) { preset ->
                                FilterPreviewItem(
                                    title = preset.label,
                                    preview = filterPreviewBitmaps[preset],
                                    selected = filterPreset == preset,
                                    onClick = { filterPreset = preset },
                                )
                            }
                        }
                    }
                    EditorTool.TEXT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = textDraft,
                                onValueChange = { textDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Inserisci testo") },
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolPill(
                                    label = "Aggiungi testo",
                                    selected = false,
                                    onClick = {
                                        val value = textDraft.trim()
                                        if (value.isNotEmpty()) {
                                            textOverlays = textOverlays + value.take(60)
                                            textDraft = ""
                                        }
                                    },
                                )
                                ToolPill(
                                    label = "Rimuovi ultimo",
                                    selected = false,
                                    onClick = {
                                        if (textOverlays.isNotEmpty()) {
                                            textOverlays = textOverlays.dropLast(1)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    EditorTool.STICKERS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("😍", "⭐", "🎉", "🔥", "❤️", "😂", "🧸", "🚀").forEach { sticker ->
                                    ToolPill(
                                        label = sticker,
                                        selected = false,
                                        onClick = { stickerOverlays = stickerOverlays + sticker },
                                    )
                                }
                            }
                            ToolPill(
                                label = "Rimuovi ultimo sticker",
                                selected = false,
                                onClick = {
                                    if (stickerOverlays.isNotEmpty()) {
                                        stickerOverlays = stickerOverlays.dropLast(1)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class EditorTool(val label: String) {
    CROP("Crop"),
    ADJUST("Regola"),
    FILTERS("Filtri"),
    TEXT("Testo"),
    STICKERS("Sticker"),
}

private enum class CropPreset(val label: String) {
    ORIGINAL("Originale"),
    SQUARE("1:1"),
    RATIO_4_5("4:5"),
    RATIO_16_9("16:9"),
}

private enum class PhotoFilterPreset(val label: String) {
    NONE("Originale"),
    VIVID("Vivido"),
    FADE("Fade"),
    MONO("Noir"),
    CHROME("Chrome"),
    WARM("Caldo"),
    COOL("Freddo"),
}

@Composable
private fun ToolPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)) else null,
    ) {
        Text(
            text = label,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun FilterPreviewItem(
    title: String,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
            color = Color.White.copy(alpha = 0.06f),
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(width = 76.dp, height = 76.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 76.dp, height = 76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("—", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        Text(
            text = title,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EditSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, color = Color.White)
            Text(text = value.roundToInt().toString(), color = Color.White.copy(alpha = 0.74f))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

private fun applyBitmapAdjustments(
    source: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    val paint = Paint()

    val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
    val contrastScale = contrast
    val contrastTranslate = (-0.5f * contrastScale + 0.5f) * 255f + brightness
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrastScale, 0f, 0f, 0f, contrastTranslate,
            0f, contrastScale, 0f, 0f, contrastTranslate,
            0f, 0f, contrastScale, 0f, contrastTranslate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val warmBlue = (2f - warmth).coerceIn(0.2f, 2f)
    val warmthMatrix = ColorMatrix(
        floatArrayOf(
            warmth, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, warmBlue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    satMatrix.postConcat(contrastMatrix)
    satMatrix.postConcat(warmthMatrix)
    paint.colorFilter = ColorMatrixColorFilter(satMatrix)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

private fun renderEditedBitmap(
    source: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
    cropPreset: CropPreset,
    filterPreset: PhotoFilterPreset,
    textOverlays: List<String>,
    stickerOverlays: List<String>,
): Bitmap {
    val cropped = centerCrop(source, cropPreset)
    val tuned = applyFilterAndAdjustments(
        source = cropped,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        warmth = warmth,
        filterPreset = filterPreset,
    )
    return drawOverlays(tuned, textOverlays, stickerOverlays)
}

private fun applyFilterAndAdjustments(
    source: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
    filterPreset: PhotoFilterPreset,
): Bitmap {
    val preset = when (filterPreset) {
        PhotoFilterPreset.NONE -> floatArrayOf(0f, 1f, 1f, 1f)
        PhotoFilterPreset.VIVID -> floatArrayOf(8f, 1.08f, 1.25f, 1.02f)
        PhotoFilterPreset.COOL -> floatArrayOf(-6f, 1f, 0.95f, 0.8f)
        PhotoFilterPreset.WARM -> floatArrayOf(6f, 1f, 1.05f, 1.25f)
        PhotoFilterPreset.MONO -> floatArrayOf(0f, 1.05f, 0f, 1f)
        PhotoFilterPreset.CHROME -> floatArrayOf(4f, 1.14f, 1.18f, 1.05f)
        PhotoFilterPreset.FADE -> floatArrayOf(14f, 0.9f, 0.85f, 1.05f)
    }
    return applyBitmapAdjustments(
        source = source,
        brightness = brightness + preset[0],
        contrast = contrast * preset[1],
        saturation = saturation * preset[2],
        warmth = warmth * preset[3],
    )
}

private fun createPreviewBitmap(
    source: Bitmap,
    maxSide: Int,
): Bitmap {
    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()
    if (srcW <= 0f || srcH <= 0f) return source
    val ratio = if (srcW >= srcH) maxSide / srcW else maxSide / srcH
    if (ratio >= 1f) return source
    val dstW = (srcW * ratio).toInt().coerceAtLeast(1)
    val dstH = (srcH * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, dstW, dstH, true)
}

private fun centerCrop(
    source: Bitmap,
    preset: CropPreset,
): Bitmap {
    val targetRatio = when (preset) {
        CropPreset.ORIGINAL -> return source
        CropPreset.SQUARE -> 1f
        CropPreset.RATIO_4_5 -> 4f / 5f
        CropPreset.RATIO_16_9 -> 16f / 9f
    }
    val srcW = source.width
    val srcH = source.height
    val srcRatio = srcW.toFloat() / srcH.toFloat()
    val (cropW, cropH) = if (srcRatio > targetRatio) {
        val w = (srcH * targetRatio).toInt().coerceAtLeast(1)
        w to srcH
    } else {
        val h = (srcW / targetRatio).toInt().coerceAtLeast(1)
        srcW to h
    }
    val left = ((srcW - cropW) / 2).coerceAtLeast(0)
    val top = ((srcH - cropH) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(source, left, top, cropW, cropH)
}

private fun drawOverlays(
    base: Bitmap,
    texts: List<String>,
    stickers: List<String>,
): Bitmap {
    if (texts.isEmpty() && stickers.isEmpty()) return base
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(result)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = (result.width * 0.055f).coerceIn(26f, 72f)
        setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
    }
    val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = (result.width * 0.09f).coerceIn(34f, 110f)
        setShadowLayer(10f, 0f, 0f, android.graphics.Color.BLACK)
    }

    texts.take(4).forEachIndexed { index, text ->
        val y = result.height * (0.18f + index * 0.15f)
        canvas.drawText(text, result.width * 0.08f, y, textPaint)
    }
    stickers.take(6).forEachIndexed { index, sticker ->
        val row = index / 3
        val col = index % 3
        val x = result.width * (0.64f + col * 0.11f)
        val y = result.height * (0.22f + row * 0.16f)
        canvas.drawText(sticker, x, y, stickerPaint)
    }
    return result
}

private fun formatHeaderDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        SimpleDateFormat("dd MMM yyyy 'alle' HH:mm", Locale.ITALY).format(Date(epochMillis))
    }.getOrDefault("")
}

private fun extractVideoFrameBitmap(file: File): Bitmap? {
    if (!file.exists()) return null
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        frame
    }.getOrNull()
}

@Composable
private fun FullscreenSystemBarsEffect(
    restoreOnDispose: Boolean = true,
) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val activity = context.findActivity()
        if (activity == null) return@DisposableEffect onDispose { }
        val controller = WindowInsetsControllerCompat(activity.window, view)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (restoreOnDispose) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            controller.systemBarsBehavior = previousBehavior
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openMedia(
    context: android.content.Context,
    mimeType: String,
    file: File,
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nessuna app disponibile per aprire il file", Toast.LENGTH_LONG).show()
    }
}

private fun shareMedia(
    context: android.content.Context,
    mimeType: String,
    file: File,
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mimeType.ifBlank { "*/*" })
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun createCaptureUri(context: android.content.Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "kb_photo_capture").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "capture_$stamp.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }.getOrNull()
}
