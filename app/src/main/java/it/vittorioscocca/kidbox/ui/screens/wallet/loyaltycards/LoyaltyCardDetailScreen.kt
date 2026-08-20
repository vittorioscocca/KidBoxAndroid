@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import it.vittorioscocca.kidbox.data.repository.LoyaltyCardPhotoSide
import it.vittorioscocca.kidbox.ui.screens.wallet.documents.rememberWalletDocumentScannerLauncher
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsFeature
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin

/**
 * Dettaglio di una carta fedeltà: barcode grande (ZXing, stesso approccio già
 * usato per i biglietti in `WalletTicketDetailScreen`), nota, azioni
 * elimina/modifica. Mirror Compose di `LoyaltyCardDetailView` (iOS), senza il
 * tab "Negozi e offerte / DoveConviene" (fuori scope).
 */
@Composable
fun LoyaltyCardDetailScreen(
    familyId: String,
    cardId: String,
    onBack: () -> Unit,
    viewModel: LoyaltyCardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val card = state.cards.firstOrNull { it.id == cardId }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var fullScreenPhoto by remember { mutableStateOf<Bitmap?>(null) }

    fullScreenPhoto?.let { photo ->
        LoyaltyCardPhotoViewer(bitmap = photo, onDismiss = { fullScreenPhoto = null })
    }

    BackHandler { onBack() }

    LaunchedEffect(familyId) {
        if (state.familyId.isBlank()) viewModel.bind(familyId)
    }

    // Aprire il dettaglio è il recupero vero, stesso pattern di
    // WalletTicketDetailScreen: non produce scritture, quindi il server non lo vede.
    LaunchedEffect(card?.id) {
        val c = card ?: return@LaunchedEffect
        KBAnalytics.logRetrieval(
            feature = KBAnalyticsFeature.WALLET,
            uploaderUid = c.createdBy,
            createdAtEpochMillis = c.createdAtEpochMillis,
            entryPoint = KBAnalyticsOrigin.consume(),
        )
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (c.createdBy.isNotBlank() && c.createdBy != currentUid) {
            AppAnalytics.contentSharedRead(context, "loyalty_card")
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.wallet_loyalty_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wallet_loyalty_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteCard(cardId)
                        onBack()
                    },
                ) { Text(stringResource(R.string.wallet_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        )
    }

    if (showEditDialog && card != null) {
        EditLoyaltyCardDialog(
            card = card,
            onDismiss = { showEditDialog = false },
            onSave = { number, note ->
                showEditDialog = false
                viewModel.updateCard(cardId, number, note)
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wallet_loyalty_detail_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wallet_back))
                    }
                },
                actions = {
                    if (card != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.wallet_loyalty_edit_cd))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.wallet_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (card == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.wallet_loyalty_not_found))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            LoyaltyCardTile(card = card, height = 190.dp)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.wallet_loyalty_card_code_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val bitmap = remember(card.cardNumber, card.barcodeFormat) {
                        generateLoyaltyCardBarcode(card.cardNumber, card.barcodeFormat)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.wallet_loyalty_barcode_cd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(if (isMatrixFormat(card.barcodeFormat)) 1f else 3f)
                                .background(Color.White),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            card.barcodeFormat.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(card.cardNumber)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.wallet_copy))
                        }
                    }
                    Text(
                        card.cardNumber,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            LoyaltyCardPhotosSection(
                card = card,
                photos = state.photos,
                busySide = state.busyPhotoSide,
                onRequestLoad = { path -> viewModel.loadCardPhoto(path) },
                onCaptured = { side, bitmap -> viewModel.setCardPhoto(cardId, side, bitmap) },
                onRemove = { side -> viewModel.removeCardPhoto(cardId, side) },
                onOpenFullScreen = { bitmap -> fullScreenPhoto = bitmap },
            )

            if (!card.note.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.wallet_loyalty_note_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(card.note)
                    }
                }
            }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.wallet_loyalty_delete_card_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Sezione "Foto della tessera": due slot, FRONTE e RETRO. Slot vuoto = bottone
 * con icona fotocamera che apre lo scanner ML Kit (lo stesso dei documenti
 * d'identità, `rememberWalletDocumentScannerLauncher`); slot pieno = miniatura
 * tappabile che apre la foto a schermo intero, con sostituisci/elimina.
 *
 * Le foto viaggiano cifrate su Storage: qui arrivano già decifrate dal
 * ViewModel come bitmap in memoria.
 */
@Composable
private fun LoyaltyCardPhotosSection(
    card: KBLoyaltyCardEntity,
    photos: Map<String, Bitmap>,
    busySide: LoyaltyCardPhotoSide?,
    onRequestLoad: (String?) -> Unit,
    onCaptured: (LoyaltyCardPhotoSide, Bitmap) -> Unit,
    onRemove: (LoyaltyCardPhotoSide) -> Unit,
    onOpenFullScreen: (Bitmap) -> Unit,
) {
    // Un solo launcher per entrambi gli slot: il lato in corso di acquisizione
    // è tenuto qui, perché il launcher va creato in composizione, non al click.
    var pendingSide by remember { mutableStateOf<LoyaltyCardPhotoSide?>(null) }
    val launchScanner = rememberWalletDocumentScannerLauncher(
        pageLimit = 1,
        onResult = { result ->
            val side = pendingSide
            val page = result.pages.firstOrNull()
            if (side != null && page != null) onCaptured(side, page)
            pendingSide = null
        },
        onCancelledOrFailed = { pendingSide = null },
    )

    LaunchedEffect(card.frontPhotoStoragePath, card.backPhotoStoragePath) {
        onRequestLoad(card.frontPhotoStoragePath)
        onRequestLoad(card.backPhotoStoragePath)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.wallet_loyalty_photos_section_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LoyaltyCardPhotoSlot(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.wallet_loyalty_photo_front),
                    bitmap = card.frontPhotoStoragePath?.let { photos[it] },
                    hasPhoto = !card.frontPhotoStoragePath.isNullOrBlank(),
                    isBusy = busySide == LoyaltyCardPhotoSide.FRONT,
                    onCapture = {
                        pendingSide = LoyaltyCardPhotoSide.FRONT
                        launchScanner()
                    },
                    onRemove = { onRemove(LoyaltyCardPhotoSide.FRONT) },
                    onOpenFullScreen = onOpenFullScreen,
                )
                LoyaltyCardPhotoSlot(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.wallet_loyalty_photo_back),
                    bitmap = card.backPhotoStoragePath?.let { photos[it] },
                    hasPhoto = !card.backPhotoStoragePath.isNullOrBlank(),
                    isBusy = busySide == LoyaltyCardPhotoSide.BACK,
                    onCapture = {
                        pendingSide = LoyaltyCardPhotoSide.BACK
                        launchScanner()
                    },
                    onRemove = { onRemove(LoyaltyCardPhotoSide.BACK) },
                    onOpenFullScreen = onOpenFullScreen,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.wallet_loyalty_photos_encrypted_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoyaltyCardPhotoSlot(
    label: String,
    bitmap: Bitmap?,
    hasPhoto: Boolean,
    isBusy: Boolean,
    onCapture: () -> Unit,
    onRemove: () -> Unit,
    onOpenFullScreen: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f) // proporzioni di una tessera reale (ISO ID-1)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !isBusy) {
                    if (bitmap != null) onOpenFullScreen(bitmap) else if (!hasPhoto) onCapture()
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                isBusy -> CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.wallet_loyalty_photo_open_cd, label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Path presente ma bitmap non ancora pronto: download/decifratura in corso.
                hasPhoto -> CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                else -> Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.wallet_loyalty_photo_add_cd, label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasPhoto && !isBusy) {
            // Solo "Elimina": per sostituire una foto si elimina e si
            // riacquisisce, e lo slot vuoto torna a proporre l'acquisizione.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRemove, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                    Text(
                        stringResource(R.string.wallet_loyalty_photo_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Foto della tessera a schermo intero, su fondo nero: tap ovunque per chiudere. */
@Composable
private fun LoyaltyCardPhotoViewer(bitmap: Bitmap, onDismiss: () -> Unit) {
    // `usePlatformDefaultWidth = false` da solo non basta: la finestra del
    // dialog continua a rispettare le system bar, quindi il fondo nero si
    // ferma prima dei bordi dello schermo. `decorFitsSystemWindows = false`
    // la fa disegnare davvero edge-to-edge.
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
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.wallet_loyalty_photos_section_title),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wallet_close), tint = Color.White)
            }
        }
    }
}

@Composable
private fun EditLoyaltyCardDialog(
    card: it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity,
    onDismiss: () -> Unit,
    onSave: (cardNumber: String, note: String?) -> Unit,
) {
    var cardNumber by remember { mutableStateOf(card.cardNumber) }
    var note by remember { mutableStateOf(card.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.brandName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it },
                    label = { Text(stringResource(R.string.wallet_loyalty_card_number_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.wallet_loyalty_description_label)) },
                    minLines = 1,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(cardNumber.trim(), note.trim().ifBlank { null }) },
                enabled = cardNumber.trim().isNotEmpty(),
            ) { Text(stringResource(R.string.wallet_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.wallet_cancel)) }
        },
    )
}

private fun isMatrixFormat(format: String): Boolean = format in setOf("qr", "aztec", "data_matrix")

private fun zxingFormat(format: String): BarcodeFormat = when (format.lowercase()) {
    "qr" -> BarcodeFormat.QR_CODE
    "aztec" -> BarcodeFormat.AZTEC
    "pdf417" -> BarcodeFormat.PDF_417
    "code128" -> BarcodeFormat.CODE_128
    "code39" -> BarcodeFormat.CODE_39
    "ean13" -> BarcodeFormat.EAN_13
    "ean8" -> BarcodeFormat.EAN_8
    "upce" -> BarcodeFormat.UPC_E
    "itf14", "itf" -> BarcodeFormat.ITF
    "data_matrix" -> BarcodeFormat.DATA_MATRIX
    else -> BarcodeFormat.CODE_128
}

private fun generateLoyaltyCardBarcode(text: String, format: String): Bitmap? {
    if (text.isBlank()) return null
    return runCatching {
        val barcodeFormat = zxingFormat(format)
        val isMatrix = isMatrixFormat(format)
        val width = if (isMatrix) 600 else 900
        val height = if (isMatrix) 600 else 200
        val matrix: BitMatrix = MultiFormatWriter().encode(text, barcodeFormat, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    }.getOrNull()
}
