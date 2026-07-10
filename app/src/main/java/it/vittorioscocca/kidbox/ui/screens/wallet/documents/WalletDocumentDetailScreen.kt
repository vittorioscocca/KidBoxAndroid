@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import it.vittorioscocca.kidbox.ui.screens.documents.DocumentsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WalletDocumentDetailScreen(
    familyId: String,
    documentId: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: WalletDocumentsViewModel = hiltViewModel(),
    documentsViewModel: DocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.document.id == documentId }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var viewerPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoadingViewer by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(familyId) { if (state.familyId.isBlank()) viewModel.bind(familyId) }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    if (showDeleteDialog && item != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina documento") },
            text = { Text("L'operazione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteSingle(item.document)
                    onBack()
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annulla") }
            },
        )
    }

    if (showEditSheet && item != null) {
        EditWalletDocumentSheet(
            document = item.document,
            metadata = item.metadata,
            owners = state.owners,
            viewModel = viewModel,
            onDismiss = { showEditSheet = false },
        )
    }

    if (showImageViewer) {
        val viewerKind = item?.metadata?.kind ?: DocumentKind.ALTRO
        FullscreenPageViewer(
            pages = viewerPages,
            isLoading = isLoadingViewer,
            kind = viewerKind,
            onDismiss = { showImageViewer = false },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Documento", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text("Documento non trovato")
            }
            return@Scaffold
        }

        val metadata = item.metadata ?: WalletDocumentMetadata(kind = DocumentKind.ALTRO)
        val kind = metadata.kind

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            WalletDocumentCard(
                item = item,
                isSelectionMode = false,
                isSelected = false,
                height = 170.dp,
                onClick = {},
            )

            InfoSection(metadata = metadata, ownerName = item.ownerName)

            if (kind == DocumentKind.PATENTE && metadata.patenteCategories.isNotEmpty()) {
                PatenteCategoriesSection(metadata.patenteCategories)
            }

            // Barcode Code39 solo per Tessera Sanitaria: escluso CIE (solo testo).
            if (kind != DocumentKind.CIE && !metadata.codiceFiscale.isNullOrBlank()) {
                BarcodeSection(codiceFiscale = metadata.codiceFiscale, clipboard = clipboard)
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoadingViewer = true
                        showImageViewer = true
                        val pages = withContext(Dispatchers.IO) {
                            runCatching { documentsViewModel.preparePreviewFile(item.document) }
                                .mapCatching { renderPreviewPages(it) }
                                .getOrDefault(emptyList())
                        }
                        viewerPages = pages
                        isLoadingViewer = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Visualizza documento (fronte/retro)")
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching { documentsViewModel.preparePreviewFile(item.document) }.getOrNull()
                        }
                        if (file == null) {
                            snackbarHostState.showSnackbar("Impossibile aprire il file.")
                            return@launch
                        }
                        openOriginalFile(context, file, item.document.mimeType)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Apri file originale")
            }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Elimina documento")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoSection(metadata: WalletDocumentMetadata, ownerName: String) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Informazioni", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(icon = Icons.Filled.Person, label = "Titolare", value = ownerName)
            metadata.holderName?.takeIf { it.isNotBlank() }?.let {
                DetailRow(icon = Icons.Filled.Person, label = "Nome e cognome", value = it)
            }
            metadata.birthInfo?.takeIf { it.isNotBlank() }?.let {
                DetailRow(icon = Icons.Filled.CalendarToday, label = "Nascita", value = it)
            }
            metadata.documentNumber?.takeIf { it.isNotBlank() }?.let {
                DetailRow(icon = Icons.Filled.CalendarToday, label = "Numero documento", value = it, monospace = true)
            }
            metadata.codiceFiscale?.takeIf { it.isNotBlank() }?.let {
                DetailRow(icon = Icons.Filled.CalendarToday, label = "Codice Fiscale", value = it, monospace = true)
            }
            if (metadata.kind != DocumentKind.PATENTE) {
                metadata.issueDate?.let { DetailRow(icon = Icons.Filled.CalendarToday, label = "Rilascio", value = it.format(dateFmt)) }
                metadata.expiryDate?.let { DetailRow(icon = Icons.Filled.CalendarToday, label = "Scadenza", value = it.format(dateFmt)) }
            }
        }
    }
}

@Composable
private fun PatenteCategoriesSection(categories: List<it.vittorioscocca.kidbox.domain.model.PatenteCategory>) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Categorie patente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            categories.forEachIndexed { index, cat ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) {
                        cat.issueDate?.let { Text("Rilascio: ${it.format(dateFmt)}", style = MaterialTheme.typography.bodySmall) }
                        cat.expiryDate?.let { Text("Scadenza: ${it.format(dateFmt)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (index != categories.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (monospace) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun BarcodeSection(codiceFiscale: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    var showFullscreen by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Codice a barre (Codice Fiscale)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            val bitmap = remember(codiceFiscale) { generateCode39Bitmap(codiceFiscale) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Barcode Code 39",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .background(Color.White)
                        .clickable { showFullscreen = true },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(codiceFiscale, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                TextButton(onClick = { clipboard.setText(AnnotatedString(codiceFiscale)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Copia")
                }
            }
        }
    }

    if (showFullscreen) {
        ComposeDialog(onDismissRequest = { showFullscreen = false }) {
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = remember(codiceFiscale) { generateCode39Bitmap(codiceFiscale) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Barcode Code 39",
                            modifier = Modifier.fillMaxWidth().aspectRatio(2.2f),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(codiceFiscale, color = Color.Black, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showFullscreen = false }) { Text("Chiudi") }
                }
            }
        }
    }
}

/**
 * Vista a schermo intero fronte/retro: immagini vere in un pager sfogliabile,
 * sfondo tenue nel colore del tipo documento, etichetta pagina ("Fronte" /
 * "Retro" / "Pagina N") e indicatori pagina — mirror di
 * `WalletDocumentImagesFullscreenView` (iOS).
 */
@Composable
private fun FullscreenPageViewer(
    pages: List<Bitmap>,
    isLoading: Boolean,
    kind: DocumentKind,
    onDismiss: () -> Unit,
) {
    val tint = Color(kind.gradientStartHex)
    ComposeDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .background(
                    Brush.verticalGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.32f))),
                ),
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                pages.isEmpty() -> Text(
                    "Immagine non disponibile.",
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size })
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { page ->
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    pageLabel(page),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black.copy(alpha = 0.6f),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Image(
                                    bitmap = pages[page].asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 20.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .shadow(10.dp, RoundedCornerShape(16.dp)),
                                )
                            }
                        }
                        if (pages.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(pages.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                Color.Black.copy(alpha = if (index == pagerState.currentPage) 0.55f else 0.2f),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Chiudi",
                    tint = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun pageLabel(index: Int): String = when (index) {
    0 -> "Fronte"
    1 -> "Retro"
    else -> "Pagina ${index + 1}"
}

private fun generateCode39Bitmap(text: String): Bitmap? = runCatching {
    val width = 900
    val height = 220
    val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.CODE_39, width, height)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bmp
}.getOrNull()

private fun renderPreviewPages(file: File): List<Bitmap> {
    if (file.extension.lowercase() != "pdf") {
        return listOfNotNull(android.graphics.BitmapFactory.decodeFile(file.absolutePath))
    }
    val bitmaps = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps += bmp
                }
            }
        }
    }
    return bitmaps
}

private fun openOriginalFile(context: android.content.Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Nessuna app per aprire il file", Toast.LENGTH_SHORT).show() }
}
