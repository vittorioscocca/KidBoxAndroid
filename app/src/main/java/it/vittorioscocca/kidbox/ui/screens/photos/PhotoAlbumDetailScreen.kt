package it.vittorioscocca.kidbox.ui.screens.photos

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.util.imageAndVideoRequest
import it.vittorioscocca.kidbox.ui.util.rememberMultiMediaPicker
import it.vittorioscocca.kidbox.ui.permissions.rememberCameraPermissionRequester
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@Composable
fun PhotoAlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: PhotoAlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewerPhotoId by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val multiMediaPicker = rememberMultiMediaPicker(maxItems = 30) { uris ->
        if (uris.isNotEmpty()) viewModel.importMediaBatch(uris)
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (saved && uri != null) {
            viewModel.importMedia(uri)
        }
    }

    val requestPhotoCamera = rememberCameraPermissionRequester(
        onDenied = {
            Toast.makeText(context, context.getString(R.string.documents_camera_permission_required), Toast.LENGTH_SHORT).show()
        },
        onLaunchCamera = {
            val uri = photosCreateCaptureUri(context) ?: run {
                Toast.makeText(context, context.getString(R.string.photos_camera_error), Toast.LENGTH_LONG).show()
                return@rememberCameraPermissionRequester
            }
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        },
    )

    fun openCamera() {
        requestPhotoCamera()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.photos) {
        val selectedId = viewerPhotoId ?: return@LaunchedEffect
        if (state.photos.none { it.id == selectedId }) {
            viewerPhotoId = null
        }
    }
    LaunchedEffect(state.photos, isSelectionMode) {
        val visibleIds = state.photos.map { it.id }.toSet()
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
                .navigationBarsPadding(),
        ) {
            AlbumDetailTopHeader(
                title = if (isSelectionMode) {
                    if (selectedPhotoIds.isEmpty()) stringResource(R.string.photos_select) else "${selectedPhotoIds.size} selezionati"
                } else {
                    state.albumTitle
                },
                isSelectionMode = isSelectionMode,
                allSelected = selectedPhotoIds.size == state.photos.size && state.photos.isNotEmpty(),
                onBack = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedPhotoIds = emptySet()
                    } else {
                        onBack()
                    }
                },
                onCamera = ::openCamera,
                onToggleSelection = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedPhotoIds = emptySet()
                    } else {
                        isSelectionMode = true
                    }
                },
                onSelectAll = {
                    selectedPhotoIds = if (selectedPhotoIds.size == state.photos.size) {
                        emptySet()
                    } else {
                        state.photos.map { it.id }.toSet()
                    }
                    if (selectedPhotoIds.isEmpty()) isSelectionMode = false
                },
            )

            if (state.isTripAlbum) {
                TripDedicatedAlbumBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF6B00))
                    }
                }
                state.photos.isEmpty() -> {
                    AlbumEmptyState(
                        isTripAlbum = state.isTripAlbum,
                        onCamera = ::openCamera,
                        onPickFromLibrary = {
                            multiMediaPicker.launch(imageAndVideoRequest())
                        },
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(state.photos, key = { it.id }) { photo ->
                            AlbumPhotoGridCell(
                                photo = photo,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedPhotoIds.contains(photo.id),
                                isUploading = state.uploadingPhotoIds.contains(photo.id),
                                onClick = {
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
                            )
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            AlbumPhotoSelectionBar(
                selectedCount = selectedPhotoIds.size,
                onRemove = { showRemoveConfirm = true },
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

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.photos_remove_from_album)) },
            text = { Text("Rimuovere ${selectedPhotoIds.size} foto da questo album? Le foto resteranno nella libreria.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removePhotosFromAlbum(selectedPhotoIds)
                        selectedPhotoIds = emptySet()
                        isSelectionMode = false
                        showRemoveConfirm = false
                    },
                ) { Text(stringResource(R.string.photos_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(R.string.life_cancel)) }
            },
        )
    }

    val startIndex = viewerPhotoId?.let { id -> state.photos.indexOfFirst { it.id == id } } ?: -1
    if (startIndex >= 0) {
        PhotosFullscreenMediaViewer(
            photos = state.photos,
            startIndex = startIndex,
            onDismiss = { viewerPhotoId = null },
            onDelete = { photo -> viewModel.deletePhoto(photo.id) },
            onOpenExternal = { photo, viewerScope ->
                viewerScope.launch {
                    runCatching {
                        val file = withContext(Dispatchers.IO) { viewModel.preparePreviewFile(photo) }
                        openAlbumPhotoMedia(context, photo.mimeType, file)
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.photos_open_media_error), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSaveEditedCopy = { _, _ -> },
            prepareFile = { photo -> viewModel.preparePreviewFile(photo) },
        )
    }
}

@Composable
private fun AlbumDetailTopHeader(
    title: String,
    isSelectionMode: Boolean,
    allSelected: Boolean,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onToggleSelection: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack, filled = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) {
                TextButton(onClick = onSelectAll) {
                    Text(if (allSelected) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.kidBoxColors.card,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.15f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderCircleButton(
                            icon = Icons.Default.CameraAlt,
                            onClick = onCamera,
                            modifier = Modifier.size(44.dp),
                            filled = false,
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.2f)),
                        )
                        TextButton(onClick = onToggleSelection) {
                            Text(stringResource(R.string.photos_select), color = MaterialTheme.kidBoxColors.title)
                        }
                    }
                }
            }
        }
    }
    Text(
        text = title,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.kidBoxColors.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TripDedicatedAlbumBanner(modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = kb.title)
                Text(
                    text = stringResource(R.string.photos_trip_album),
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    fontSize = 15.sp,
                )
            }
            Text(
                text = "Sei nell'album KidBox creato per questo viaggio. Le foto che scatti qui e quelle che aggiungi dalla libreria verranno salvate automaticamente in questo album.",
                color = kb.subtitle,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun AlbumEmptyState(
    isTripAlbum: Boolean,
    onCamera: () -> Unit,
    onPickFromLibrary: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Image, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.photos_album_empty), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isTripAlbum) {
                stringResource(R.string.photos_trip_album_hint)
            } else {
                stringResource(R.string.photos_album_add_hint)
            },
            color = kb.subtitle,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = onCamera,
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFF2D6F),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photos_take_photo), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!isTripAlbum) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onPickFromLibrary) {
                Text(stringResource(R.string.photos_add_from_library))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumPhotoGridCell(
    photo: KBFamilyPhotoEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isUploading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .combinedClickable(onClick = onClick),
    ) {
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
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(photosFormatDuration(photo.videoDurationSeconds), color = Color.White, fontSize = 10.sp)
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
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Color(0xFF1E88E5).copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.2f)),
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
                    modifier = Modifier.size(22.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AlbumPhotoSelectionBar(
    selectedCount: Int,
    onRemove: () -> Unit,
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
            PhotosActionIconButton(icon = Icons.Default.Close, label = stringResource(R.string.life_cancel), enabled = true, onClick = onDeselect)
            PhotosActionIconButton(
                icon = Icons.Default.RemoveCircleOutline,
                label = stringResource(R.string.photos_remove),
                enabled = selectedCount > 0,
                onClick = onRemove,
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
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp),
    filled: Boolean = true,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (filled) MaterialTheme.kidBoxColors.card else Color.Transparent,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

private fun openAlbumPhotoMedia(
    context: android.content.Context,
    mimeType: String,
    file: java.io.File,
) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType.ifBlank { "*/*" })
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
