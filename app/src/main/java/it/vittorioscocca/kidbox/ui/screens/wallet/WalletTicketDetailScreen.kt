@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletTicketDetailScreen(
    familyId: String,
    ticketId: String,
    onBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ticket = state.tickets.firstOrNull { it.id == ticketId }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(familyId) {
        if (state.familyId.isBlank()) viewModel.bind(familyId)
    }

    LaunchedEffect(state.pdfBytesEvent) {
        val bytes = state.pdfBytesEvent ?: return@LaunchedEffect
        viewModel.consumePdfBytes()
        val tmpFile = File(context.cacheDir, "wallet_tmp_$ticketId.pdf")
        tmpFile.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tmpFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "Nessuna app per aprire PDF", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina biglietto") },
            text = { Text("L'operazione non può essere annullata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTicket(ticketId)
                        onBack()
                    },
                ) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annulla") }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Dettaglio biglietto", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (ticket == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Biglietto non trovato")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header card
            WalletTicketCard(
                ticket = ticket,
                modifier = Modifier.height(210.dp),
            )

            // Barcode section
            if (!ticket.barcodeText.isNullOrBlank()) {
                BarcodeSection(ticket)
            }

            // Details section
            val hasDetails = ticket.eventDateEpochMillis != null ||
                !ticket.location.isNullOrBlank() ||
                !ticket.bookingCode.isNullOrBlank() ||
                !ticket.notes.isNullOrBlank()

            if (hasDetails) {
                DetailsSection(ticket)
            }

            // Actions
            Button(
                onClick = { viewModel.openPdf(ticketId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isImporting,
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Apri PDF")
                }
            }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Elimina biglietto")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BarcodeSection(ticket: KBWalletTicketEntity) {
    val clipboardManager = LocalClipboardManager.current
    val barcodeText = ticket.barcodeText ?: return
    val format = ticket.barcodeFormat ?: "QR_CODE"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Codice di accesso",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            val bitmap = remember(barcodeText, format) {
                generateBarcodeBitmap(barcodeText, format)
            }

            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Barcode",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (format == "QR_CODE" || format == "AZTEC") 1f else 3f)
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
                    format.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(barcodeText)) },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Copia", fontSize = 13.sp)
                }
            }
            Text(
                barcodeText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DetailsSection(ticket: KBWalletTicketEntity) {
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Dettagli",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            ticket.eventDateEpochMillis?.let { ms ->
                DetailRow(
                    icon = { Icon(Icons.Filled.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    label = "Quando",
                    value = dateFmt.format(Date(ms)),
                )
            }
            ticket.location?.takeIf { it.isNotBlank() }?.let {
                DetailRow(
                    icon = { Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    label = "Dove",
                    value = it,
                )
            }
            ticket.bookingCode?.takeIf { it.isNotBlank() }?.let {
                DetailRow(
                    icon = { Icon(Icons.Filled.Tag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    label = "Codice prenotazione",
                    value = it,
                    monospace = true,
                )
            }
            ticket.notes?.takeIf { it.isNotBlank() }?.let {
                DetailRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.Note, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    label = "Note",
                    value = it,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 2.dp)) { icon() }
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (monospace) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun generateBarcodeBitmap(text: String, format: String): Bitmap? {
    return runCatching {
        val barcodeFormat = when (format) {
            "QR_CODE" -> BarcodeFormat.QR_CODE
            "AZTEC" -> BarcodeFormat.AZTEC
            "PDF_417" -> BarcodeFormat.PDF_417
            "CODE_128" -> BarcodeFormat.CODE_128
            "CODE_39" -> BarcodeFormat.CODE_39
            "EAN_13" -> BarcodeFormat.EAN_13
            "DATA_MATRIX" -> BarcodeFormat.DATA_MATRIX
            else -> BarcodeFormat.QR_CODE
        }
        val isMatrix = barcodeFormat == BarcodeFormat.QR_CODE ||
            barcodeFormat == BarcodeFormat.AZTEC ||
            barcodeFormat == BarcodeFormat.DATA_MATRIX

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
