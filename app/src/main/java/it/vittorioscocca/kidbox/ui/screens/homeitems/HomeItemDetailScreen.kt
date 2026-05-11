package it.vittorioscocca.kidbox.ui.screens.homeitems

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Kitchen
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.ui.components.IosFormDivider
import it.vittorioscocca.kidbox.ui.components.IosGroupedCard
import it.vittorioscocca.kidbox.ui.components.IosPlainTextFieldRow
import it.vittorioscocca.kidbox.ui.components.KidBoxIosFormTopBar
import it.vittorioscocca.kidbox.ui.screens.life.daysRemainingCalendarIt
import it.vittorioscocca.kidbox.ui.screens.life.formatItDateMedium
import it.vittorioscocca.kidbox.ui.screens.life.homeCategoryLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.screens.life.homeDeadlineUrgencyColor
import it.vittorioscocca.kidbox.ui.screens.life.homeDeadlineUrgencyLabel
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File

private fun homeCategoryIcon(cat: String): ImageVector = when (cat) {
    "appliance" -> Icons.Filled.Kitchen
    "system" -> Icons.Filled.Build
    "contract" -> Icons.Filled.Description
    else -> Icons.Filled.Home
}

private val categoryIconTint = Color(0xFF8B6914)
private val accentOrange = Color(0xFFFF6B00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeItemDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HomeItemDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val item by viewModel.item.collectAsStateWithLifecycle()
    val itemAttachments by viewModel.itemAttachments.collectAsStateWithLifecycle()
    val attachmentUploading by viewModel.attachmentUploading.collectAsStateWithLifecycle()
    val openFileEvent by viewModel.openFileEvent.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showKidBoxPicker by remember { mutableStateOf(false) }

    val kb = MaterialTheme.kidBoxColors

    LaunchedEffect(attachmentError) {
        attachmentError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
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

    val camDir = remember { File(context.cacheDir, "casa-camera").apply { mkdirs() } }
    val camFile = remember { File(camDir, "home_item_cam.jpg") }
    val camUri = remember(camFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", camFile)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) item?.let { viewModel.uploadHomeItemAttachment(camUri) }
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) takePicture.launch(camUri) else Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        u?.let { viewModel.uploadHomeItemAttachment(it) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { viewModel.uploadHomeItemAttachment(it) }
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Dettaglio", color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifica", tint = kb.title)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = kb.title)
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
        val itm = item
        if (itm == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeItemHeaderCard(itm, kb)
            Text(
                "SCADENZE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HomeDeadlineCard(
                title = "Garanzia",
                dateMillis = itm.warrantyExpiryDate,
                kb = kb,
            )
            HomeDeadlineCard(
                title = "Prossima manutenzione",
                dateMillis = itm.nextServiceDate,
                kb = kb,
            )
            Text(
                "ALLEGATI",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HealthAttachmentsCard(
                attachments = itemAttachments,
                tintColor = accentOrange,
                isUploading = attachmentUploading,
                onPickFile = { pickFile.launch(arrayOf("*/*")) },
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onTakePhoto = {
                    when {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED -> takePicture.launch(camUri)
                        else -> camPerm.launch(Manifest.permission.CAMERA)
                    }
                },
                onOpenAttachment = { viewModel.openAttachment(it) },
                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                onPickFromKidBoxDocuments = { showKidBoxPicker = true },
            )
        }
    }

    item?.familyId?.let { fid ->
        if (showKidBoxPicker && fid.isNotBlank()) {
            KidBoxDocumentPickerSheet(
                familyId = fid,
                onDismiss = { showKidBoxPicker = false },
                onPickedUri = {
                    viewModel.uploadHomeItemAttachment(it)
                    showKidBoxPicker = false
                },
            )
        }
    }

    if (editing && item != null) {
        EditHomeItemDialog(
            initial = item!!,
            itemAttachments = itemAttachments,
            attachmentUploading = attachmentUploading,
            onPickFile = { pickFile.launch(arrayOf("*/*")) },
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onTakePhoto = {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED -> takePicture.launch(camUri)
                    else -> camPerm.launch(Manifest.permission.CAMERA)
                }
            },
            onPickKidBox = { showKidBoxPicker = true },
            onOpenAttachment = { viewModel.openAttachment(it) },
            onDeleteAttachment = { viewModel.deleteAttachment(it) },
            onDismiss = { editing = false },
            onConfirm = { updated ->
                viewModel.updateHomeItem(updated) { err -> toast = err }
                editing = false
            },
        )
    }

    if (confirmDelete && item != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare questo elemento?") },
            text = { Text("Verrà rimosso per tutta la famiglia.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHomeItem(item!!) { err -> toast = err }
                        confirmDelete = false
                        onNavigateBack()
                    },
                ) { Text("Elimina", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("OK") } },
            title = { Text("Errore") },
            text = { Text(msg) },
        )
    }
}

private fun homeCategoryPickerLabel(raw: String): String = when (raw) {
    "appliance" -> "Elettrodomestico"
    "system" -> "Impianto"
    "contract" -> "Contratto"
    "other" -> "Altro"
    else -> raw
}

@Composable
private fun EditHomeItemDialog(
    initial: HomeItemEntity,
    itemAttachments: List<KBDocumentEntity>,
    attachmentUploading: Boolean,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickKidBox: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (HomeItemEntity) -> Unit,
) {
    val cats = listOf("appliance", "system", "contract", "other")
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var brand by remember(initial.id) { mutableStateOf(initial.brand.orEmpty()) }
    var model by remember(initial.id) { mutableStateOf(initial.model.orEmpty()) }
    var serialNumber by remember(initial.id) { mutableStateOf(initial.serialNumber.orEmpty()) }
    var hasPurchase by remember(initial.id) { mutableStateOf(initial.purchaseDate != null) }
    var purchaseDate by remember(initial.id) { mutableStateOf(initial.purchaseDate ?: System.currentTimeMillis()) }
    var hasWarranty by remember(initial.id) { mutableStateOf(initial.warrantyExpiryDate != null) }
    var warrantyDate by remember(initial.id) { mutableStateOf(initial.warrantyExpiryDate ?: System.currentTimeMillis()) }
    var hasService by remember(initial.id) { mutableStateOf(initial.nextServiceDate != null) }
    var serviceDate by remember(initial.id) { mutableStateOf(initial.nextServiceDate ?: System.currentTimeMillis()) }
    var hasPeriod by remember(initial.id) { mutableStateOf(initial.servicePeriodMonths != null) }
    var serviceMonths by remember(initial.id) { mutableStateOf((initial.servicePeriodMonths ?: 12).coerceIn(1, 60)) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    val pickPurchase = rememberLifeDatePicker { purchaseDate = it }
    val pickWarranty = rememberLifeDatePicker { warrantyDate = it }
    val pickService = rememberLifeDatePicker { serviceDate = it }

    val kb = MaterialTheme.kidBoxColors
    val canSave = name.trim().isNotBlank()

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
                    title = "Modifica",
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val reminderEnabled = hasWarranty || hasService
                            onConfirm(
                                initial.copy(
                                    name = name.trim(),
                                    category = category,
                                    brand = brand.trim().takeIf { it.isNotEmpty() },
                                    model = model.trim().takeIf { it.isNotEmpty() },
                                    serialNumber = serialNumber.trim().takeIf { it.isNotEmpty() },
                                    purchaseDate = if (hasPurchase) purchaseDate else null,
                                    warrantyExpiryDate = if (hasWarranty) warrantyDate else null,
                                    nextServiceDate = if (hasService) serviceDate else null,
                                    servicePeriodMonths = if (hasService && hasPeriod) serviceMonths else null,
                                    notes = notes.trim().takeIf { it.isNotEmpty() },
                                    reminderEnabled = reminderEnabled,
                                ),
                            )
                        }
                    },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = accentOrange,
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
                        IosPlainTextFieldRow(name, { name = it }, "Nome", kb = kb)
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { categoryMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Categoria", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(homeCategoryPickerLabel(category), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = categoryMenuOpen,
                                onDismissRequest = { categoryMenuOpen = false },
                            ) {
                                cats.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(homeCategoryPickerLabel(c)) },
                                        onClick = {
                                            category = c
                                            categoryMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(brand, { brand = it }, "Marca", kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(model, { model = it }, "Modello", kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(serialNumber, { serialNumber = it }, "Numero di serie", kb = kb)
                    }

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Data acquisto", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasPurchase,
                                onCheckedChange = { on ->
                                    hasPurchase = on
                                    if (on) purchaseDate = initial.purchaseDate ?: System.currentTimeMillis()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentOrange,
                                    checkedTrackColor = accentOrange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (hasPurchase) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickPurchase(purchaseDate) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Acquisto", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    formatItDateMedium(purchaseDate),
                                    color = accentOrange,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Scadenza garanzia", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasWarranty,
                                onCheckedChange = { on ->
                                    hasWarranty = on
                                    if (on) warrantyDate = initial.warrantyExpiryDate ?: System.currentTimeMillis()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentOrange,
                                    checkedTrackColor = accentOrange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (hasWarranty) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickWarranty(warrantyDate) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Garanzia", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    formatItDateMedium(warrantyDate),
                                    color = accentOrange,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Prossima manutenzione", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasService,
                                onCheckedChange = { on ->
                                    hasService = on
                                    if (on) serviceDate = initial.nextServiceDate ?: System.currentTimeMillis()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentOrange,
                                    checkedTrackColor = accentOrange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (hasService) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickService(serviceDate) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Manutenzione", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    formatItDateMedium(serviceDate),
                                    color = accentOrange,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Periodicità (mesi)", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Switch(
                                    checked = hasPeriod,
                                    onCheckedChange = { hasPeriod = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = accentOrange,
                                        checkedTrackColor = accentOrange.copy(alpha = 0.35f),
                                    ),
                                )
                            }
                            if (hasPeriod) {
                                IosFormDivider(kb)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { if (serviceMonths > 1) serviceMonths-- },
                                        enabled = serviceMonths > 1,
                                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                    Text(
                                        "Ogni $serviceMonths mesi",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = kb.title,
                                    )
                                    TextButton(
                                        onClick = { if (serviceMonths < 60) serviceMonths++ },
                                        enabled = serviceMonths < 60,
                                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                                }
                            }
                        }
                    }

                    Text(
                        "Note",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            placeholder = { Text("Note", color = kb.subtitle) },
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
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Allegati",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    HealthAttachmentsCard(
                        attachments = itemAttachments,
                        tintColor = accentOrange,
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
private fun HomeItemHeaderCard(it: HomeItemEntity, kb: KidBoxColorScheme) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    homeCategoryIcon(it.category),
                    contentDescription = null,
                    tint = categoryIconTint,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    it.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = kb.title,
                )
            }
            Text(
                homeCategoryLabelIt(it.category),
                style = MaterialTheme.typography.bodyMedium,
                color = kb.subtitle,
            )
            it.brand?.takeIf { it.isNotBlank() }?.let { b ->
                HomeItemDetailLineRow(label = "Marca", value = b, kb = kb)
            }
            it.model?.takeIf { it.isNotBlank() }?.let { m ->
                HomeItemDetailLineRow(label = "Modello", value = m, kb = kb)
            }
            it.serialNumber?.takeIf { it.isNotBlank() }?.let { s ->
                HomeItemDetailLineRow(label = "Serie", value = s, kb = kb)
            }
            it.purchaseDate?.let { p ->
                HomeItemDetailLineRow(label = "Acquisto", value = formatItDateMedium(p), kb = kb)
            }
            it.servicePeriodMonths?.let { m ->
                HomeItemDetailLineRow(label = "Periodicità", value = "$m mesi", kb = kb)
            }
            it.notes?.takeIf { it.isNotBlank() }?.let { n ->
                Text(n, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun HomeItemDetailLineRow(label: String, value: String, kb: KidBoxColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = kb.subtitle, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = kb.title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HomeDeadlineCard(
    title: String,
    dateMillis: Long?,
    kb: KidBoxColorScheme,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val days = dateMillis?.let { daysRemainingCalendarIt(it) }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = kb.subtitle,
                )
                if (dateMillis != null && days != null) {
                    Text(
                        formatItDateMedium(dateMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.title,
                    )
                    Text(
                        homeDeadlineUrgencyLabel(days),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = homeDeadlineUrgencyColor(days),
                    )
                } else {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = kb.subtitle.copy(alpha = 0.55f),
                    )
                }
            }
            if (dateMillis != null && days != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(homeDeadlineUrgencyColor(days), CircleShape),
                )
            }
        }
    }
}
