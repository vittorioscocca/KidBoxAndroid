package it.vittorioscocca.kidbox.ui.screens.vehicles

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.ui.screens.life.daysRemainingCalendarIt
import it.vittorioscocca.kidbox.ui.screens.life.formatItDateMedium
import it.vittorioscocca.kidbox.ui.screens.life.formatItDateTime
import it.vittorioscocca.kidbox.ui.screens.life.formatItTimeHm
import it.vittorioscocca.kidbox.ui.screens.life.homeDeadlineUrgencyColor
import it.vittorioscocca.kidbox.ui.screens.life.homeDeadlineUrgencyLabel
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePickerKeepingTime
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeTimePicker
import it.vittorioscocca.kidbox.ui.screens.life.vehicleEventTypeEmoji
import it.vittorioscocca.kidbox.ui.screens.life.vehicleEventTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.vehicleFuelLabelIt
import it.vittorioscocca.kidbox.ui.components.IosFormDivider
import it.vittorioscocca.kidbox.ui.components.IosGroupedCard
import it.vittorioscocca.kidbox.ui.components.IosPlainTextFieldRow
import it.vittorioscocca.kidbox.ui.components.KidBoxIosFormTopBar
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private enum class GarageAttachmentPickTarget {
    DetailVehicle,
    EditVehicle,
    EventDraft,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    onNavigateBack: () -> Unit,
    onSeeAllInterventions: () -> Unit = {},
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val events by viewModel.vehicleEvents.collectAsStateWithLifecycle()
    val vehicleAttachments by viewModel.vehicleAttachments.collectAsStateWithLifecycle()
    val eventDraftAttachments by viewModel.eventDraftAttachments.collectAsStateWithLifecycle()
    val attachmentUploading by viewModel.attachmentUploading.collectAsStateWithLifecycle()
    val openFileEvent by viewModel.openFileEvent.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()
    var showEditVehicle by remember { mutableStateOf(false) }
    var showAddEvent by remember { mutableStateOf(false) }
    var pendingEventDraftId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var pickTarget by remember { mutableStateOf<GarageAttachmentPickTarget?>(null) }
    var showKidBoxPicker by remember { mutableStateOf(false) }
    var kidBoxPickerTarget by remember { mutableStateOf<GarageAttachmentPickTarget?>(null) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val destructive = Color(0xFFE53935)

    val previewInterventions = remember(events) { events.take(4) }
    val showSeeAllInterventions = events.size > 4

    LaunchedEffect(attachmentError) {
        attachmentError?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeAttachmentError()
        }
    }
    LaunchedEffect(openFileEvent) {
        openFileEvent?.let { (mime, file) ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
            viewModel.consumeOpenFileEvent()
        }
    }

    val vehicleCamDir = remember { File(context.cacheDir, "garage-camera").apply { mkdirs() } }
    val garageCameraFile = remember { File(vehicleCamDir, "garage_attach_tmp.jpg") }
    val garageCameraUri = remember(garageCameraFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", garageCameraFile)
    }

    fun uploadUriForTarget(uri: Uri) {
        when (pickTarget) {
            GarageAttachmentPickTarget.DetailVehicle -> viewModel.uploadVehicleAttachment(uri)
            GarageAttachmentPickTarget.EditVehicle -> viewModel.uploadVehicleAttachment(uri)
            GarageAttachmentPickTarget.EventDraft -> viewModel.uploadEventDraftAttachment(uri)
            null -> Unit
        }
        pickTarget = null
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pickTarget != null) uploadUriForTarget(garageCameraUri)
        else pickTarget = null
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, context.getString(R.string.life_camera_denied), Toast.LENGTH_SHORT).show()
            pickTarget = null
            return@rememberLauncherForActivityResult
        }
        if (pickTarget != null) takePictureLauncher.launch(garageCameraUri)
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadUriForTarget(uri)
        else pickTarget = null
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) uploadUriForTarget(uri)
        else pickTarget = null
    }

    fun requestTakePhoto(target: GarageAttachmentPickTarget) {
        pickTarget = target
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> takePictureLauncher.launch(garageCameraUri)
            else -> cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestPickPhoto(target: GarageAttachmentPickTarget) {
        pickTarget = target
        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun requestPickFile(target: GarageAttachmentPickTarget) {
        pickTarget = target
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    fun requestKidBoxDocuments(target: GarageAttachmentPickTarget) {
        kidBoxPickerTarget = target
        showKidBoxPicker = true
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        vehicle?.name ?: stringResource(R.string.vehicles_garage),
                        color = kb.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    if (vehicle != null) {
                        IconButton(
                            onClick = {
                                pendingEventDraftId = UUID.randomUUID().toString()
                                viewModel.bindEventDraftAttachments(pendingEventDraftId)
                                showAddEvent = true
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vehicles_new_service), tint = orange)
                        }
                        IconButton(onClick = { showEditVehicle = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.vehicles_edit), tint = kb.title)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.vehicles_delete), tint = destructive)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.background,
                    titleContentColor = kb.title,
                    navigationIconContentColor = kb.title,
                    actionIconContentColor = kb.title,
                ),
            )
        },
    ) { padding ->
        val v = vehicle
        if (v == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = kb.card),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(v.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = kb.title)
                        listOfNotNull(v.brand, v.model)
                            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() }
                            ?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = kb.subtitle) }
                        v.year?.let { Text("Anno: $it", style = MaterialTheme.typography.bodyMedium, color = kb.title) }
                        Text(
                            "Carburante: ${vehicleFuelLabelIt(v.fuelType)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = kb.title,
                        )
                        v.color?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            Text("Colore: $it", style = MaterialTheme.typography.bodyMedium, color = kb.title)
                        }
                        v.licensePlate?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            Text("Targa: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = kb.title)
                        }
                        v.vin?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            Text("VIN: $it", style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                        }
                        v.currentKm?.let {
                            Text("Chilometri: $it km", style = MaterialTheme.typography.bodyMedium, color = kb.title)
                        }
                        v.notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.section_attachments),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.subtitle,
                )
            }
            item {
                HealthAttachmentsCard(
                    attachments = vehicleAttachments,
                    tintColor = orange,
                    isUploading = attachmentUploading,
                    onPickFile = { requestPickFile(GarageAttachmentPickTarget.DetailVehicle) },
                    onPickPhoto = { requestPickPhoto(GarageAttachmentPickTarget.DetailVehicle) },
                    onTakePhoto = { requestTakePhoto(GarageAttachmentPickTarget.DetailVehicle) },
                    onOpenAttachment = { viewModel.openAttachment(it) },
                    onDeleteAttachment = { viewModel.deleteAttachment(it) },
                    onPickFromKidBoxDocuments = { requestKidBoxDocuments(GarageAttachmentPickTarget.DetailVehicle) },
                )
            }
            item {
                Text(stringResource(R.string.section_deadlines), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = kb.subtitle)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VehicleDeadlineCard(stringResource(R.string.vehicles_insurance), v.insuranceExpiryDate)
                    VehicleDeadlineCard(stringResource(R.string.vehicles_inspection), v.revisionExpiryDate)
                    VehicleDeadlineCard(stringResource(R.string.vehicles_road_tax), v.taxExpiryDate)
                    VehicleDeadlineCard(stringResource(R.string.vehicles_next_service), v.nextServiceDate)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.vehicles_service_history),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                    )
                    if (showSeeAllInterventions) {
                        TextButton(onClick = onSeeAllInterventions) {
                            Text(stringResource(R.string.vehicles_see_all), color = orange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                if (events.isEmpty()) {
                    Text(
                        stringResource(R.string.vehicles_no_services),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kb.subtitle,
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column {
                            previewInterventions.forEachIndexed { index, ev ->
                                VehicleEventRowContent(ev)
                                if (index < previewInterventions.lastIndex) {
                                    HorizontalDivider(color = kb.divider, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddEvent && pendingEventDraftId != null) {
        AddVehicleEventDialog(
            eventDraftAttachments = eventDraftAttachments,
            attachmentUploading = attachmentUploading,
            onDismiss = {
                val id = pendingEventDraftId
                scope.launch {
                    id?.let { viewModel.deleteAllAttachmentsForDraftEvent(it) }
                }
                viewModel.bindEventDraftAttachments(null)
                pendingEventDraftId = null
                showAddEvent = false
            },
            onConfirm = { title, type, date, km, cost, garage, notes ->
                val draftId = pendingEventDraftId
                viewModel.addVehicleEvent(draftId, title, type, date, km, cost, garage, notes) { err -> toast = err }
                pendingEventDraftId = null
                viewModel.bindEventDraftAttachments(null)
                showAddEvent = false
            },
            onTakePhoto = { requestTakePhoto(GarageAttachmentPickTarget.EventDraft) },
            onPickPhoto = { requestPickPhoto(GarageAttachmentPickTarget.EventDraft) },
            onPickFile = { requestPickFile(GarageAttachmentPickTarget.EventDraft) },
            onPickKidBox = { requestKidBoxDocuments(GarageAttachmentPickTarget.EventDraft) },
            onOpenAttachment = { viewModel.openAttachment(it) },
            onDeleteAttachment = { viewModel.deleteAttachment(it) },
        )
    }

    if (showEditVehicle && vehicle != null) {
        EditVehicleDialog(
            initial = vehicle!!,
            vehicleAttachments = vehicleAttachments,
            attachmentUploading = attachmentUploading,
            onDismiss = { showEditVehicle = false },
            onConfirm = { updated ->
                viewModel.updateVehicle(updated) { err -> toast = err }
                showEditVehicle = false
            },
            onTakePhoto = { requestTakePhoto(GarageAttachmentPickTarget.EditVehicle) },
            onPickPhoto = { requestPickPhoto(GarageAttachmentPickTarget.EditVehicle) },
            onPickFile = { requestPickFile(GarageAttachmentPickTarget.EditVehicle) },
            onPickKidBox = { requestKidBoxDocuments(GarageAttachmentPickTarget.EditVehicle) },
            onOpenAttachment = { viewModel.openAttachment(it) },
            onDeleteAttachment = { viewModel.deleteAttachment(it) },
        )
    }

    val familyIdForPicker = vehicle?.familyId.orEmpty()
    if (showKidBoxPicker && familyIdForPicker.isNotBlank()) {
        KidBoxDocumentPickerSheet(
            familyId = familyIdForPicker,
            onDismiss = {
                showKidBoxPicker = false
                kidBoxPickerTarget = null
            },
            onPickedUri = { uri ->
                kidBoxPickerTarget?.let { t ->
                    pickTarget = t
                    uploadUriForTarget(uri)
                }
                kidBoxPickerTarget = null
                showKidBoxPicker = false
            },
        )
    }

    if (confirmDelete && vehicle != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vehicles_delete_q)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vehicle?.let {
                            viewModel.deleteVehicle(it) { err -> toast = err }
                            onNavigateBack()
                        }
                        confirmDelete = false
                    },
                ) { Text(stringResource(R.string.life_delete), color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.life_cancel)) } },
        )
    }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("OK") } },
            title = { Text(stringResource(R.string.life_error)) },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun VehicleDeadlineCard(label: String, millis: Long?) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                if (millis != null) {
                    val days = daysRemainingCalendarIt(millis)
                    val urgency = homeDeadlineUrgencyColor(days)
                    Text(
                        formatItDateMedium(millis),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.title,
                    )
                    Text(
                        homeDeadlineUrgencyLabel(days),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = urgency,
                    )
                } else {
                    Text("—", color = kb.subtitle)
                }
            }
            if (millis != null) {
                val days = daysRemainingCalendarIt(millis)
                Box(
                    Modifier
                        .size(10.dp)
                        .background(homeDeadlineUrgencyColor(days), CircleShape),
                )
            }
        }
    }
}

private fun formatVehicleEventCostEuro(value: Double): String {
    val nf = NumberFormat.getCurrencyInstance(KBLocale.current())
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = 2
    return nf.format(value)
}

@Composable
private fun VehicleEventRowContent(ev: VehicleEventEntity) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Text(vehicleEventTypeEmoji(ev.eventType), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ev.title, fontWeight = FontWeight.SemiBold, color = kb.title, style = MaterialTheme.typography.bodyLarge)
            Text(formatItDateTime(ev.date), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ev.km?.let {
                    Text("$it km", style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
                }
                ev.cost?.let {
                    Text(formatVehicleEventCostEuro(it), style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = kb.subtitle.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AddVehicleEventDialog(
    eventDraftAttachments: List<KBDocumentEntity>,
    attachmentUploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, type: String, date: Long, km: Int?, cost: Double?, garage: String?, notes: String?) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickKidBox: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("service") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var km by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var garage by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    val types = listOf(
        "service",
        "oil_filter",
        "gpl_filter",
        "brake_pads",
        "repair",
        "tire",
        "revision",
        "other",
    )
    val pickDate = rememberLifeDatePickerKeepingTime { date = it }
    val pickTime = rememberLifeTimePicker { date = it }
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val canSave = title.trim().isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = kb.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                KidBoxIosFormTopBar(
                    title = stringResource(R.string.vehicles_new_service),
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            onConfirm(
                                title.trim(),
                                type,
                                date,
                                km.trim().takeIf { it.isNotEmpty() }?.toIntOrNull(),
                                cost.replace(',', '.').takeIf { it.isNotBlank() }?.toDoubleOrNull(),
                                garage.trim().takeIf { it.isNotEmpty() },
                                notes.trim().takeIf { it.isNotEmpty() },
                            )
                        }
                    },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = orange,
                )
                HorizontalDivider(color = kb.divider)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(title, { title = it }, stringResource(R.string.vehicles_title_field), kb = kb)
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { typeMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.home_items_type), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(vehicleEventTypeLabelIt(type), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = typeMenuOpen,
                                onDismissRequest = { typeMenuOpen = false },
                            ) {
                                types.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(vehicleEventTypeLabelIt(t)) },
                                        onClick = {
                                            type = t
                                            typeMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.vehicles_date), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = kb.surfaceOverlay,
                                    modifier = Modifier.clickable { pickDate(date) },
                                ) {
                                    Text(
                                        formatItDateMedium(date),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = kb.title,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = kb.surfaceOverlay,
                                    modifier = Modifier.clickable { pickTime(date) },
                                ) {
                                    Text(
                                        formatItTimeHm(date),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = kb.title,
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(
                            km,
                            { km = it },
                            stringResource(R.string.vehicles_km),
                            kb = kb,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(
                            cost,
                            { cost = it },
                            stringResource(R.string.vehicles_cost),
                            kb = kb,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(garage, { garage = it }, stringResource(R.string.vehicles_workshop), kb = kb)
                    }
                    Text(
                        stringResource(R.string.life_notes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            placeholder = { Text(stringResource(R.string.life_notes), color = kb.subtitle) },
                            singleLine = false,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kb.title,
                                unfocusedTextColor = kb.title,
                                cursorColor = kb.title,
                                focusedPlaceholderColor = kb.subtitle,
                                unfocusedPlaceholderColor = kb.subtitle,
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        stringResource(R.string.life_attachments),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    HealthAttachmentsCard(
                        attachments = eventDraftAttachments,
                        tintColor = orange,
                        isUploading = attachmentUploading,
                        onPickFile = onPickFile,
                        onPickPhoto = onPickPhoto,
                        onTakePhoto = onTakePhoto,
                        onOpenAttachment = onOpenAttachment,
                        onDeleteAttachment = onDeleteAttachment,
                        onPickFromKidBoxDocuments = onPickKidBox,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditVehicleDialog(
    initial: it.vittorioscocca.kidbox.data.local.entity.VehicleEntity,
    vehicleAttachments: List<KBDocumentEntity>,
    attachmentUploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (it.vittorioscocca.kidbox.data.local.entity.VehicleEntity) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickKidBox: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var plate by remember(initial.id) { mutableStateOf(initial.licensePlate.orEmpty()) }
    var brand by remember(initial.id) { mutableStateOf(initial.brand.orEmpty()) }
    var model by remember(initial.id) { mutableStateOf(initial.model.orEmpty()) }
    var yearText by remember(initial.id) { mutableStateOf(initial.year?.toString().orEmpty()) }
    var fuel by remember(initial.id) { mutableStateOf(initial.fuelType ?: "benzina") }
    var color by remember(initial.id) { mutableStateOf(initial.color.orEmpty()) }
    var vin by remember(initial.id) { mutableStateOf(initial.vin.orEmpty()) }
    var ins by remember(initial.id) { mutableStateOf(initial.insuranceExpiryDate) }
    var rev by remember(initial.id) { mutableStateOf(initial.revisionExpiryDate) }
    var tax by remember(initial.id) { mutableStateOf(initial.taxExpiryDate) }
    var lastSvc by remember(initial.id) { mutableStateOf(initial.lastServiceDate) }
    var nextSvc by remember(initial.id) { mutableStateOf(initial.nextServiceDate) }
    var kmText by remember(initial.id) { mutableStateOf(initial.currentKm?.toString().orEmpty()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var hasIns by remember(initial.id) { mutableStateOf(initial.insuranceExpiryDate != null) }
    var hasRev by remember(initial.id) { mutableStateOf(initial.revisionExpiryDate != null) }
    var hasTax by remember(initial.id) { mutableStateOf(initial.taxExpiryDate != null) }
    var hasNextSvc by remember(initial.id) { mutableStateOf(initial.nextServiceDate != null) }
    var fuelMenuOpen by remember { mutableStateOf(false) }

    val fuels = listOf("benzina", "diesel", "elettrica", "ibrida", "gpl")
    val pickIns = rememberLifeDatePicker { ins = it }
    val pickRev = rememberLifeDatePicker { rev = it }
    val pickTax = rememberLifeDatePicker { tax = it }
    val pickNext = rememberLifeDatePicker { nextSvc = it }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val switchGreen = Color(0xFF27AE60)
    val canSave = name.trim().isNotBlank()
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = switchGreen,
        checkedTrackColor = switchGreen.copy(alpha = 0.35f),
        uncheckedThumbColor = kb.subtitle.copy(alpha = 0.55f),
        uncheckedTrackColor = kb.surfaceOverlay,
        uncheckedBorderColor = Color.Transparent,
    )

    fun commitSave() {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val reminderAuto = hasIns || hasRev || hasTax || hasNextSvc
        onConfirm(
            initial.copy(
                name = trimmedName,
                licensePlate = plate.trim().takeIf { it.isNotEmpty() },
                brand = brand.trim().takeIf { it.isNotEmpty() },
                model = model.trim().takeIf { it.isNotEmpty() },
                year = yearText.toIntOrNull(),
                fuelType = fuel,
                color = color.trim().takeIf { it.isNotEmpty() },
                vin = vin.trim().takeIf { it.isNotEmpty() },
                insuranceExpiryDate = ins,
                revisionExpiryDate = rev,
                taxExpiryDate = tax,
                lastServiceDate = lastSvc,
                nextServiceDate = nextSvc,
                currentKm = kmText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull(),
                notes = notes.trim().takeIf { it.isNotEmpty() },
                reminderEnabled = reminderAuto,
            ),
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = kb.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                KidBoxIosFormTopBar(
                    title = stringResource(R.string.vehicles_edit),
                    onCancel = onDismiss,
                    onSave = { if (canSave) commitSave() },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = orange,
                )
                HorizontalDivider(color = kb.divider)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(name, { name = it }, stringResource(R.string.life_name), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(plate, { plate = it }, stringResource(R.string.vehicles_plate), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(brand, { brand = it }, stringResource(R.string.home_items_brand), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(model, { model = it }, stringResource(R.string.home_items_model), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(
                            yearText,
                            { yearText = it },
                            stringResource(R.string.vehicles_year),
                            kb = kb,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { fuelMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.vehicles_fuel), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(vehicleFuelLabelIt(fuel), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = fuelMenuOpen,
                                onDismissRequest = { fuelMenuOpen = false },
                            ) {
                                fuels.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(vehicleFuelLabelIt(f)) },
                                        onClick = {
                                            fuel = f
                                            fuelMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(color, { color = it }, stringResource(R.string.vehicles_color), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(vin, { vin = it }, stringResource(R.string.vehicles_vin), kb = kb)
                    }

                    Text(
                        stringResource(R.string.home_items_deadlines),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.vehicles_insurance), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasIns,
                                onCheckedChange = {
                                    hasIns = it
                                    if (!it) ins = null else if (ins == null) ins = System.currentTimeMillis()
                                },
                                colors = switchColors,
                            )
                        }
                        if (hasIns) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.home_items_deadline), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = kb.surfaceOverlay,
                                    modifier = Modifier.clickable { pickIns(ins) },
                                ) {
                                    Text(
                                        formatItDateMedium(ins ?: System.currentTimeMillis()),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = kb.title,
                                    )
                                }
                            }
                        }

                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.vehicles_inspection), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasRev,
                                onCheckedChange = {
                                    hasRev = it
                                    if (!it) rev = null else if (rev == null) rev = System.currentTimeMillis()
                                },
                                colors = switchColors,
                            )
                        }
                        if (hasRev) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.home_items_deadline), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = kb.surfaceOverlay,
                                    modifier = Modifier.clickable { pickRev(rev) },
                                ) {
                                    Text(
                                        formatItDateMedium(rev ?: System.currentTimeMillis()),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = kb.title,
                                    )
                                }
                            }
                        }

                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.vehicles_road_tax), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasTax,
                                onCheckedChange = {
                                    hasTax = it
                                    if (!it) tax = null else if (tax == null) tax = System.currentTimeMillis()
                                },
                                colors = switchColors,
                            )
                        }
                        if (hasTax) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.home_items_deadline), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = kb.surfaceOverlay,
                                    modifier = Modifier.clickable { pickTax(tax) },
                                ) {
                                    Text(
                                        formatItDateMedium(tax ?: System.currentTimeMillis()),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = kb.title,
                                    )
                                }
                            }
                        }

                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.vehicles_next_service),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = kb.title,
                            )
                            Switch(
                                checked = hasNextSvc,
                                onCheckedChange = {
                                    hasNextSvc = it
                                    if (!it) nextSvc = null else if (nextSvc == null) nextSvc = System.currentTimeMillis()
                                },
                                colors = switchColors,
                            )
                        }
                        if (hasNextSvc) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.vehicles_date), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = kb.surfaceOverlay,
                                    modifier = Modifier.clickable { pickNext(nextSvc) },
                                ) {
                                    Text(
                                        formatItDateMedium(nextSvc ?: System.currentTimeMillis()),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = kb.title,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.life_notes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(
                            kmText,
                            { kmText = it },
                            stringResource(R.string.vehicles_current_km),
                            kb = kb,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        IosFormDivider(kb)
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            placeholder = { Text(stringResource(R.string.life_notes), color = kb.subtitle) },
                            singleLine = false,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kb.title,
                                unfocusedTextColor = kb.title,
                                cursorColor = kb.title,
                                focusedPlaceholderColor = kb.subtitle,
                                unfocusedPlaceholderColor = kb.subtitle,
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        stringResource(R.string.life_attachments),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    HealthAttachmentsCard(
                        attachments = vehicleAttachments,
                        tintColor = orange,
                        isUploading = attachmentUploading,
                        onPickFile = onPickFile,
                        onPickPhoto = onPickPhoto,
                        onTakePhoto = onTakePhoto,
                        onOpenAttachment = onOpenAttachment,
                        onDeleteAttachment = onDeleteAttachment,
                        onPickFromKidBoxDocuments = onPickKidBox,
                    )
                }
            }
        }
    }
}
