@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.wallet.WalletParsedData
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerFullscreenDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.ui.util.visibilityChipLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddWalletTicketSheet(
    familyId: String,
    viewModel: WalletViewModel,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val parsedData by viewModel.parsedData.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pdfUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var pdfFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedKind by rememberSaveable { mutableStateOf(WalletTicketKind.OTHER) }
    var hasDate by rememberSaveable { mutableStateOf(false) }
    var eventDateMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var hasArrivalDate by rememberSaveable { mutableStateOf(false) }
    var arrivalDateMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var location by rememberSaveable { mutableStateOf("") }
    var arrivalLocation by rememberSaveable { mutableStateOf("") }
    var holderName by rememberSaveable { mutableStateOf("") }
    var bookingCode by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showArrivalDatePicker by remember { mutableStateOf(false) }
    var showArrivalTimePicker by remember { mutableStateOf(false) }

    var isAiReading by remember { mutableStateOf(false) }
    var showAiCostConfirm by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    var showVisibilityPicker by remember { mutableStateOf(false) }
    var showKidBoxDocumentPicker by remember { mutableStateOf(false) }
    var draftVisibilityScope by remember { mutableStateOf(KBVisibilityScope.ONLY_CREATOR) }
    var draftVisibilityMemberIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val dateFmt = remember { SimpleDateFormat("EEE d MMM yyyy, HH:mm", KBLocale.current()) }

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

    fun applyAiExtraction(ext: it.vittorioscocca.kidbox.data.wallet.WalletTicketExtraction) {
        ext.holderName?.let { holderName = it }
        ext.bookingCode?.let { bookingCode = it }
        ext.kind?.let { selectedKind = it }
        ext.departureLocation?.let { location = it }
        ext.departureDateTimeEpochMillis?.let { hasDate = true; eventDateMs = it }
        ext.arrivalLocation?.let { arrivalLocation = it }
        ext.arrivalDateTimeEpochMillis?.let { hasArrivalDate = true; arrivalDateMs = it }
    }

    fun runAiExtraction() {
        scope.launch {
            isAiReading = true
            aiError = null
            val bitmap = parsedData?.thumbnailBase64?.let { b64 ->
                runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            viewModel.runAiTicketExtraction(parsedData?.rawText, bitmap, familyId)
                .onSuccess { applyAiExtraction(it) }
                .onFailure { aiError = it.localizedMessage ?: context.getString(R.string.wallet_ai_read_failed) }
            isAiReading = false
        }
    }

    if (showAiCostConfirm) {
        val usedImageFallback = parsedData?.rawText.isNullOrBlank()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAiCostConfirm = false },
            title = { Text(stringResource(R.string.wallet_ai_read_title)) },
            text = {
                Text(
                    stringResource(R.string.wallet_ai_read_cost_message, viewModel.estimatedAiTicketMessageCost(usedImageFallback)),
                )
            },
            confirmButton = {
                TextButton(onClick = { showAiCostConfirm = false; runAiExtraction() }) {
                    Text(context.getString(R.string.wallet_read_ai_msg_count, viewModel.estimatedAiTicketMessageCost(usedImageFallback)))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiCostConfirm = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        )
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
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
                }) { Text(stringResource(R.string.wallet_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showArrivalDatePicker) {
        val arrivalDatePickerState = rememberDatePickerState(initialSelectedDateMillis = arrivalDateMs)
        DatePickerDialog(
            onDismissRequest = { showArrivalDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    arrivalDatePickerState.selectedDateMillis?.let { arrivalDateMs = it }
                    showArrivalDatePicker = false
                    showArrivalTimePicker = true
                }) { Text(stringResource(R.string.wallet_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showArrivalDatePicker = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        ) {
            DatePicker(state = arrivalDatePickerState)
        }
    }

    if (showArrivalTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = arrivalDateMs }
        val arrivalTimePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showArrivalTimePicker = false },
            title = { Text(stringResource(R.string.wallet_select_arrival_time_title)) },
            text = { TimePicker(state = arrivalTimePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = arrivalDateMs
                        set(Calendar.HOUR_OF_DAY, arrivalTimePickerState.hour)
                        set(Calendar.MINUTE, arrivalTimePickerState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    arrivalDateMs = newCal.timeInMillis
                    showArrivalTimePicker = false
                }) { Text(stringResource(R.string.wallet_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showArrivalTimePicker = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        )
    }

    if (showVisibilityPicker) {
        VisibilityPickerFullscreenDialog(
            currentUid = FirebaseAuth.getInstance().currentUser?.uid,
            scopeSectionTitle = stringResource(R.string.wallet_visibility_who_can_see_ticket),
            membersExcludingSelf = state.visibilityMembers,
            initialScope = draftVisibilityScope,
            initialMemberIds = draftVisibilityMemberIds,
            onDismiss = { showVisibilityPicker = false },
            onConfirmed = { scope, ids ->
                draftVisibilityScope = scope
                draftVisibilityMemberIds = ids
            },
        )
    }

    if (showKidBoxDocumentPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            pdfOnly = true,
            onDismiss = { showKidBoxDocumentPicker = false },
            onPickedUri = { uri ->
                showKidBoxDocumentPicker = false
                pdfUri = uri
                pdfFileName = runCatching {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull() ?: "documento.pdf"
                viewModel.parsePdf(context, uri, pdfFileName)
            },
        )
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = eventDateMs }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.wallet_select_time_title)) },
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
                }) { Text(stringResource(R.string.wallet_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.wallet_cancel)) }
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.wallet_cancel)) }
                Text(
                    stringResource(R.string.wallet_new_ticket_title),
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
                                eventEndDate = if (hasArrivalDate) arrivalDateMs else null,
                                location = location.ifBlank { null },
                                arrivalLocation = arrivalLocation.ifBlank { null },
                                holderName = holderName.ifBlank { null },
                                bookingCode = bookingCode.ifBlank { null },
                                notes = notes.ifBlank { null },
                            ),
                            visibilityScope = draftVisibilityScope,
                            visibilityMemberIds = draftVisibilityMemberIds,
                            context = context,
                            onSuccess = onDismiss,
                        )
                    },
                    enabled = pdfUri != null && title.isNotBlank() && !state.isImporting,
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.wallet_save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.wallet_visibility_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = { showVisibilityPicker = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        visibilityChipLabel(draftVisibilityScope),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // PDF section
            Text(
                stringResource(R.string.wallet_pdf_section_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wallet_device_button), maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showKidBoxDocumentPicker = true },
                ) {
                    Text(stringResource(R.string.wallet_kidbox_button), maxLines = 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                if (pdfUri != null) {
                    Column {
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
                            pdfFileName ?: stringResource(R.string.wallet_pdf_selected_fallback),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.wallet_pdf_change_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.wallet_pdf_choose_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (pdfUri != null) {
                if (state.currentPlan == KBPlan.MAX) {
                    OutlinedButton(
                        onClick = { showAiCostConfirm = true },
                        enabled = !isAiReading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isAiReading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wallet_read_with_ai_button))
                        }
                    }
                } else {
                    OutlinedButton(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wallet_read_ai_plan_max))
                    }
                }
                Text(
                    stringResource(R.string.wallet_ticket_ai_assisted_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                aiError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            // Ticket data
            Text(
                stringResource(R.string.wallet_ticket_data_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.wallet_label_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Kind selector
            Text(
                stringResource(R.string.wallet_label_type),
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

            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text(stringResource(R.string.wallet_holder_name_optional_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Departure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.wallet_departure_time_label), style = MaterialTheme.typography.bodyMedium)
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
                    label = { Text(stringResource(R.string.wallet_departure_datetime_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                )
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text(stringResource(R.string.wallet_departure_location_optional_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Arrival
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.wallet_arrival_time_label), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = hasArrivalDate,
                    onCheckedChange = { hasArrivalDate = it },
                )
            }

            if (hasArrivalDate) {
                OutlinedTextField(
                    value = dateFmt.format(Date(arrivalDateMs)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.wallet_arrival_datetime_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showArrivalDatePicker = true },
                    enabled = false,
                )
            }

            OutlinedTextField(
                value = arrivalLocation,
                onValueChange = { arrivalLocation = it },
                label = { Text(stringResource(R.string.wallet_arrival_location_optional_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = bookingCode,
                onValueChange = { bookingCode = it },
                label = { Text(stringResource(R.string.wallet_ticket_code_optional_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.wallet_notes_optional_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
