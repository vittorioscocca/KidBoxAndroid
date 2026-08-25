package it.vittorioscocca.kidbox.ui.screens.documents

import it.vittorioscocca.kidbox.ui.permissions.rememberCameraPermissionRequester
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsFeature

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.ui.components.FamilyKeyMissingGate
import it.vittorioscocca.kidbox.ui.components.FamilyKeyMissingDialog
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerFullscreenDialog
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.util.rememberSingleImagePicker
import it.vittorioscocca.kidbox.ui.util.singleImageRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import it.vittorioscocca.kidbox.notifications.AppSection
import it.vittorioscocca.kidbox.notifications.TrackSectionPresence
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FolderOpen

private const val TAG_DOC_OPEN = "KB_Doc_Open"
private sealed interface ContextMenuTarget {
    data class Document(val value: KBDocumentEntity) : ContextMenuTarget
    data class Folder(val value: KBDocumentCategoryEntity) : ContextMenuTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentBrowserScreen(
    familyId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    initialHighlightDocumentId: String? = null,
    initialFolderId: String = "root",
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrackSectionPresence(AppSection.DOCUMENTS, familyId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Copre in un colpo solo le decifrature che qui falliscono in silenzio.
    FamilyKeyMissingGate(familyId)
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    var showUploadVisibilityGate by remember { mutableStateOf(false) }
    var showUploadVisibilityPicker by remember { mutableStateOf(false) }
    var draftUploadScope by remember { mutableStateOf(KBVisibilityScope.FAMILY) }
    var draftUploadMemberIds by remember { mutableStateOf(setOf<String>()) }
    var documentInfoForDialog by remember { mutableStateOf<KBDocumentEntity?>(null) }
    var showDocumentDetailVisibilityPicker by remember { mutableStateOf(false) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showCopySheet by remember { mutableStateOf(false) }
    var showMergeSheet by remember { mutableStateOf(false) }
    var showUnlockSheet by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var isOpeningDocument by remember { mutableStateOf(false) }
    var isMergingPdfs by remember { mutableStateOf(false) }
    var isUnlockingPdf by remember { mutableStateOf(false) }
    var mergeNameDraft by remember { mutableStateOf("") }
    var mergeCandidates by remember { mutableStateOf<List<KBDocumentEntity>>(emptyList()) }
    var unlockTarget by remember { mutableStateOf<KBDocumentEntity?>(null) }
    var unlockNameDraft by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var contextMenuTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var singleMoveTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var singleCopyTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var uploadTargetFolderId by remember { mutableStateOf<String?>(null) }
    val msgReportedTodo = stringResource(R.string.documents_reported_todo)
    val shareFolderChooserTitle = stringResource(R.string.documents_share_folder_chooser)

    LaunchedEffect(familyId, initialHighlightDocumentId, initialFolderId) {
        viewModel.startObservingActiveFamily(routeFamilyId = familyId)
        viewModel.bindFamily(familyId)
        if (!initialHighlightDocumentId.isNullOrBlank()) {
            viewModel.focusDocument(initialHighlightDocumentId)
        }
        if (initialFolderId.isBlank()) {
            // Keep a safe non-null navigation default for folder route param.
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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        val fileName = "camera_${System.currentTimeMillis()}.jpg"
        viewModel.importDocument(
            fileName = fileName,
            mimeType = "image/jpeg",
            bytes = bytes,
            targetFolderId = uploadTargetFolderId,
        )
    }
    val requestDocumentCamera = rememberCameraPermissionRequester(
        onDenied = {
            Toast.makeText(context, context.getString(R.string.documents_camera_permission_required), Toast.LENGTH_SHORT).show()
        },
        onLaunchCamera = { cameraLauncher.launch(null) },
    )

    val photoLibraryLauncher = rememberSingleImagePicker { uri ->
        uri?.let { u ->
            val mime = context.contentResolver.getType(u) ?: "image/jpeg"
            val fileName = resolveFileName(context, u, mime)
            val bytes = context.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: ByteArray(0)
            if (bytes.isNotEmpty()) {
                viewModel.importDocument(
                    fileName = fileName,
                    mimeType = mime,
                    bytes = bytes,
                    targetFolderId = uploadTargetFolderId,
                )
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = resolveFileName(context, uri, mime)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        if (bytes.isNotEmpty()) {
            viewModel.importDocument(
                fileName = fileName,
                mimeType = mime,
                bytes = bytes,
                targetFolderId = uploadTargetFolderId,
            )
        }
    }
    val sortedFolders = remember(state.folders, state.sort, state.sortAscending) {
        sortFolders(state.folders, state.sort, state.sortAscending)
    }
    val sortedDocuments = remember(state.documents, state.sort, state.sortAscending) {
        sortDocuments(state.documents, state.sort, state.sortAscending)
    }
    val selectedCount = state.selectedFolderIds.size + state.selectedDocumentIds.size
    // Cartella vuota: restano solo icona, testo e pulsante dell'empty state.
    val isEmptyBrowser = sortedFolders.isEmpty() && sortedDocuments.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = {
                    if (!viewModel.navigateBack()) onBack()
                })
                Spacer(modifier = Modifier.weight(1f))
                SelectionHeaderPill(
                    showSelectAction = !isEmptyBrowser,
                    selecting = state.isSelecting,
                    selectedCount = selectedCount,
                    onToggleSelection = { viewModel.toggleSelectionMode() },
                    onAdd = {
                        if (!state.isSelecting) {
                            uploadTargetFolderId = state.breadcrumbs.lastOrNull()?.id
                            draftUploadScope = KBVisibilityScope.FAMILY
                            draftUploadMemberIds = emptySet()
                            showUploadVisibilityGate = true
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                // La radice (id == null) usa sempre l'etichetta tradotta: il titolo nel
                // breadcrumb è un default italiano cablato nel ViewModel.
                text = state.breadcrumbs.lastOrNull()
                    ?.takeIf { it.id != null }
                    ?.title
                    ?: stringResource(R.string.documents_default_title),
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.kidBoxColors.title,
            )

            if (!isEmptyBrowser) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ModeButton(
                    selected = state.mode == DocumentsViewMode.GRID,
                    icon = Icons.Default.GridView,
                    onClick = { viewModel.setMode(DocumentsViewMode.GRID) },
                )
                ModeButton(
                    selected = state.mode == DocumentsViewMode.LIST,
                    icon = Icons.Default.List,
                    onClick = { viewModel.setMode(DocumentsViewMode.LIST) },
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.kidBoxColors.card) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.size(14.dp))
                        Text(" ${state.folders.size}", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                        Text(" • ", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.size(14.dp))
                        Text(" ${state.documents.size}", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip(
                    label = stringResource(R.string.documents_sort_name),
                    selected = state.sort == DocumentsSort.NAME,
                    ascending = state.sortAscending,
                    onClick = { viewModel.setSort(DocumentsSort.NAME) },
                )
                SortChip(
                    label = stringResource(R.string.documents_sort_type),
                    selected = state.sort == DocumentsSort.TYPE,
                    ascending = state.sortAscending,
                    onClick = { viewModel.setSort(DocumentsSort.TYPE) },
                )
                SortChip(
                    label = stringResource(R.string.documents_sort_date),
                    selected = state.sort == DocumentsSort.DATE,
                    ascending = state.sortAscending,
                    onClick = { viewModel.setSort(DocumentsSort.DATE) },
                )
                SortChip(
                    label = stringResource(R.string.documents_sort_size),
                    selected = state.sort == DocumentsSort.SIZE,
                    ascending = state.sortAscending,
                    onClick = { viewModel.setSort(DocumentsSort.SIZE) },
                )
            }
            }

            Spacer(Modifier.height(10.dp))
            if (isEmptyBrowser) {
                KBEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.empty_documents_title),
                    body = stringResource(R.string.empty_documents_body),
                    primaryIcon = Icons.Filled.AddCircle,
                    primaryLabel = stringResource(R.string.empty_documents_action),
                    onPrimary = { showUploadSheet = true },
                )
            } else if (state.mode == DocumentsViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sortedFolders, key = { "folder_${it.id}" }) { folder ->
                        FolderGridItem(
                            folder = folder,
                            isSelected = folder.id in state.selectedFolderIds,
                            selecting = state.isSelecting,
                            onClick = {
                                if (state.isSelecting) viewModel.toggleFolderSelection(folder.id)
                                else viewModel.navigateToFolder(folder)
                            },
                            onLongPress = { contextMenuTarget = ContextMenuTarget.Folder(folder) },
                        )
                    }
                    items(sortedDocuments, key = { "doc_${it.id}" }) { doc ->
                        DocumentGridItem(
                            document = doc,
                            isSelected = doc.id in state.selectedDocumentIds,
                            isHighlighted = doc.id == state.highlightedDocumentId,
                            selecting = state.isSelecting,
                            onClick = {
                                if (state.isSelecting) {
                                    viewModel.toggleDocumentSelection(doc.id)
                                } else {
                                    viewModel.clearHighlightedDocument()
                                    KBLog.ui.info("tap document id=${doc.id} mime=${doc.mimeType} hasLocal=${!doc.localPath.isNullOrBlank()} hasRemote=${!doc.storagePath.isBlank()}", TAG_DOC_OPEN)
                                    scope.launch {
                                        isOpeningDocument = true
                                        try {
                                            openDocument(context, viewModel, doc)
                                        } finally {
                                            isOpeningDocument = false
                                        }
                                    }
                                }
                            },
                            onLongPress = { contextMenuTarget = ContextMenuTarget.Document(doc) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(if (state.isSelecting) 90.dp else 24.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedFolders, key = { "folder_${it.id}" }) { folder ->
                        FolderListItem(
                            folder = folder,
                            selecting = state.isSelecting,
                            isSelected = folder.id in state.selectedFolderIds,
                            onClick = {
                                if (state.isSelecting) viewModel.toggleFolderSelection(folder.id)
                                else viewModel.navigateToFolder(folder)
                            },
                            onLongPress = { contextMenuTarget = ContextMenuTarget.Folder(folder) },
                        )
                    }
                    items(sortedDocuments, key = { "doc_${it.id}" }) { doc ->
                        DocumentListItem(
                            document = doc,
                            selecting = state.isSelecting,
                            isSelected = doc.id in state.selectedDocumentIds,
                            isHighlighted = doc.id == state.highlightedDocumentId,
                            onClick = {
                                if (state.isSelecting) {
                                    viewModel.toggleDocumentSelection(doc.id)
                                } else {
                                    viewModel.clearHighlightedDocument()
                                    KBLog.ui.info("tap document id=${doc.id} mime=${doc.mimeType} hasLocal=${!doc.localPath.isNullOrBlank()} hasRemote=${!doc.storagePath.isBlank()}", TAG_DOC_OPEN)
                                    scope.launch {
                                        isOpeningDocument = true
                                        try {
                                            openDocument(context, viewModel, doc)
                                        } finally {
                                            isOpeningDocument = false
                                        }
                                    }
                                }
                            },
                            onLongPress = { contextMenuTarget = ContextMenuTarget.Document(doc) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(if (state.isSelecting) 90.dp else 24.dp)) }
                }
            }
        }

        if (state.isSelecting) {
            val selectedPdfDocsForActions = remember(state.selectedDocumentIds, state.documents) {
                viewModel.selectedDocuments().filter {
                    it.mimeType.contains("pdf", ignoreCase = true) ||
                        it.fileName.endsWith(".pdf", ignoreCase = true)
                }
            }
            val canMergeNow = selectedPdfDocsForActions.size >= 2 &&
                selectedPdfDocsForActions.size == state.selectedDocumentIds.size
            val canUnlockNow = selectedPdfDocsForActions.size == 1 &&
                state.selectedDocumentIds.size == 1
            val msgSelectAtLeast2Pdfs = stringResource(R.string.documents_select_at_least_2_pdfs)
            val msgSelectSinglePdf = stringResource(R.string.documents_select_single_pdf)
            val msgSelectAtLeastOneFile = stringResource(R.string.documents_select_at_least_one_file)
            val msgMergeSuccess = stringResource(R.string.documents_merge_success)
            val msgWrongPassword = stringResource(R.string.documents_wrong_password)
            val msgUnlockSuccess = stringResource(R.string.documents_unlock_success)
            SelectionBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                onMove = { showMoveSheet = true; singleMoveTarget = null },
                onMerge = {
                    val selected = selectedPdfDocsForActions
                    if (selected.size < 2) {
                        Toast.makeText(context, msgSelectAtLeast2Pdfs, Toast.LENGTH_SHORT).show()
                    } else {
                        mergeCandidates = selected
                        mergeNameDraft = buildMergedPdfTitle(selected)
                        showMergeSheet = true
                    }
                },
                onUnlock = {
                    val target = selectedPdfDocsForActions.firstOrNull()
                    if (target == null || state.selectedDocumentIds.size != 1) {
                        Toast.makeText(context, msgSelectSinglePdf, Toast.LENGTH_SHORT).show()
                    } else {
                        unlockTarget = target
                        unlockNameDraft = buildUnlockedPdfDefaultName(target)
                        showUnlockSheet = true
                    }
                },
                onShare = {
                    scope.launch {
                        val docs = viewModel.selectedDocuments()
                        if (docs.isEmpty()) {
                            Toast.makeText(context, msgSelectAtLeastOneFile, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        shareDocuments(context, viewModel, docs)
                    }
                },
                onChat = {
                    if (state.selectedDocumentIds.isNotEmpty()) {
                        viewModel.clearSelection()
                        onNavigate(AppDestination.Chat.route)
                    } else {
                        Toast.makeText(context, msgSelectAtLeastOneFile, Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { viewModel.deleteSelected() },
                shareEnabled = state.selectedDocumentIds.isNotEmpty(),
                mergeEnabled = canMergeNow,
                unlockEnabled = canUnlockNow,
                hasSelection = selectedCount > 0,
                chatEnabled = state.selectedDocumentIds.isNotEmpty(),
            )
        }

        if (isOpeningDocument || isMergingPdfs || isUnlockingPdf) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = when {
                                isMergingPdfs -> stringResource(R.string.documents_merging_pdf)
                                isUnlockingPdf -> stringResource(R.string.documents_unlocking_pdf)
                                else -> stringResource(R.string.documents_opening_document)
                            },
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.kidBoxColors.title,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (state.isStabilizingHierarchy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.kidBoxColors.card,
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.documents_stabilizing_hierarchy),
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.kidBoxColors.title,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showMoveSheet) {
        MoveSelectionBottomSheet(
            folders = state.folders,
            onDismiss = { showMoveSheet = false },
            onMoveTo = { destination ->
                showMoveSheet = false
                when (val target = singleMoveTarget) {
                    is ContextMenuTarget.Document -> viewModel.moveSingleDocument(target.value, destination)
                    is ContextMenuTarget.Folder -> viewModel.moveSingleFolder(target.value, destination)
                    null -> viewModel.moveSelected(destination)
                }
                singleMoveTarget = null
            },
        )
    }

    if (showCopySheet) {
        MoveSelectionBottomSheet(
            folders = state.folders,
            title = stringResource(R.string.documents_copy_items_title),
            onDismiss = { showCopySheet = false },
            onMoveTo = { destination ->
                showCopySheet = false
                when (val target = singleCopyTarget) {
                    is ContextMenuTarget.Document -> viewModel.duplicateDocument(target.value, destination)
                    is ContextMenuTarget.Folder -> viewModel.duplicateFolder(target.value, destination)
                    null -> Unit
                }
                singleCopyTarget = null
            },
        )
    }

    if (showMergeSheet) {
        MergePdfBottomSheet(
            nameDraft = mergeNameDraft,
            documents = mergeCandidates,
            onDismiss = { showMergeSheet = false },
            onNameChange = { mergeNameDraft = it },
            onMove = { from, to ->
                if (from !in mergeCandidates.indices || to !in mergeCandidates.indices || from == to) return@MergePdfBottomSheet
                val mutable = mergeCandidates.toMutableList()
                val moved = mutable.removeAt(from)
                mutable.add(to, moved)
                mergeCandidates = mutable.toList()
            },
            onConfirm = {
                val ordered = mergeCandidates
                showMergeSheet = false
                scope.launch {
                    isMergingPdfs = true
                    try {
                        val files = ordered.map { viewModel.preparePreviewFile(it) }
                        val mergedBytes = mergePdfFiles(files)
                        val mergedFileName = buildMergedPdfFileName(mergeNameDraft, ordered)
                        viewModel.importDocument(
                            fileName = mergedFileName,
                            mimeType = "application/pdf",
                            bytes = mergedBytes,
                            targetFolderId = state.breadcrumbs.lastOrNull()?.id,
                        )
                        viewModel.clearSelection()
                        Toast.makeText(context, context.getString(R.string.documents_merge_success), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.documents_merge_error_prefix, e.localizedMessage ?: "sconosciuto"),
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        isMergingPdfs = false
                    }
                }
            },
        )
    }

    if (showUnlockSheet) {
        val target = unlockTarget
        if (target == null) {
            showUnlockSheet = false
        } else {
            UnlockPdfBottomSheet(
                document = target,
                nameDraft = unlockNameDraft,
                onNameChange = { unlockNameDraft = it },
                onDismiss = {
                    showUnlockSheet = false
                    unlockTarget = null
                },
                onConfirm = { password ->
                    val source = unlockTarget ?: return@UnlockPdfBottomSheet
                    showUnlockSheet = false
                    val nameDraftAtConfirm = unlockNameDraft
                    scope.launch {
                        isUnlockingPdf = true
                        try {
                            val file = viewModel.preparePreviewFile(source)
                            val unlockedBytes = unlockPdfFile(context, file, password)
                            val unlockedFileName = buildUnlockedPdfFileName(nameDraftAtConfirm, source)
                            viewModel.importDocument(
                                fileName = unlockedFileName,
                                mimeType = "application/pdf",
                                bytes = unlockedBytes,
                                targetFolderId = state.breadcrumbs.lastOrNull()?.id,
                            )
                            viewModel.clearSelection()
                            Toast.makeText(context, context.getString(R.string.documents_unlock_success), Toast.LENGTH_SHORT).show()
                        } catch (e: IllegalArgumentException) {
                            // Distinta password errata: non chiudo lo sheet così
                            // l'utente può riprovare? Per ora chiudo sempre e
                            // mostro toast: l'utente può riaprire.
                            Toast.makeText(context, e.message ?: context.getString(R.string.documents_wrong_password), Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.documents_unlock_error_prefix, e.localizedMessage ?: "sconosciuto"),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            isUnlockingPdf = false
                            unlockTarget = null
                        }
                    }
                },
            )
        }
    }

    if (showUploadVisibilityGate) {
        AlertDialog(
            onDismissRequest = {
                showUploadVisibilityPicker = false
                showUploadVisibilityGate = false
            },
            title = { Text(stringResource(R.string.documents_upload_visibility_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.documents_upload_visibility_description),
                        color = MaterialTheme.kidBoxColors.subtitle,
                    )
                    Surface(
                        onClick = { showUploadVisibilityPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.kidBoxColors.card,
                    ) {
                        Text(
                            KBVisibilityScope.chipLabel(draftUploadScope),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.kidBoxColors.title,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setPendingUploadVisibility(draftUploadScope, draftUploadMemberIds)
                        showUploadVisibilityPicker = false
                        showUploadVisibilityGate = false
                        showUploadSheet = true
                    },
                ) { Text(stringResource(R.string.documents_continue)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUploadVisibilityPicker = false
                        showUploadVisibilityGate = false
                    },
                ) { Text(stringResource(R.string.documents_cancel)) }
            },
        )
    }

    if (showUploadVisibilityPicker && showUploadVisibilityGate) {
        VisibilityPickerFullscreenDialog(
            currentUid = currentUid,
            scopeSectionTitle = stringResource(R.string.documents_visibility_who_can_see),
            membersExcludingSelf = state.visibilityMembers,
            initialScope = draftUploadScope,
            initialMemberIds = draftUploadMemberIds.toList(),
            onDismiss = { showUploadVisibilityPicker = false },
            onConfirmed = { scope, ids ->
                draftUploadScope = KBVisibilityScope.normalized(scope)
                draftUploadMemberIds = ids.toSet()
                showUploadVisibilityPicker = false
            },
        )
    }

    if (documentInfoForDialog != null) {
        val docInfo = documentInfoForDialog!!
        AlertDialog(
            onDismissRequest = {
                documentInfoForDialog = null
                showDocumentDetailVisibilityPicker = false
            },
            title = { Text(docInfo.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.documents_visibility_label), fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                    Surface(
                        onClick = { showDocumentDetailVisibilityPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.kidBoxColors.card,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                KBVisibilityScope.chipLabel(docInfo.visibilityScope),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.kidBoxColors.title,
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.kidBoxColors.subtitle,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.documents_visibility_tap_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.kidBoxColors.subtitle,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        documentInfoForDialog = null
                        showDocumentDetailVisibilityPicker = false
                    },
                ) { Text(stringResource(R.string.documents_close)) }
            },
        )
    }

    if (showDocumentDetailVisibilityPicker && documentInfoForDialog != null) {
        val d = documentInfoForDialog!!
        VisibilityPickerFullscreenDialog(
            currentUid = currentUid,
            scopeSectionTitle = stringResource(R.string.documents_visibility_who_can_see),
            membersExcludingSelf = state.visibilityMembers,
            initialScope = KBVisibilityScope.normalized(d.visibilityScope),
            initialMemberIds = decodeStringList(d.visibilityMemberIdsJson),
            onDismiss = { showDocumentDetailVisibilityPicker = false },
            onConfirmed = { scope, ids ->
                viewModel.updateDocumentVisibility(d, scope, ids)
                showDocumentDetailVisibilityPicker = false
                documentInfoForDialog = null
            },
        )
    }

    if (showUploadSheet) {
        DocumentUploadBottomSheet(
            onDismiss = {
                showUploadSheet = false
                uploadTargetFolderId = null
            },
            onCamera = {
                showUploadSheet = false
                requestDocumentCamera()
            },
            onPhotoLibrary = {
                showUploadSheet = false
                photoLibraryLauncher.launch(singleImageRequest())
            },
            onFile = {
                showUploadSheet = false
                documentLauncher.launch(arrayOf("*/*"))
            },
            onCreateFolder = {
                showUploadSheet = false
                showCreateFolderDialog = true
            },
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                folderName = ""
            },
            title = { Text(stringResource(R.string.documents_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text(stringResource(R.string.documents_folder_name_placeholder)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createFolder(folderName)
                    folderName = ""
                    showCreateFolderDialog = false
                }) { Text(stringResource(R.string.documents_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    folderName = ""
                    showCreateFolderDialog = false
                }) { Text(stringResource(R.string.documents_cancel)) }
            },
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = {
                renameTarget = null
                renameText = ""
            },
            title = { Text(stringResource(R.string.documents_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = { Text(stringResource(R.string.documents_new_name_placeholder)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    if (target != null && renameText.isNotBlank()) {
                        when (target) {
                            is ContextMenuTarget.Document -> viewModel.renameDocument(target.value, renameText)
                            is ContextMenuTarget.Folder -> viewModel.renameFolder(target.value, renameText)
                        }
                    }
                    renameTarget = null
                    renameText = ""
                }) { Text(stringResource(R.string.documents_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    renameTarget = null
                    renameText = ""
                }) { Text(stringResource(R.string.documents_cancel)) }
            },
        )
    }

    if (contextMenuTarget != null) {
        val isDocumentTarget = contextMenuTarget is ContextMenuTarget.Document
        ContextActionBottomSheet(
            onDismiss = { contextMenuTarget = null },
            showDocumentInfo = isDocumentTarget,
            onDocumentInfo = {
                val d = (contextMenuTarget as? ContextMenuTarget.Document)?.value
                if (d != null) {
                    documentInfoForDialog = d
                    showDocumentDetailVisibilityPicker = false
                }
                contextMenuTarget = null
            },
            onRename = {
                val target = contextMenuTarget ?: return@ContextActionBottomSheet
                renameText = when (target) {
                    is ContextMenuTarget.Document -> target.value.title
                    is ContextMenuTarget.Folder -> target.value.title
                }
                renameTarget = target
                contextMenuTarget = null
            },
            onMove = {
                singleMoveTarget = contextMenuTarget
                contextMenuTarget = null
                showMoveSheet = true
            },
            onCopy = {
                singleCopyTarget = contextMenuTarget
                contextMenuTarget = null
                showCopySheet = true
            },
            onDuplicate = {
                when (val target = contextMenuTarget) {
                    is ContextMenuTarget.Document -> viewModel.duplicateDocument(target.value)
                    is ContextMenuTarget.Folder -> viewModel.duplicateFolder(target.value)
                    null -> Unit
                }
                contextMenuTarget = null
            },
            onShare = {
                scope.launch {
                    when (val target = contextMenuTarget) {
                        is ContextMenuTarget.Document -> shareDocuments(context, viewModel, listOf(target.value))
                        is ContextMenuTarget.Folder -> {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, target.value.title)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(send, shareFolderChooserTitle))
                        }
                        null -> Unit
                    }
                }
                contextMenuTarget = null
            },
            onChat = {
                onNavigate(AppDestination.Chat.route)
                contextMenuTarget = null
            },
            onReportTodo = {
                onNavigate(AppDestination.Todo.route)
                Toast.makeText(context, msgReportedTodo, Toast.LENGTH_SHORT).show()
                contextMenuTarget = null
            },
            onDelete = {
                when (val target = contextMenuTarget) {
                    is ContextMenuTarget.Document -> viewModel.deleteDocument(target.value)
                    is ContextMenuTarget.Folder -> viewModel.deleteFolder(target.value)
                    null -> Unit
                }
                contextMenuTarget = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentUploadBottomSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotoLibrary: () -> Unit,
    onFile: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.documents_add_document_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                // Il foglio è tinto con un colore del tema e non con uno schema
                // Material: senza colore esplicito il titolo eredita il nero di
                // default e in tema scuro sparisce. Le voci qui sotto lo
                // impostavano già, questo era rimasto indietro.
                color = MaterialTheme.kidBoxColors.title,
            )
            SheetAction(stringResource(R.string.documents_take_photo), icon = Icons.Default.Image, onClick = onCamera)
            SheetAction(stringResource(R.string.documents_upload_photo), icon = Icons.Default.Image, onClick = onPhotoLibrary)
            SheetAction(stringResource(R.string.documents_upload_file), icon = Icons.Default.Description, onClick = onFile)
            SheetAction(stringResource(R.string.documents_create_folder), icon = Icons.Default.Folder, onClick = onCreateFolder)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetAction(
    title: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.kidBoxColors.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.kidBoxColors.title,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.kidBoxColors.title,
            )
        }
    }
}

@Composable
private fun FolderGridItem(
    folder: KBDocumentCategoryEntity,
    isSelected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            }
            .background(if (isSelected) Color(0xFFEAF4FF) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF2196F3) else Color(0xFFBDBDBD),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFF941A), modifier = Modifier.size(54.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                folder.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.kidBoxColors.title,
            )
            Text(formatRelativeTime(folder.updatedAtEpochMillis), color = Color(0xFF8B8B8B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun DocumentGridItem(
    document: KBDocumentEntity,
    isSelected: Boolean,
    isHighlighted: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val itemBackground = when {
        isSelected -> Color(0xFFEAF4FF)
        isHighlighted -> Color(0xFFFFF6D9)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            }
            .background(itemBackground, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF2196F3) else Color(0xFFBDBDBD),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (document.mimeType.startsWith("image/") && !document.localPath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(document.localPath),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)),
                    )
                } else {
                    val icon = iconForMime(document.mimeType)
                    Icon(icon, contentDescription = null, tint = Color(0xFF5F6B7A), modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                document.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.kidBoxColors.title,
            )
            Text(document.fileName, color = Color(0xFF8B8B8B), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatRelativeTime(document.updatedAtEpochMillis), color = Color(0xFF8B8B8B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FolderListItem(
    folder: KBDocumentCategoryEntity,
    selecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            }
            .background(if (isSelected) Color(0xFFEAF4FF) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF2196F3) else Color(0xFFBDBDBD),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFF941A), modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(folder.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
            Text(formatRelativeTime(folder.updatedAtEpochMillis), color = Color(0xFF8B8B8B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun DocumentListItem(
    document: KBDocumentEntity,
    selecting: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val itemBackground = when {
        isSelected -> Color(0xFFEAF4FF)
        isHighlighted -> Color(0xFFFFF6D9)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            }
            .background(itemBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF2196F3) else Color(0xFFBDBDBD),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (document.mimeType.startsWith("image/") && !document.localPath.isNullOrBlank()) {
            AsyncImage(
                model = File(document.localPath),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)),
            )
        } else {
            Icon(iconForMime(document.mimeType), contentDescription = null, tint = Color(0xFF5F6B7A), modifier = Modifier.size(34.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                document.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.kidBoxColors.title,
            )
            Text(
                "${document.fileName} • ${formatRelativeTime(document.updatedAtEpochMillis)}",
                fontSize = 12.sp,
                color = Color(0xFF8B8B8B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.kidBoxColors.card,
        shadowElevation = 6.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.kidBoxColors.card else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kidBoxColors.divider),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.kidBoxColors.title,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFFDFF0FF) else MaterialTheme.kidBoxColors.card,
        onClick = onClick,
    ) {
        Text(
            text = if (selected) "$label ${if (ascending) "↑" else "↓"}" else label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.kidBoxColors.subtitle,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SelectionHeaderPill(
    selecting: Boolean,
    selectedCount: Int,
    onToggleSelection: () -> Unit,
    onAdd: () -> Unit,
    showSelectAction: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.kidBoxColors.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showSelectAction) {
                Text(
                    text = if (selecting) {
                        if (selectedCount > 0) {
                            stringResource(R.string.documents_selection_done_with_count, selectedCount)
                        } else {
                            stringResource(R.string.documents_selection_done)
                        }
                    } else {
                        stringResource(R.string.documents_selection_select)
                    },
                    modifier = Modifier
                        .clickable(onClick = onToggleSelection)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.kidBoxColors.title,
                )
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .width(1.dp)
                        .background(MaterialTheme.kidBoxColors.divider),
                )
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.documents_add_content_description),
                tint = if (selecting) MaterialTheme.kidBoxColors.subtitle else MaterialTheme.kidBoxColors.title,
                modifier = Modifier
                    .clickable(enabled = !selecting, onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun SelectionBottomBar(
    modifier: Modifier = Modifier,
    onMove: () -> Unit,
    onMerge: () -> Unit,
    onUnlock: () -> Unit,
    onShare: () -> Unit,
    onChat: () -> Unit,
    onDelete: () -> Unit,
    shareEnabled: Boolean,
    mergeEnabled: Boolean,
    unlockEnabled: Boolean,
    chatEnabled: Boolean,
    hasSelection: Boolean,
) {
    Surface(
        modifier = modifier.navigationBarsPadding(),
        color = MaterialTheme.kidBoxColors.card,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomAction(stringResource(R.string.documents_action_move), Icons.Default.DriveFileMove, onMove, hasSelection)
            BottomAction(stringResource(R.string.documents_action_merge), Icons.Default.MergeType, onMerge, mergeEnabled)
            BottomAction(stringResource(R.string.documents_action_unlock), Icons.Default.LockOpen, onUnlock, unlockEnabled)
            BottomAction(stringResource(R.string.documents_action_share), Icons.Default.Share, onShare, shareEnabled)
            BottomAction(stringResource(R.string.documents_action_chat), Icons.AutoMirrored.Filled.Chat, onChat, chatEnabled)
            BottomAction(stringResource(R.string.documents_action_delete), Icons.Default.Delete, onDelete, hasSelection, tint = Color(0xFFE35156))
        }
    }
}

@Composable
private fun BottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    enabled: Boolean,
    tint: Color = Color(0xFFE09A3D),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) tint else Color(0xFFBDBDBD),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (enabled) tint else Color(0xFFBDBDBD),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextActionBottomSheet(
    onDismiss: () -> Unit,
    showDocumentInfo: Boolean,
    onDocumentInfo: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onChat: () -> Unit,
    onReportTodo: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showDocumentInfo) {
                SheetAction(stringResource(R.string.documents_menu_info), icon = Icons.Default.Info, onClick = onDocumentInfo)
            }
            SheetAction(stringResource(R.string.documents_menu_rename), icon = Icons.Default.Edit, onClick = onRename)
            SheetAction(stringResource(R.string.documents_menu_move_to), icon = Icons.Default.DriveFileMove, onClick = onMove)
            SheetAction(stringResource(R.string.documents_menu_copy_to), icon = Icons.Default.ContentCopy, onClick = onCopy)
            SheetAction(stringResource(R.string.documents_menu_duplicate), icon = Icons.Default.FileCopy, onClick = onDuplicate)
            SheetAction(stringResource(R.string.documents_menu_share), icon = Icons.Default.Share, onClick = onShare)
            SheetAction(stringResource(R.string.documents_menu_chat), icon = Icons.AutoMirrored.Filled.Chat, onClick = onChat)
            SheetAction(stringResource(R.string.documents_menu_report_todo), icon = Icons.Default.TaskAlt, onClick = onReportTodo)
            SheetAction(stringResource(R.string.documents_menu_delete), icon = Icons.Default.Delete, onClick = onDelete)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSelectionBottomSheet(
    folders: List<KBDocumentCategoryEntity>,
    title: String? = null,
    onDismiss: () -> Unit,
    onMoveTo: (String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title ?: stringResource(R.string.documents_move_items_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.kidBoxColors.title,
            )
            SheetAction(stringResource(R.string.documents_root_folder), icon = Icons.Default.Folder, onClick = { onMoveTo(null) })
            folders.forEach { folder ->
                SheetAction(folder.title, icon = Icons.Default.Folder, onClick = { onMoveTo(folder.id) })
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergePdfBottomSheet(
    nameDraft: String,
    documents: List<KBDocumentEntity>,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dragThresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleActionButton(
                    label = stringResource(R.string.documents_cancel),
                    onClick = onDismiss,
                    enabled = true,
                    modifier = Modifier.width(92.dp),
                )
                Text(
                    text = stringResource(R.string.documents_merge_pdf_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.kidBoxColors.title,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                CapsuleActionButton(
                    label = stringResource(R.string.documents_action_merge),
                    onClick = onConfirm,
                    enabled = documents.size >= 2 && nameDraft.isNotBlank(),
                    modifier = Modifier.width(92.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.documents_merged_pdf_name_label),
                color = MaterialTheme.kidBoxColors.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = nameDraft,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                placeholder = { Text(stringResource(R.string.documents_merged_pdf_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.documents_pages_order_label, documents.size),
                    color = MaterialTheme.kidBoxColors.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = Color(0xFFB8B8B8),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.documents_drag_to_reorder),
                    color = Color(0xFFB8B8B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                tonalElevation = 0.dp,
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(documents, key = { _, doc -> doc.id }) { index, doc ->
                        MergePdfRow(
                            index = index,
                            document = doc,
                            totalCount = documents.size,
                            dragThresholdPx = dragThresholdPx,
                            onMove = onMove,
                        )
                        if (index < documents.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .height(1.dp)
                                    .background(Color(0xFFECECEC)),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CapsuleActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) Color.White else Color(0xFFF0F0F0),
        modifier = modifier.height(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.kidBoxColors.title else Color(0xFFB8B8B8),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockPdfBottomSheet(
    document: KBDocumentEntity,
    nameDraft: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleActionButton(
                    label = "Annulla",
                    onClick = onDismiss,
                    enabled = true,
                    modifier = Modifier.width(92.dp),
                )
                Text(
                    text = stringResource(R.string.documents_unlock_pdf_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.kidBoxColors.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                CapsuleActionButton(
                    label = "Sblocca",
                    onClick = { onConfirm(password) },
                    enabled = password.isNotEmpty() && nameDraft.isNotBlank(),
                    modifier = Modifier.width(92.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ── Documento di origine ─────────────────────────────────────
            Text(
                text = "PDF da sbloccare",
                color = MaterialTheme.kidBoxColors.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFE35156),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = document.title.ifBlank { document.fileName },
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.kidBoxColors.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatFileSize(document.fileSize),
                            color = MaterialTheme.kidBoxColors.subtitle,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Password ─────────────────────────────────────────────────
            Text(
                text = "Password del PDF",
                color = MaterialTheme.kidBoxColors.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                placeholder = { Text("Inserisci la password") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (showPassword) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    autoCorrect = false,
                ),
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Nascondi password" else "Mostra password",
                            tint = MaterialTheme.kidBoxColors.subtitle,
                        )
                    }
                },
            )
            Text(
                text = "Verrà creata una copia non protetta nella stessa cartella; l'originale resta invariato.",
                color = MaterialTheme.kidBoxColors.subtitle,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Nome del nuovo PDF ───────────────────────────────────────
            Text(
                text = "Nome del nuovo PDF",
                color = MaterialTheme.kidBoxColors.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = nameDraft,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                placeholder = { Text("Documento sbloccato") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun MergePdfRow(
    index: Int,
    document: KBDocumentEntity,
    totalCount: Int,
    dragThresholdPx: Float,
    onMove: (Int, Int) -> Unit,
) {
    var dragAccum by remember(document.id) { mutableStateOf(0f) }
    var dragOffsetY by remember(document.id) { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFEFA14A),
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title.ifBlank { document.fileName },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatFileSize(document.fileSize),
                color = MaterialTheme.kidBoxColors.subtitle,
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFFE95858),
            modifier = Modifier.size(22.dp),
        )
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Trascina per riordinare",
            tint = Color(0xFFB0B0B0),
            modifier = Modifier
                .padding(start = 8.dp)
                .pointerInput(index, totalCount) {
                    var workingIndex = index
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragAccum = 0f
                            dragOffsetY = 0f
                            workingIndex = index
                        },
                        onDragEnd = {
                            dragAccum = 0f
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            dragAccum = 0f
                            dragOffsetY = 0f
                        },
                    ) { change, dragAmount ->
                        dragOffsetY += dragAmount.y
                        dragAccum += dragAmount.y
                        if (dragAccum > dragThresholdPx && workingIndex < totalCount - 1) {
                            val next = workingIndex + 1
                            onMove(workingIndex, next)
                            workingIndex = next
                            dragAccum = 0f
                            dragOffsetY = 0f
                        } else if (dragAccum < -dragThresholdPx && workingIndex > 0) {
                            val prev = workingIndex - 1
                            onMove(workingIndex, prev)
                            workingIndex = prev
                            dragAccum = 0f
                            dragOffsetY = 0f
                        }
                    }
                },
        )
    }
}

private suspend fun openDocument(
    context: android.content.Context,
    viewModel: DocumentsViewModel,
    document: KBDocumentEntity,
) {
    // Il valore del documento è che sia a portata di click per la famiglia:
    // questa apertura è la prova, e il server non la vede (nessuna scrittura).
    KBAnalytics.logRetrieval(
        feature = KBAnalyticsFeature.DOCUMENTS,
        uploaderUid = document.createdBy,
        createdAtEpochMillis = document.createdAtEpochMillis,
        entryPoint = KBAnalyticsOrigin.consume(),
    )
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    if (document.createdBy.isNotBlank() && document.createdBy != currentUid) {
        AppAnalytics.contentSharedRead(context, "documents")
    }
    try {
        KBLog.ui.debug("preparePreviewFile start docId=${document.id}", TAG_DOC_OPEN)
        val file = withTimeout(20_000L) { viewModel.preparePreviewFile(document) }
        KBLog.ui.debug("preparePreviewFile ok docId=${document.id} path=${file.absolutePath} size=${file.length()}", TAG_DOC_OPEN)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        KBLog.ui.debug("fileProvider uri generated docId=${document.id} uri=$uri", TAG_DOC_OPEN)
        val mime = document.mimeType.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        KBLog.ui.info("startActivity viewer docId=${document.id} mime=$mime", TAG_DOC_OPEN)
        context.startActivity(intent)
        KBLog.ui.info("startActivity success docId=${document.id}", TAG_DOC_OPEN)
    } catch (_: ActivityNotFoundException) {
        KBLog.ui.error("no app found to open docId=${document.id} mime=${document.mimeType}", TAG_DOC_OPEN)
        Toast.makeText(context, "Nessuna app disponibile per aprire questo file", Toast.LENGTH_LONG).show()
    } catch (_: TimeoutCancellationException) {
        KBLog.ui.error("openDocument timeout docId=${document.id}", TAG_DOC_OPEN)
        Toast.makeText(context, "Timeout apertura documento", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        KBLog.ui.error("openDocument failed docId=${document.id}", TAG_DOC_OPEN, e)
        Toast.makeText(
            context,
            "Impossibile aprire il documento: ${e.localizedMessage ?: "errore sconosciuto"}",
            Toast.LENGTH_LONG,
        ).show()
    } catch (t: Throwable) {
        KBLog.ui.error("openDocument fatal docId=${document.id}", TAG_DOC_OPEN, t)
        Toast.makeText(
            context,
            "Errore critico apertura documento",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private suspend fun shareDocuments(
    context: android.content.Context,
    viewModel: DocumentsViewModel,
    docs: List<KBDocumentEntity>,
) {
    val uris = mutableListOf<Uri>()
    docs.forEach { doc ->
        runCatching {
            val file = viewModel.preparePreviewFile(doc)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            uris.add(uri)
        }
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, "Nessun file condivisibile", Toast.LENGTH_SHORT).show()
        return
    }
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Condividi documenti")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun iconForMime(mime: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    mime.contains("pdf") -> Icons.Default.PictureAsPdf
    mime.startsWith("image/") -> Icons.Default.Image
    else -> Icons.Default.Description
}

/**
 * Nome reale del file scelto dall'utente.
 *
 * L'unica fonte attendibile è `OpenableColumns.DISPLAY_NAME` del provider:
 * è il nome che l'utente vede nel selettore. Ricavarlo dall'URI non funziona
 * con i content URI del Storage Access Framework, dove l'ultimo segmento è un
 * id opaco del documento (`msf:1000000123`, `primary%3ADownload%2F…`): il file
 * finiva così in archivio con un nome tecnico invece del suo.
 *
 * [guessFileName] resta solo come ripiego per i provider che non espongono la
 * colonna. Stesso approccio già usato da `PhotoVideoRepository` e
 * `WalletRepository`.
 */
private fun resolveFileName(context: Context, uri: Uri, mime: String): String {
    val fromProvider = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    return fromProvider?.let { ensureExtension(it, mime) }
        ?: guessFileName(uri.toString(), mime)
}

/** Estensione attesa per un mime type, usata solo quando il nome ne è privo. */
private fun extensionFor(mime: String): String = when {
    mime.contains("pdf") -> "pdf"
    mime.contains("png") -> "png"
    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
    else -> "bin"
}

/**
 * Alcuni provider restituiscono il nome senza estensione ("Scansione 3").
 * Senza, il file viene salvato e riaperto come binario generico.
 */
private fun ensureExtension(name: String, mime: String): String =
    if (name.substringAfterLast('.', "").isNotEmpty()) name else "$name.${extensionFor(mime)}"

private fun guessFileName(uriText: String, mime: String): String {
    val base = uriText.substringAfterLast('/').substringBefore('?').ifBlank { "document_${System.currentTimeMillis()}" }
    return ensureExtension(base, mime)
}

private fun sortFolders(
    folders: List<KBDocumentCategoryEntity>,
    sort: DocumentsSort,
    ascending: Boolean,
): List<KBDocumentCategoryEntity> {
    val sorted = when (sort) {
        DocumentsSort.NAME -> folders.sortedBy { it.title.lowercase() }
        DocumentsSort.TYPE -> folders.sortedBy { "folder" }
        DocumentsSort.DATE -> folders.sortedBy { it.updatedAtEpochMillis }
        DocumentsSort.SIZE -> folders.sortedBy { 0L }
    }
    return if (ascending) sorted else sorted.reversed()
}

private fun sortDocuments(
    documents: List<KBDocumentEntity>,
    sort: DocumentsSort,
    ascending: Boolean,
): List<KBDocumentEntity> {
    val sorted = when (sort) {
        DocumentsSort.NAME -> documents.sortedBy { it.title.lowercase() }
        DocumentsSort.TYPE -> documents.sortedBy { it.mimeType.lowercase() }
        DocumentsSort.DATE -> documents.sortedBy { it.updatedAtEpochMillis }
        DocumentsSort.SIZE -> documents.sortedBy { it.fileSize }
    }
    return if (ascending) sorted else sorted.reversed()
}

private fun formatRelativeTime(epochMillis: Long): String {
    val delta = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        minutes < 1 -> "Modificata ora"
        minutes < 60 -> "Modificata ${minutes} min fa"
        minutes < 60 * 24 -> "Modificata ${minutes / 60} ore fa"
        minutes < 60 * 24 * 7 -> "Modificata ${minutes / (60 * 24)} giorni fa"
        else -> "Modificata ${minutes / (60 * 24 * 7)} settimane fa"
    }
}

/**
 * Rimuove la password protezione da un singolo PDF e restituisce i byte
 * della copia non protetta.
 *
 * Usa PDFBox (già in dipendenza per `WalletPdfParser`) perché `PdfRenderer`
 * di Android non supporta i PDF cifrati. Se la password è errata
 * `PDDocument.load(...)` lancia `InvalidPasswordException`: viene rilanciata
 * con un messaggio italiano user-friendly.
 *
 * - Se il PDF non è cifrato il metodo restituisce comunque una copia
 *   normalizzata: in questo modo eventuali permessi (no-print, no-copy)
 *   posti dall'owner password vengono rimossi.
 */
private suspend fun unlockPdfFile(
    context: android.content.Context,
    file: File,
    password: String,
): ByteArray = withContext(Dispatchers.IO) {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
    val document = try {
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(file, password)
    } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        throw IllegalArgumentException(context.getString(R.string.documents_wrong_password))
    }
    try {
        if (document.isEncrypted) {
            // Toglie sia user-password che owner-password (e i permessi associati).
            document.setAllSecurityToBeRemoved(true)
        }
        val out = ByteArrayOutputStream()
        try {
            document.save(out)
            out.toByteArray()
        } finally {
            out.close()
        }
    } finally {
        document.close()
    }
}

private fun buildUnlockedPdfFileName(nameDraft: String, source: KBDocumentEntity): String {
    val base = nameDraft.trim().ifBlank {
        val title = source.title.trim().ifBlank { source.fileName.removeSuffix(".pdf") }
        "$title (sbloccato)"
    }
    return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
}

private fun buildUnlockedPdfDefaultName(source: KBDocumentEntity): String {
    val base = source.title.trim().ifBlank { source.fileName.removeSuffix(".pdf") }
    return "$base (sbloccato)"
}

private suspend fun mergePdfFiles(files: List<File>): ByteArray = withContext(Dispatchers.IO) {
    if (files.size < 2) throw IllegalArgumentException("At least 2 PDF files required")
    val outputDocument = PdfDocument()
    var outputPageNumber = 1

    files.forEach { inputFile ->
        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try {
                    val targetWidth = if (page.width > 1440) 1440 else page.width
                    val targetHeight = if (page.width > 0) (targetWidth.toFloat() / page.width.toFloat() * page.height.toFloat()).toInt().coerceAtLeast(1) else page.height
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, outputPageNumber++).create()
                    val outputPage = outputDocument.startPage(pageInfo)
                    val canvas: Canvas = outputPage.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    outputDocument.finishPage(outputPage)
                    bitmap.recycle()
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    val out = ByteArrayOutputStream()
    try {
        outputDocument.writeTo(out)
        out.toByteArray()
    } finally {
        outputDocument.close()
        out.close()
    }
}

private fun buildMergedPdfTitle(selected: List<KBDocumentEntity>): String {
    val first = selected.firstOrNull()?.title?.trim().orEmpty().ifBlank { "documento" }
    return "$first (uniti)"
}

private fun buildMergedPdfFileName(nameDraft: String, selected: List<KBDocumentEntity>): String {
    val base = nameDraft.trim().ifBlank { buildMergedPdfTitle(selected) }
    return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
