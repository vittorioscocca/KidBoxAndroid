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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.data.local.PhotoPreviewCache
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.util.imageAndVideoRequest
import it.vittorioscocca.kidbox.ui.util.rememberMultiMediaPicker
import it.vittorioscocca.kidbox.util.fixBitmapOrientationFromFile
import it.vittorioscocca.kidbox.util.fixVideoFrameOrientation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsFeature
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.ui.components.FamilyKeyMissingGate
import it.vittorioscocca.kidbox.ui.components.FamilyKeyMissingDialog
import it.vittorioscocca.kidbox.R

private enum class PhotosTab { LIBRARY, ALBUMS }
private enum class PhotoGrouping(@androidx.annotation.StringRes val labelRes: Int) {
    YEAR(R.string.photos_years), MONTH(R.string.photos_months), DAY(R.string.photos_days), ALL(R.string.photos_all)
}

@Composable
fun FamilyPhotosScreen(
    onBack: () -> Unit,
    onOpenAlbumDetail: (albumId: String, albumTitle: String) -> Unit = { _, _ -> },
    viewModel: FamilyPhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Copre in un colpo solo le decifrature che qui falliscono in silenzio.
    FamilyKeyMissingGate(state.familyId)
    var currentTab by remember { mutableStateOf(PhotosTab.LIBRARY) }
    var showCreateAlbum by remember { mutableStateOf(false) }
    var viewerPhotoId by remember { mutableStateOf<String?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAlbumSelectionMode by remember { mutableStateOf(false) }
    var selectedAlbumIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAlbumActionPicker by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var longPressMenuPhoto by remember { mutableStateOf<KBFamilyPhotoEntity?>(null) }
    var longPressMenuAlbum by remember { mutableStateOf<KBPhotoAlbumEntity?>(null) }
    var grouping by remember { mutableStateOf(PhotoGrouping.ALL) }
    var thumbTarget by remember { mutableFloatStateOf(124f) }
    var pendingScrollKey by remember { mutableStateOf<String?>(null) }

    // Mantieni la cache anteprime hi-res entro il budget all'apertura (come iOS).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { PhotoPreviewCache.trim(context) }
    }

    val multiMediaPicker = rememberMultiMediaPicker(maxItems = 30) { uris: List<Uri> ->
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

    // Il messaggio della chiave mancante è lungo e va letto: in un Toast
    // verrebbe troncato a due righe. Gli altri errori sono brevi e il Toast va
    // ancora bene.
    var keyMissingFromError by remember { mutableStateOf(false) }
    val keyMissingText = stringResource(R.string.family_key_missing_short)
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            if (msg == keyMissingText) {
                keyMissingFromError = true
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            viewModel.clearError()
        }
    }
    if (keyMissingFromError) {
        FamilyKeyMissingDialog(onDismiss = { keyMissingFromError = false })
    }
    LaunchedEffect(state.filteredPhotos, viewerPhotoId) {
        val selectedId = viewerPhotoId ?: return@LaunchedEffect
        if (state.filteredPhotos.none { it.id == selectedId }) {
            viewerPhotoId = null
        }
    }

    // Aprire una foto a schermo intero è il recupero: la griglia è solo sfoglio.
    // `viewerPhotoId` non nullo = viewer aperto.
    LaunchedEffect(viewerPhotoId) {
        val id = viewerPhotoId ?: return@LaunchedEffect
        val photo = state.filteredPhotos.firstOrNull { it.id == id } ?: return@LaunchedEffect
        KBAnalytics.logRetrieval(
            feature = KBAnalyticsFeature.PHOTO_VIDEO,
            uploaderUid = photo.createdBy,
            createdAtEpochMillis = photo.createdAtEpochMillis,
            entryPoint = KBAnalyticsOrigin.consume(),
        )
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
    LaunchedEffect(state.albums, currentTab) {
        if (currentTab != PhotosTab.ALBUMS) {
            isAlbumSelectionMode = false
            selectedAlbumIds = emptySet()
            return@LaunchedEffect
        }
        val visibleIds = state.albums.map { it.id }.toSet()
        selectedAlbumIds = selectedAlbumIds.intersect(visibleIds)
        if (selectedAlbumIds.isEmpty()) isAlbumSelectionMode = false
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
                isAlbumSelectionMode = isAlbumSelectionMode,
                hasAlbums = state.albums.isNotEmpty(),
                onBack = onBack,
                onToggleSelection = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedPhotoIds = emptySet()
                    } else {
                        isSelectionMode = true
                    }
                },
                onToggleAlbumSelection = {
                    if (isAlbumSelectionMode) {
                        isAlbumSelectionMode = false
                        selectedAlbumIds = emptySet()
                    } else {
                        isAlbumSelectionMode = true
                    }
                },
                onCamera = {
                    val uri = photosCreateCaptureUri(context) ?: run {
                        Toast.makeText(context, context.getString(R.string.photos_camera_error), Toast.LENGTH_LONG).show()
                        return@TopHeader
                    }
                    pendingCaptureUri = uri
                    takePictureLauncher.launch(uri)
                },
                onPlus = {
                    if (currentTab == PhotosTab.ALBUMS) {
                        showCreateAlbum = true
                    } else {
                        multiMediaPicker.launch(imageAndVideoRequest())
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.photos_title),
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
                    Text(stringResource(R.string.photos_show_library))
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
                        Text(if (selectedPhotoIds.size == state.filteredPhotos.size) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                    }
                }
            }

            if (currentTab == PhotosTab.ALBUMS && isAlbumSelectionMode) {
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
                            text = "${selectedAlbumIds.size} selezionati",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val visibleIds = state.albums.map { it.id }.toSet()
                            selectedAlbumIds = if (selectedAlbumIds.size == visibleIds.size) emptySet() else visibleIds
                            if (selectedAlbumIds.isEmpty()) isAlbumSelectionMode = false
                        },
                    ) {
                        Text(if (selectedAlbumIds.size == state.albums.size) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            when (currentTab) {
                PhotosTab.LIBRARY -> {
                    LibraryContent(
                        isLoading = state.isLoading,
                        photos = state.filteredPhotos,
                        grouping = grouping,
                        onGroupingChange = { grouping = it },
                        thumbTarget = thumbTarget,
                        onThumbTargetChange = { thumbTarget = it },
                        pendingScrollKey = pendingScrollKey,
                        onScrollConsumed = { pendingScrollKey = null },
                        onRequestYearScroll = { monthKey ->
                            pendingScrollKey = monthKey
                            grouping = PhotoGrouping.MONTH
                        },
                        isSelectionMode = isSelectionMode,
                        selectedPhotoIds = selectedPhotoIds,
                        uploadingPhotoIds = state.uploadingPhotoIds,
                        familyId = state.familyId,
                        loadPreview = viewModel::previewBitmap,
                        onEmptyPick = {
                            multiMediaPicker.launch(imageAndVideoRequest())
                        },
                        onEmptyCamera = {
                            val uri = photosCreateCaptureUri(context) ?: run {
                                Toast.makeText(context, context.getString(R.string.photos_camera_error), Toast.LENGTH_LONG).show()
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
                        onSetSelected = { id, sel ->
                            selectedPhotoIds = if (sel) selectedPhotoIds + id else selectedPhotoIds - id
                            if (selectedPhotoIds.isEmpty()) isSelectionMode = false
                        },
                    )
                }

                PhotosTab.ALBUMS -> {
                    AlbumsContent(
                        isLoading = state.isLoading,
                        albums = state.albums,
                        allPhotos = state.photos,
                        isSelectionMode = isAlbumSelectionMode,
                        selectedAlbumIds = selectedAlbumIds,
                        onCreateAlbum = { showCreateAlbum = true },
                        onAlbumTap = { album ->
                            if (isAlbumSelectionMode) {
                                selectedAlbumIds = if (selectedAlbumIds.contains(album.id)) {
                                    selectedAlbumIds - album.id
                                } else {
                                    selectedAlbumIds + album.id
                                }
                                if (selectedAlbumIds.isEmpty()) isAlbumSelectionMode = false
                            } else {
                                onOpenAlbumDetail(album.id, album.title)
                            }
                        },
                        onAlbumLongPress = { album ->
                            longPressMenuAlbum = album
                        },
                    )
                }
            }
        }

        if (isAlbumSelectionMode && currentTab == PhotosTab.ALBUMS) {
            AlbumSelectionActionBar(
                selectedCount = selectedAlbumIds.size,
                onDelete = {
                    viewModel.deleteAlbums(selectedAlbumIds)
                    selectedAlbumIds = emptySet()
                    isAlbumSelectionMode = false
                },
                onDeselect = {
                    selectedAlbumIds = emptySet()
                    isAlbumSelectionMode = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
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
                onDeselect = {
                    selectedPhotoIds = emptySet()
                    isSelectionMode = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
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

    longPressMenuAlbum?.let { album ->
        AlbumLongPressMenuDialog(
            onDismiss = { longPressMenuAlbum = null },
            onOpen = {
                longPressMenuAlbum = null
                onOpenAlbumDetail(album.id, album.title)
            },
            onSelect = {
                longPressMenuAlbum = null
                isAlbumSelectionMode = true
                selectedAlbumIds = setOf(album.id)
            },
            onDelete = {
                longPressMenuAlbum = null
                viewModel.deleteAlbum(album.id)
            },
        )
    }

    val startIndex = viewerPhotoId?.let { id -> state.filteredPhotos.indexOfFirst { it.id == id } } ?: -1
    if (startIndex >= 0) {
        PhotosFullscreenMediaViewer(
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
                        Toast.makeText(context, context.getString(R.string.photos_open_media_error), Toast.LENGTH_LONG).show()
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
    isAlbumSelectionMode: Boolean,
    hasAlbums: Boolean,
    onBack: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleAlbumSelection: () -> Unit,
    onCamera: () -> Unit,
    onPlus: () -> Unit,
) {
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
                HeaderCircleButton(icon = Icons.Default.CameraAlt, onClick = onCamera)
                HeaderCircleButton(icon = Icons.Default.Add, onClick = onPlus)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasAlbums) {
                    HeaderCircleButton(
                        icon = if (isAlbumSelectionMode) Icons.Default.Done else Icons.Default.DoneAll,
                        onClick = onToggleAlbumSelection,
                    )
                }
                HeaderCircleButton(icon = Icons.Default.Add, onClick = onPlus)
            }
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
                label = stringResource(R.string.photos_library),
                selected = selectedTab == PhotosTab.LIBRARY,
                onClick = { onSelect(PhotosTab.LIBRARY) },
                modifier = Modifier.weight(1f),
            )
            TabPill(
                label = stringResource(R.string.photos_albums),
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
    grouping: PhotoGrouping,
    onGroupingChange: (PhotoGrouping) -> Unit,
    thumbTarget: Float,
    onThumbTargetChange: (Float) -> Unit,
    pendingScrollKey: String?,
    onScrollConsumed: () -> Unit,
    onRequestYearScroll: (String) -> Unit,
    isSelectionMode: Boolean,
    selectedPhotoIds: Set<String>,
    uploadingPhotoIds: Set<String>,
    familyId: String,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    onEmptyPick: () -> Unit,
    onEmptyCamera: () -> Unit,
    onPhotoTap: (KBFamilyPhotoEntity) -> Unit,
    onPhotoLongPress: (KBFamilyPhotoEntity) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Larghezza effettiva: piena su telefono, limitata e centrata su schermi larghi
        // (evita tessere "giganti", come iOS).
        val layoutWidth = minOf(maxWidth, 720.dp)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.width(layoutWidth).fillMaxHeight()) {
                if (isSelectionMode) {
                    DensePhotoGrid(
                        photos = remember(photos) { photos.sortedByDescending { it.takenAtEpochMillis } },
                        layoutWidth = layoutWidth,
                        thumbTarget = thumbTarget,
                        onThumbTargetChange = onThumbTargetChange,
                        enablePinch = false,
                        isSelectionMode = true,
                        selectedPhotoIds = selectedPhotoIds,
                        uploadingPhotoIds = uploadingPhotoIds,
                        familyId = familyId,
                        loadPreview = loadPreview,
                        onPhotoTap = onPhotoTap,
                        onPhotoLongPress = onPhotoLongPress,
                        onSetSelected = onSetSelected,
                    )
                } else {
                    when (grouping) {
                        PhotoGrouping.ALL -> DensePhotoGrid(
                            photos = remember(photos) { photos.sortedByDescending { it.takenAtEpochMillis } },
                            layoutWidth = layoutWidth,
                            thumbTarget = thumbTarget,
                            onThumbTargetChange = onThumbTargetChange,
                            enablePinch = true,
                            isSelectionMode = false,
                            selectedPhotoIds = selectedPhotoIds,
                            uploadingPhotoIds = uploadingPhotoIds,
                            familyId = familyId,
                            loadPreview = loadPreview,
                            onPhotoTap = onPhotoTap,
                            onPhotoLongPress = onPhotoLongPress,
                            onSetSelected = onSetSelected,
                        )
                        PhotoGrouping.YEAR -> YearsLayout(
                            groups = remember(photos) { groupPhotos(context, photos, PhotoGrouping.YEAR) },
                            layoutWidth = layoutWidth,
                            familyId = familyId,
                            loadPreview = loadPreview,
                            onYearTap = { group ->
                                val first = group.photos.firstOrNull() ?: return@YearsLayout
                                onRequestYearScroll(monthKeyOf(first))
                            },
                            onPhotoLongPress = onPhotoLongPress,
                        )
                        PhotoGrouping.MONTH -> MosaicSections(
                            groups = remember(photos) { groupPhotos(context, photos, PhotoGrouping.MONTH) },
                            layoutWidth = layoutWidth,
                            pendingScrollKey = pendingScrollKey,
                            onScrollConsumed = onScrollConsumed,
                            familyId = familyId,
                            loadPreview = loadPreview,
                            uploadingPhotoIds = uploadingPhotoIds,
                            onPhotoTap = onPhotoTap,
                            onPhotoLongPress = onPhotoLongPress,
                        )
                        PhotoGrouping.DAY -> MosaicSections(
                            groups = remember(photos) { groupPhotos(context, photos, PhotoGrouping.DAY) },
                            layoutWidth = layoutWidth,
                            pendingScrollKey = pendingScrollKey,
                            onScrollConsumed = onScrollConsumed,
                            familyId = familyId,
                            loadPreview = loadPreview,
                            uploadingPhotoIds = uploadingPhotoIds,
                            onPhotoTap = onPhotoTap,
                            onPhotoLongPress = onPhotoLongPress,
                        )
                    }
                }
            }
        }
        if (!isSelectionMode) {
            GroupingBar(
                grouping = grouping,
                onSelect = onGroupingChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            )
        }
    }
}

// MARK: - Floating grouping bar (Anni · Mesi · Giorni · Tutto)

@Composable
private fun GroupingBar(
    grouping: PhotoGrouping,
    onSelect: (PhotoGrouping) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.kidBoxColors.card.copy(alpha = 0.96f),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(PhotoGrouping.YEAR, PhotoGrouping.MONTH, PhotoGrouping.DAY, PhotoGrouping.ALL).forEach { g ->
                val selected = g == grouping
                Surface(
                    onClick = { onSelect(g) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.kidBoxColors.title.copy(alpha = 0.12f) else Color.Transparent,
                ) {
                    Text(
                        text = stringResource(g.labelRes),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        color = if (selected) MaterialTheme.kidBoxColors.title else MaterialTheme.kidBoxColors.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// MARK: - Years layout (una grande copertina per anno)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YearsLayout(
    groups: List<PhotoGroup>,
    layoutWidth: Dp,
    familyId: String,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    onYearTap: (PhotoGroup) -> Unit,
    onPhotoLongPress: (KBFamilyPhotoEntity) -> Unit,
) {
    val cardW = layoutWidth - 24.dp
    val cardH = cardW * 0.62f
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 86.dp),
    ) {
        items(groups, key = { it.key }) { group ->
            val cover = group.photos.firstOrNull()
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(cardW)
                        .height(cardH)
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { onYearTap(group) },
                            onLongClick = { cover?.let(onPhotoLongPress) },
                        ),
                ) {
                    if (cover != null) {
                        PhotoThumbnailCell(
                            photo = cover,
                            displaySize = cardW,
                            loadPreview = loadPreview,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.kidBoxColors.rowBackground))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                ),
                            ),
                    )
                    Text(
                        text = group.title,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// MARK: - Mosaic sections (Mesi / Giorni)

private sealed interface MosaicItem {
    data class Header(val key: String, val title: String) : MosaicItem
    data class BlockRow(val key: String, val block: MosaicBlock) : MosaicItem
}

private fun buildMosaicItems(groups: List<PhotoGroup>): List<MosaicItem> {
    val rows = mutableListOf<MosaicItem>()
    groups.forEach { group ->
        rows.add(MosaicItem.Header(group.key, group.title))
        mosaicBlocks(group.photos).forEachIndexed { idx, block ->
            rows.add(MosaicItem.BlockRow("${group.key}_$idx", block))
        }
    }
    return rows
}

@Composable
private fun MosaicSections(
    groups: List<PhotoGroup>,
    layoutWidth: Dp,
    pendingScrollKey: String?,
    onScrollConsumed: () -> Unit,
    familyId: String,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    uploadingPhotoIds: Set<String>,
    onPhotoTap: (KBFamilyPhotoEntity) -> Unit,
    onPhotoLongPress: (KBFamilyPhotoEntity) -> Unit,
) {
    val rows = remember(groups) { buildMosaicItems(groups) }
    val listState = rememberLazyListState()
    LaunchedEffect(pendingScrollKey, rows) {
        val key = pendingScrollKey ?: return@LaunchedEffect
        val idx = rows.indexOfFirst { it is MosaicItem.Header && it.key == key }
        if (idx >= 0) listState.animateScrollToItem(idx)
        onScrollConsumed()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 86.dp),
    ) {
        items(
            items = rows,
            key = { row ->
                when (row) {
                    is MosaicItem.Header -> "h_${row.key}"
                    is MosaicItem.BlockRow -> "b_${row.key}"
                }
            },
        ) { row ->
            when (row) {
                is MosaicItem.Header -> Text(
                    text = row.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.kidBoxColors.background.copy(alpha = 0.95f))
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    color = MaterialTheme.kidBoxColors.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                is MosaicItem.BlockRow -> MosaicBlockRow(
                    block = row.block,
                    layoutWidth = layoutWidth,
                    familyId = familyId,
                    loadPreview = loadPreview,
                    uploadingPhotoIds = uploadingPhotoIds,
                    onPhotoTap = onPhotoTap,
                    onPhotoLongPress = onPhotoLongPress,
                )
            }
        }
    }
}

@Composable
private fun MosaicBlockRow(
    block: MosaicBlock,
    layoutWidth: Dp,
    familyId: String,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    uploadingPhotoIds: Set<String>,
    onPhotoTap: (KBFamilyPhotoEntity) -> Unit,
    onPhotoLongPress: (KBFamilyPhotoEntity) -> Unit,
) {
    val s = 2.dp
    val w = layoutWidth - s * 2
    val tile: @Composable (KBFamilyPhotoEntity, Dp, Dp) -> Unit = { photo, tw, th ->
        MediaTile(
            photo = photo,
            width = tw,
            height = th,
            familyId = familyId,
            loadPreview = loadPreview,
            isUploading = uploadingPhotoIds.contains(photo.id) ||
                KBSyncState.fromRaw(photo.syncStateRaw) == KBSyncState.PENDING_UPSERT,
            onTap = { onPhotoTap(photo) },
            onLongPress = { onPhotoLongPress(photo) },
        )
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = s, vertical = s / 2)) {
        when (block.kind) {
            MosaicKind.FEATURE_LEFT, MosaicKind.FEATURE_RIGHT -> {
                val leftW = (w - s) * 0.64f
                val rightW = w - s - leftW
                val h = leftW
                val smallH = (h - s) / 2f
                Row(horizontalArrangement = Arrangement.spacedBy(s)) {
                    val stack: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(s)) {
                            block.photos.drop(1).take(2).forEach { p -> tile(p, rightW, smallH) }
                        }
                    }
                    if (block.kind == MosaicKind.FEATURE_RIGHT) {
                        stack()
                        block.photos.firstOrNull()?.let { tile(it, leftW, h) }
                    } else {
                        block.photos.firstOrNull()?.let { tile(it, leftW, h) }
                        stack()
                    }
                }
            }
            MosaicKind.PAIR -> {
                val cw = (w - s) / 2f
                val h = cw * 0.78f
                Row(horizontalArrangement = Arrangement.spacedBy(s)) {
                    block.photos.forEach { p -> tile(p, cw, h) }
                }
            }
            MosaicKind.TRIPLE -> {
                val cw = (w - s * 2) / 3f
                Row(horizontalArrangement = Arrangement.spacedBy(s)) {
                    block.photos.forEach { p -> tile(p, cw, cw) }
                }
            }
            MosaicKind.SINGLE -> {
                block.photos.firstOrNull()?.let { tile(it, w, w * 0.66f) }
            }
        }
    }
}

// MARK: - Dense grid (Tutto / selezione)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DensePhotoGrid(
    photos: List<KBFamilyPhotoEntity>,
    layoutWidth: Dp,
    thumbTarget: Float,
    onThumbTargetChange: (Float) -> Unit,
    enablePinch: Boolean,
    isSelectionMode: Boolean,
    selectedPhotoIds: Set<String>,
    uploadingPhotoIds: Set<String>,
    familyId: String,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    onPhotoTap: (KBFamilyPhotoEntity) -> Unit,
    onPhotoLongPress: (KBFamilyPhotoEntity) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
) {
    val spacing = 2.dp
    val columns = maxOf(3, (layoutWidth.value / thumbTarget).roundToInt())
    val cellSize = ((layoutWidth.value - 2f * (columns - 1)) / columns).dp
    val gridState = rememberLazyGridState()
    val finalModifier = if (isSelectionMode) {
        Modifier
            .fillMaxSize()
            .dragSelect(gridState, photos, onSetSelected) { id -> selectedPhotoIds.contains(id) }
    } else if (enablePinch) {
        Modifier.fillMaxSize().pinchZoom { zoom ->
            onThumbTargetChange((thumbTarget * zoom).coerceIn(84f, 220f))
        }
    } else {
        Modifier.fillMaxSize()
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = finalModifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(bottom = 86.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            val selected = selectedPhotoIds.contains(photo.id)
            MediaTile(
                photo = photo,
                width = cellSize,
                height = cellSize,
                familyId = familyId,
                loadPreview = loadPreview,
                isSelectionMode = isSelectionMode,
                isSelected = selected,
                isUploading = uploadingPhotoIds.contains(photo.id) ||
                    KBSyncState.fromRaw(photo.syncStateRaw) == KBSyncState.PENDING_UPSERT,
                onTap = {
                    if (isSelectionMode) onSetSelected(photo.id, !selected) else onPhotoTap(photo)
                },
                onLongPress = if (isSelectionMode) null else { -> onPhotoLongPress(photo) },
            )
        }
    }
}

// MARK: - Drag-select & pinch gestures

private fun Modifier.dragSelect(
    gridState: LazyGridState,
    photos: List<KBFamilyPhotoEntity>,
    onSetSelected: (String, Boolean) -> Unit,
    isSelected: (String) -> Boolean,
): Modifier = composed {
    val currentPhotos by rememberUpdatedState(photos)
    val currentIsSelected by rememberUpdatedState(isSelected)
    val currentOnSet by rememberUpdatedState(onSetSelected)
    pointerInput(Unit) {
        var paint = true
        var lastIndex = -1
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val idx = gridState.itemIndexAt(offset)
                if (idx != null && idx in currentPhotos.indices) {
                    val id = currentPhotos[idx].id
                    paint = !currentIsSelected(id)
                    currentOnSet(id, paint)
                    lastIndex = idx
                }
            },
            onDrag = { change, _ ->
                val idx = gridState.itemIndexAt(change.position)
                if (idx != null && idx in currentPhotos.indices && idx != lastIndex) {
                    currentOnSet(currentPhotos[idx].id, paint)
                    lastIndex = idx
                }
            },
            onDragEnd = { lastIndex = -1 },
            onDragCancel = { lastIndex = -1 },
        )
    }
}

private fun LazyGridState.itemIndexAt(pos: Offset): Int? {
    val item = layoutInfo.visibleItemsInfo.firstOrNull {
        pos.x >= it.offset.x && pos.x <= it.offset.x + it.size.width &&
            pos.y >= it.offset.y && pos.y <= it.offset.y + it.size.height
    }
    return item?.index
}

/**
 * Foto a schermo intero con pinch-to-zoom, trascinamento e doppio tap.
 *
 * **Il punto delicato è la convivenza con il pager.** La foto vive dentro un
 * [HorizontalPager]: se la gesture consumasse sempre gli eventi, lo swipe
 * orizzontale per cambiare foto smetterebbe di funzionare; se non li consumasse
 * mai, non si potrebbe spostare la foto ingrandita perché il pager cambierebbe
 * pagina. Qui si consuma **solo** quando la gesture è davvero nostra: pinch a
 * due dita, oppure trascinamento a foto già ingrandita. Con una sola dita e a
 * zoom 1 non si consuma nulla e lo swipe arriva al pager.
 *
 * Stesso criterio di [pinchZoom], che nella griglia consuma solo a due dita.
 */
@Composable
private fun ZoomableAsyncImage(
    model: Any?,
    photoId: String,
    modifier: Modifier = Modifier,
) {
    // `remember(photoId)`: cambiando foto lo zoom riparte da 1, altrimenti la
    // successiva si aprirebbe con l'ingrandimento della precedente.
    var scale by remember(photoId) { mutableFloatStateOf(1f) }
    var offset by remember(photoId) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    /** Tiene la foto dentro i bordi: ingrandita può spostarsi solo di quanto eccede. */
    fun clamped(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f) return Offset.Zero
        val maxX = boxSize.width * (currentScale - 1f) / 2f
        val maxY = boxSize.height * (currentScale - 1f) / 2f
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(photoId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2 || scale > 1f) {
                            val newScale = (scale * event.calculateZoom())
                                .coerceIn(1f, MAX_PHOTO_ZOOM)
                            scale = newScale
                            offset = if (newScale > 1f) {
                                clamped(offset + event.calculatePan(), newScale)
                            } else {
                                Offset.Zero
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(photoId) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_PHOTO_ZOOM
                            offset = Offset.Zero
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

private const val MAX_PHOTO_ZOOM = 4f
private const val DOUBLE_TAP_PHOTO_ZOOM = 2.5f

private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

// MARK: - Media tile + thumbnail cell

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    photo: KBFamilyPhotoEntity,
    width: Dp,
    height: Dp,
    familyId: String,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isUploading: Boolean = false,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    displaySize: Dp = maxOf(width, height),
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        PhotoThumbnailCell(
            photo = photo,
            displaySize = displaySize,
            loadPreview = loadPreview,
            modifier = Modifier.fillMaxSize(),
        )

        if (photo.mimeType.startsWith("video/")) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = photosFormatDuration(photo.videoDurationSeconds),
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
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
        }

        if (isSelectionMode) {
            if (!isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            }
            Surface(
                color = if (isSelected) Color(0xFF1E88E5) else Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(22.dp),
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tessera immagine: mostra subito il thumbnail base64 (pochi KB) e, per le tessere grandi
 * (Anni, feature del mosaico), carica on-demand un'anteprima più nitida (cache su disco).
 */
@Composable
private fun PhotoThumbnailCell(
    photo: KBFamilyPhotoEntity,
    displaySize: Dp,
    loadPreview: suspend (KBFamilyPhotoEntity, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val bucket = remember(photo.id, displaySize, photo.storagePath, photo.localPath, photo.mimeType) {
        previewBucketFor(photo, displaySize.value, density)
    }
    var hiRes by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.id, bucket) {
        hiRes = null
        if (bucket > 0) {
            hiRes = withContext(Dispatchers.IO) { loadPreview(photo, bucket) }
        }
    }
    val thumbBytes = remember(photo.thumbnailBase64) {
        photo.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
    }
    Box(modifier = modifier.background(MaterialTheme.kidBoxColors.rowBackground)) {
        val hi = hiRes
        when {
            hi != null -> Image(
                bitmap = hi.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            thumbBytes != null -> AsyncImage(
                model = thumbBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle)
            }
        }
    }
}

private fun previewBucketFor(photo: KBFamilyPhotoEntity, displaySizeDp: Float, density: Float): Int {
    if (photo.mimeType.startsWith("video/")) return 0
    if (displaySizeDp < 150f) return 0
    if (photo.storagePath.isBlank() && photo.localPath == null) return 0
    val px = displaySizeDp * density
    return when {
        px <= 280f -> 0
        px <= 520f -> 512
        px <= 820f -> 800
        else -> 1200
    }
}

// MARK: - Grouping helpers

private data class PhotoGroup(
    val key: String,
    val title: String,
    val photos: List<KBFamilyPhotoEntity>,
)

private fun monthKeyOf(photo: KBFamilyPhotoEntity): String =
    SimpleDateFormat("yyyy-MM", KBLocale.current()).format(Date(photo.takenAtEpochMillis))

private fun groupPhotos(context: android.content.Context, photos: List<KBFamilyPhotoEntity>, grouping: PhotoGrouping): List<PhotoGroup> {
    val sorted = photos.sortedByDescending { it.takenAtEpochMillis }
    if (grouping == PhotoGrouping.ALL) {
        return listOf(PhotoGroup("all", context.getString(R.string.photos_all), sorted))
    }
    val locale = KBLocale.current()
    val keyFormat = when (grouping) {
        PhotoGrouping.DAY -> SimpleDateFormat("yyyy-MM-dd", locale)
        PhotoGrouping.MONTH -> SimpleDateFormat("yyyy-MM", locale)
        else -> SimpleDateFormat("yyyy", locale)
    }
    val titleFormat = when (grouping) {
        PhotoGrouping.DAY -> SimpleDateFormat("EEEE d MMMM yyyy", locale)
        PhotoGrouping.MONTH -> SimpleDateFormat("MMMM yyyy", locale)
        else -> SimpleDateFormat("yyyy", locale)
    }
    val grouped = LinkedHashMap<String, MutableList<KBFamilyPhotoEntity>>()
    sorted.forEach { photo ->
        val key = keyFormat.format(Date(photo.takenAtEpochMillis))
        grouped.getOrPut(key) { mutableListOf() }.add(photo)
    }
    return grouped.map { (key, list) ->
        val date = Date(list.first().takenAtEpochMillis)
        PhotoGroup(
            key = key,
            title = titleFormat.format(date).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
            },
            photos = list,
        )
    }
}

// MARK: - Mosaic blocks

private enum class MosaicKind { FEATURE_LEFT, FEATURE_RIGHT, PAIR, TRIPLE, SINGLE }

private data class MosaicBlock(val kind: MosaicKind, val photos: List<KBFamilyPhotoEntity>)

/** Suddivide una lista di foto in blocchi mosaico ciclando alcune forme (porting iOS). */
private fun mosaicBlocks(items: List<KBFamilyPhotoEntity>): List<MosaicBlock> {
    val pattern = listOf(
        MosaicKind.FEATURE_LEFT to 3,
        MosaicKind.PAIR to 2,
        MosaicKind.TRIPLE to 3,
        MosaicKind.FEATURE_RIGHT to 3,
        MosaicKind.PAIR to 2,
    )
    val blocks = mutableListOf<MosaicBlock>()
    var i = 0
    var p = 0
    while (i < items.size) {
        val remaining = items.size - i
        val (kind, count) = pattern[p % pattern.size]
        if (remaining >= count) {
            blocks.add(MosaicBlock(kind, items.subList(i, i + count).toList()))
            i += count
        } else {
            // Coda più corta della forma successiva: render pulito senza buchi.
            val tailKind = if (remaining == 1) MosaicKind.SINGLE else MosaicKind.PAIR
            blocks.add(MosaicBlock(tailKind, items.subList(i, items.size).toList()))
            i = items.size
        }
        p++
    }
    return blocks
}

@Composable
private fun AlbumsContent(
    isLoading: Boolean,
    albums: List<KBPhotoAlbumEntity>,
    allPhotos: List<KBFamilyPhotoEntity>,
    isSelectionMode: Boolean,
    selectedAlbumIds: Set<String>,
    onCreateAlbum: () -> Unit,
    onAlbumTap: (KBPhotoAlbumEntity) -> Unit,
    onAlbumLongPress: (KBPhotoAlbumEntity) -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6B00))
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isSelectionMode) {
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
                    text = stringResource(R.string.photos_new_album),
                    color = MaterialTheme.kidBoxColors.title,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isSelectionMode) 8.dp else 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                val cover = allPhotos.firstOrNull { it.id == album.coverPhotoId }
                AlbumCard(
                    albumTitle = album.title,
                    coverPhoto = cover,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedAlbumIds.contains(album.id),
                    onClick = { onAlbumTap(album) },
                    onLongClick = { onAlbumLongPress(album) },
                )
            }
        }
    }
}

@Composable
private fun AlbumSelectionActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.kidBoxColors.card,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotosActionIconButton(
                icon = Icons.Default.Close,
                label = stringResource(R.string.life_cancel),
                enabled = true,
                onClick = onDeselect,
            )
            PhotosActionIconButton(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.life_delete),
                enabled = selectedCount > 0,
                destructive = true,
                onClick = onDelete,
            )
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
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.kidBoxColors.card,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotosActionIconButton(
                icon = Icons.Default.Close,
                label = stringResource(R.string.life_cancel),
                enabled = true,
                onClick = onDeselect,
            )
            PhotosActionIconButton(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = stringResource(R.string.vehicles_add),
                enabled = selectedCount > 0,
                onClick = onAdd,
            )
            PhotosActionIconButton(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                label = stringResource(R.string.photos_move),
                enabled = selectedCount > 0,
                onClick = onMove,
            )
            PhotosActionIconButton(
                icon = Icons.Default.RemoveCircleOutline,
                label = stringResource(R.string.photos_remove),
                enabled = canRemoveFromAlbum,
                onClick = onRemove,
            )
            PhotosActionIconButton(
                icon = Icons.Default.Image,
                label = stringResource(R.string.photos_cover),
                enabled = canSetCover,
                onClick = onSetCover,
            )
            PhotosActionIconButton(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.life_delete),
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
            text = stringResource(R.string.photos_none),
            color = MaterialTheme.kidBoxColors.title,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.photos_none_hint),
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
                text = stringResource(R.string.photos_add_media),
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
                    text = stringResource(R.string.photos_take_photo),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    albumTitle: String,
    coverPhoto: KBFamilyPhotoEntity?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
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
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) Color(0xFF1E88E5).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.2f),
                        ),
                )
                Surface(
                    color = if (isSelected) Color(0xFF1E88E5) else Color.Black.copy(alpha = 0.5f),
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
                        textAlign = TextAlign.Center,
                    )
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
            text = stringResource(R.string.photos_new_album),
            color = MaterialTheme.kidBoxColors.title,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}


@Composable
private fun AlbumLongPressMenuDialog(
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photos_album_actions)) },
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
                        text = stringResource(R.string.photos_open),
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
                        text = stringResource(R.string.photos_select),
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
                        text = stringResource(R.string.photos_delete_album),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Color(0xFFE35156),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.photos_close)) }
        },
    )
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
        title = { Text(stringResource(R.string.photos_photo_actions)) },
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
                        text = stringResource(R.string.photos_open),
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
                        text = stringResource(R.string.photos_select),
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
                        text = stringResource(R.string.life_delete),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Color(0xFFE35156),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_cancel)) }
        },
    )
}

@Composable
internal fun PhotosActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.kidBoxColors.subtitle
        destructive -> Color(0xFFE35156)
        else -> MaterialTheme.kidBoxColors.title
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
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
        title = { Text(stringResource(R.string.photos_select_album)) },
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
                    Text(stringResource(R.string.photos_no_albums), color = MaterialTheme.kidBoxColors.subtitle)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_cancel)) }
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
        title = { Text(stringResource(R.string.photos_new_album)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.photos_album_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim()) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.photos_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_cancel)) }
        },
    )
}

internal fun photosFormatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null || durationSeconds <= 0.0) return "00:00"
    val total = durationSeconds.toInt()
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotosFullscreenMediaViewer(
    photos: List<KBFamilyPhotoEntity>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (KBFamilyPhotoEntity) -> Unit,
    onOpenExternal: (KBFamilyPhotoEntity, CoroutineScope) -> Unit,
    onSaveEditedCopy: (KBFamilyPhotoEntity, ByteArray) -> Unit,
    prepareFile: suspend (KBFamilyPhotoEntity) -> File,
) {
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
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                // Edge-to-edge fullscreen: la dialog deve coprire l'intero
                // schermo inclusa la gesture/navigation bar area.
                // 1. WindowCompat.setDecorFitsSystemWindows è l'API ufficiale
                //    per estendere la window dietro le system bars.
                // 2. setLayout(MATCH_PARENT) rimuove il cap di dimensione.
                // 3. FLAG_LAYOUT_NO_LIMITS permette l'estensione oltre i bordi.
                // 4. Background trasparente rimuove il padding nine-patch del Dialog.
                WindowCompat.setDecorFitsSystemWindows(this, false)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
        FullscreenSystemBarsEffect()
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
                                Toast.makeText(context, context.getString(R.string.photos_load_error), Toast.LENGTH_SHORT).show()
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
                                    text = stringResource(R.string.photos_open_video),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        ZoomableAsyncImage(
                            model = localFile ?: photo.thumbnailBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() },
                            photoId = photo.id,
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
                        label = stringResource(R.string.photos_share),
                        onClick = {
                            currentPhoto?.let { photo ->
                                scope.launch {
                                    runCatching {
                                        val file = withContext(Dispatchers.IO) { prepareFile(photo) }
                                        shareMedia(context, photo.mimeType, file)
                                    }.onFailure {
                                        Toast.makeText(context, context.getString(R.string.photos_share_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                    ActionLabel(
                        label = stringResource(R.string.life_edit),
                        enabled = currentPhoto?.mimeType?.startsWith("image/") == true,
                        onClick = {
                            currentPhoto?.let { photo ->
                                scope.launch {
                                    runCatching {
                                        val file = withContext(Dispatchers.IO) { prepareFile(photo) }
                                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                            ?.let { fixBitmapOrientationFromFile(it, file.absolutePath) }
                                            ?: error("Anteprima non disponibile")
                                        editorBitmap = bitmap
                                        showEditorForPhoto = photo
                                    }.onFailure {
                                        Toast.makeText(context, context.getString(R.string.photos_editor_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                    ActionLabel(
                        label = stringResource(R.string.life_delete),
                        destructive = true,
                        onClick = { currentPhoto?.let { onDelete(it) } },
                    )
                }
                currentPhoto?.takeIf { it.mimeType.startsWith("video/") }?.let { video ->
                    Text(
                        text = photosFormatDuration(video.videoDurationSeconds),
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
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_cancel), color = Color.White) }
                    TextButton(
                        onClick = {
                            val out = java.io.ByteArrayOutputStream()
                            editedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            onSaveCopy(out.toByteArray())
                        },
                    ) { Text(stringResource(R.string.photos_save_copy)) }
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
                            label = stringResource(tool.labelRes),
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
                                    label = stringResource(preset.labelRes),
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
                            EditSlider(stringResource(R.string.photos_contrast), contrast, 0.2f..2.2f) { contrast = it }
                            EditSlider(stringResource(R.string.photos_saturation), saturation, 0f..2f) { saturation = it }
                            EditSlider(stringResource(R.string.photos_warmth), warmth, 0.4f..1.8f) { warmth = it }
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
                                    title = stringResource(preset.labelRes),
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
                                placeholder = { Text(stringResource(R.string.photos_insert_text)) },
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolPill(
                                    label = stringResource(R.string.photos_add_text),
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
                                    label = stringResource(R.string.photos_remove_last),
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
                                label = stringResource(R.string.photos_remove_last_sticker),
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

private enum class EditorTool(@androidx.annotation.StringRes val labelRes: Int) {
    CROP(R.string.photos_crop),
    ADJUST(R.string.photos_adjust),
    FILTERS(R.string.photos_filters),
    TEXT(R.string.photos_text),
    STICKERS(R.string.photos_stickers),
}

private enum class CropPreset(@androidx.annotation.StringRes val labelRes: Int) {
    ORIGINAL(R.string.photos_original),
    SQUARE(R.string.photos_ratio_1_1),
    RATIO_4_5(R.string.photos_ratio_4_5),
    RATIO_16_9(R.string.photos_ratio_16_9),
}

private enum class PhotoFilterPreset(@androidx.annotation.StringRes val labelRes: Int) {
    NONE(R.string.photos_original),
    VIVID(R.string.photos_vivid),
    FADE(R.string.photos_fade),
    MONO(R.string.photos_noir),
    CHROME(R.string.photos_chrome),
    WARM(R.string.photos_warm),
    COOL(R.string.photos_cold),
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
        SimpleDateFormat("dd MMM yyyy · HH:mm", KBLocale.current()).format(Date(epochMillis))
    }.getOrDefault("")
}

private fun extractVideoFrameBitmap(file: File): Bitmap? {
    if (!file.exists()) return null
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?.let { fixVideoFrameOrientation(it, retriever) }
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
        Toast.makeText(context, context.getString(R.string.photos_no_app), Toast.LENGTH_LONG).show()
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
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.photos_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun photosCreateCaptureUri(context: android.content.Context): Uri? {
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
