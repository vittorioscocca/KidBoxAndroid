@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.wallet.WalletParsedData
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddWalletTicketSheet(
    familyId: String,
    viewModel: WalletViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val parsedData by viewModel.parsedData.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pdfUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var pdfFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedKind by rememberSaveable { mutableStateOf(WalletTicketKind.OTHER) }
    var hasDate by rememberSaveable { mutableStateOf(false) }
    var eventDateMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var location by rememberSaveable { mutableStateOf("") }
    var bookingCode by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.ITALIAN) }

    LaunchedEffect(parsedData) {
        val p = parsedData ?: return@LaunchedEffect
        if (title.isBlank()) title = p.suggestedTitle
        selectedKind = p.kind
        if (p.eventDate != null) {
            hasDate = true
            eventDateMs = p.eventDate
        }
        if (location.isBlank() && !p.location.isNullOrBlank()) location = p.location
        if (bookingCode.isBlank() && !p.bookingCode.isNullOrBlank()) bookingCode = p.bookingCode
        if (notes.isBlank() && !p.notes.isNullOrBlank()) notes = p.notes
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            pdfUri = uri
            pdfFileName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            viewModel.parsePdf(context, uri, pdfFileName)
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = eventDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { eventDateMs = it }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Avanti") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = eventDateMs }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Seleziona ora") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = eventDateMs
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    eventDateMs = newCal.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Annulla") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
                Text(
                    "Nuovo biglietto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = {
                        val uri = pdfUri ?: return@TextButton
                        val p = parsedData ?: WalletParsedData(
                            suggestedTitle = title,
                            kind = selectedKind,
                            emitter = null,
                            eventDate = if (hasDate) eventDateMs else null,
                            location = location.ifBlank { null },
                            bookingCode = bookingCode.ifBlank { null },
                            barcodeText = null,
                            barcodeFormat = null,
                            notes = notes.ifBlank { null },
                            thumbnailBase64 = null,
                        )
                        viewModel.addTicketFromForm(
                            familyId = familyId,
                            pdfUri = uri,
                            title = title,
                            parsed = p.copy(
                                kind = selectedKind,
                                eventDate = if (hasDate) eventDateMs else null,
                                location = location.ifBlank { null },
                                bookingCode = bookingCode.ifBlank { null },
                                notes = notes.ifBlank { null },
                            ),
                            context = context,
                            onSuccess = onDismiss,
                        )
                    },
                    enabled = pdfUri != null && title.isNotBlank() && !state.isImporting,
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Salva", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider()

            // PDF section
            Text(
                "PDF",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { pdfPicker.launch("application/pdf") }
                    .padding(16.dp),
            ) {
                if (pdfUri != null) {
                    Column {
                        // Thumbnail preview
                        parsedData?.thumbnailBase64?.let { b64 ->
                            val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
                            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        Text(
                            pdfFileName ?: "PDF selezionato",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Tocca per cambiare",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Nessun PDF selezionato",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "  Scegli PDF",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HorizontalDivider()

            // Ticket data
            Text(
                "Dati biglietto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titolo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Kind selector
            Text(
                "Tipo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WalletTicketKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = { selectedKind = kind },
                        label = { Text(kind.displayName) },
                    )
                }
            }

            // Date toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Data evento", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = hasDate,
                    onCheckedChange = { hasDate = it },
                )
            }

            if (hasDate) {
                OutlinedTextField(
                    value = dateFmt.format(Date(eventDateMs)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data e ora") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                )
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Luogo (opzionale)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = bookingCode,
                onValueChange = { bookingCode = it },
                label = { Text("Codice prenotazione (opzionale)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note (opzionale)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
