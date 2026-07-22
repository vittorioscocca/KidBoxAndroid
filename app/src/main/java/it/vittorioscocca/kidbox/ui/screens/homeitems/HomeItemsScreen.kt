package it.vittorioscocca.kidbox.ui.screens.homeitems

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MoreVert
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
import it.vittorioscocca.kidbox.data.life.HousePaymentDeadlineCalculator
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.ui.screens.life.deadlineUrgencyColor
import it.vittorioscocca.kidbox.ui.screens.life.earliestNonNull
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.homeCategoryLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.housePaymentTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.components.IosFormDivider
import it.vittorioscocca.kidbox.ui.components.IosGroupedCard
import it.vittorioscocca.kidbox.ui.components.IosPlainTextFieldRow
import it.vittorioscocca.kidbox.ui.components.KidBoxIosFormTopBar
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private fun categoryIcon(cat: String): ImageVector = when (cat) {
    "appliance" -> Icons.Filled.Kitchen
    "system" -> Icons.Filled.Build
    "contract" -> Icons.Filled.Description
    else -> Icons.Filled.Home
}

/** Etichetta categoria singolare (picker), allineata a iOS. */
private fun casaCategoryPickerLabel(context: android.content.Context, raw: String): String = when (raw) {
    "appliance" -> context.getString(R.string.home_items_cat_appliance)
    "system" -> context.getString(R.string.home_items_cat_system)
    "contract" -> context.getString(R.string.home_items_cat_contract)
    "other" -> context.getString(R.string.home_items_cat_other)
    else -> raw
}

private sealed class CasaListRow(val stableKey: String) {
    data class SectionHeader(val label: String) : CasaListRow("hdr_$label")
    data class HomeItemRow(val item: HomeItemEntity) : CasaListRow("item_${item.id}")
    data class HousePaymentRow(val payment: HousePaymentEntity) : CasaListRow("hpay_${payment.id}")
}

private data class KidBoxCasaDraftRef(val draftId: String, val isHomeItem: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeItemsScreen(
    onNavigateBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenHousePayment: (String) -> Unit,
    viewModel: HomeItemsViewModel = hiltViewModel(),
) {
    val deadlinesHeader = stringResource(R.string.home_items_deadlines_payments)
    val context = LocalContext.current
    val items by viewModel.homeItems.collectAsStateWithLifecycle()
    val housePayments by viewModel.housePayments.collectAsStateWithLifecycle()
    val draftAttachmentUploading by viewModel.draftAttachmentUploading.collectAsStateWithLifecycle()
    val draftAttachmentError by viewModel.draftAttachmentError.collectAsStateWithLifecycle()
    val openDraftFileEvent by viewModel.openDraftFileEvent.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showAddPayment by remember { mutableStateOf(false) }
    var homeAddDraftId by remember { mutableStateOf<String?>(null) }
    var paymentAddDraftId by remember { mutableStateOf<String?>(null) }
    var kidBoxCasaDraftRef by remember { mutableStateOf<KidBoxCasaDraftRef?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<HomeItemEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)

    LaunchedEffect(draftAttachmentError) {
        draftAttachmentError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeDraftAttachmentError()
        }
    }
    LaunchedEffect(openDraftFileEvent) {
        openDraftFileEvent?.let { (mime, file) ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
            viewModel.consumeOpenDraftFileEvent()
        }
    }

    val camDir = remember { File(context.cacheDir, "casa-camera").apply { mkdirs() } }
    val camFileHomeAdd = remember { File(camDir, "home_add_cam.jpg") }
    val camUriHomeAdd = remember(camFileHomeAdd) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", camFileHomeAdd)
    }
    val camFilePaymentAdd = remember { File(camDir, "payment_add_cam.jpg") }
    val camUriPaymentAdd = remember(camFilePaymentAdd) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", camFilePaymentAdd)
    }

    val takePictureHomeAdd = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) homeAddDraftId?.let { viewModel.uploadDraftHomeItemAttachment(camUriHomeAdd, it) }
    }
    val camPermHomeAdd = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) takePictureHomeAdd.launch(camUriHomeAdd) else Toast.makeText(context, context.getString(R.string.life_camera_denied), Toast.LENGTH_SHORT).show()
    }
    val pickPhotoHomeAdd = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        val id = homeAddDraftId
        if (u != null && id != null) viewModel.uploadDraftHomeItemAttachment(u, id)
    }
    val pickFileHomeAdd = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val id = homeAddDraftId
        if (u != null && id != null) viewModel.uploadDraftHomeItemAttachment(u, id)
    }

    val takePicturePaymentAdd = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) paymentAddDraftId?.let { viewModel.uploadDraftHousePaymentAttachment(camUriPaymentAdd, it) }
    }
    val camPermPaymentAdd = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) takePicturePaymentAdd.launch(camUriPaymentAdd) else Toast.makeText(context, context.getString(R.string.life_camera_denied), Toast.LENGTH_SHORT).show()
    }
    val pickPhotoPaymentAdd = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        val id = paymentAddDraftId
        if (u != null && id != null) viewModel.uploadDraftHousePaymentAttachment(u, id)
    }
    val pickFilePaymentAdd = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val id = paymentAddDraftId
        if (u != null && id != null) viewModel.uploadDraftHousePaymentAttachment(u, id)
    }

    val homeDraftFlow = remember(homeAddDraftId) {
        homeAddDraftId?.let { viewModel.observeDraftHomeItemAttachments(it) } ?: flowOf(emptyList())
    }
    val homeDraftAttachments by homeDraftFlow.collectAsStateWithLifecycle(emptyList())

    val paymentDraftFlow = remember(paymentAddDraftId) {
        paymentAddDraftId?.let { viewModel.observeDraftHousePaymentAttachments(it) } ?: flowOf(emptyList())
    }
    val paymentDraftAttachments by paymentDraftFlow.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(showAdd) {
        if (showAdd && homeAddDraftId == null) homeAddDraftId = UUID.randomUUID().toString()
    }
    LaunchedEffect(showAddPayment) {
        if (showAddPayment && paymentAddDraftId == null) paymentAddDraftId = UUID.randomUUID().toString()
    }

    val listRows = remember(items, housePayments) {
        val grouped = items.groupBy { it.category }
        val categoryOrder = listOf("appliance", "system", "contract")
        val sortedPayments = housePayments.sortedWith(
            compareBy<HousePaymentEntity>(
                { HousePaymentDeadlineCalculator.urgencyRank(it) },
                { HousePaymentDeadlineCalculator.earliestDisplayDeadlineMillis(it) ?: Long.MAX_VALUE },
            ),
        )
        buildList<CasaListRow> {
            categoryOrder.forEach { cat ->
                val rows = grouped[cat].orEmpty()
                if (rows.isNotEmpty()) {
                    add(CasaListRow.SectionHeader(homeCategoryLabelIt(cat)))
                    rows.forEach { add(CasaListRow.HomeItemRow(it)) }
                }
            }
            if (sortedPayments.isNotEmpty()) {
                add(CasaListRow.SectionHeader(deadlinesHeader))
                sortedPayments.forEach { add(CasaListRow.HousePaymentRow(it)) }
            }
            val other = grouped["other"].orEmpty()
            if (other.isNotEmpty()) {
                add(CasaListRow.SectionHeader(homeCategoryLabelIt("other")))
                other.forEach { add(CasaListRow.HomeItemRow(it)) }
            }
            val seenCats = setOf("appliance", "system", "contract", "other")
            (grouped.keys - seenCats).sorted().forEach { cat ->
                val rows = grouped[cat].orEmpty()
                if (rows.isNotEmpty()) {
                    add(CasaListRow.SectionHeader(homeCategoryLabelIt(cat)))
                    rows.forEach { add(CasaListRow.HomeItemRow(it)) }
                }
            }
        }
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_items_house), color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    IconButton(onClick = { topMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_items_cat_other), tint = kb.title)
                    }
                    DropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_items_new_item_house)) },
                            onClick = {
                                topMenuOpen = false
                                homeAddDraftId = UUID.randomUUID().toString()
                                showAdd = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_items_new_deadline_payment)) },
                            onClick = {
                                topMenuOpen = false
                                paymentAddDraftId = UUID.randomUUID().toString()
                                showAddPayment = true
                            },
                        )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (items.isEmpty() && housePayments.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = Color(0xFF8B6914))
                        Text(stringResource(R.string.home_items_none_yet), color = kb.title, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    homeAddDraftId = UUID.randomUUID().toString()
                                    showAdd = true
                                },
                                color = orange,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    stringResource(R.string.home_items_item),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }
                            Surface(
                                onClick = {
                                    paymentAddDraftId = UUID.randomUUID().toString()
                                    showAddPayment = true
                                },
                                color = orange,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    stringResource(R.string.home_items_deadline),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
            items(
                listRows.size,
                key = { i -> listRows[i].stableKey },
            ) { idx ->
                when (val row = listRows[idx]) {
                    is CasaListRow.SectionHeader -> {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = kb.subtitle,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    is CasaListRow.HomeItemRow -> {
                        val homeRow = row.item
                        val deadline = earliestNonNull(homeRow.warrantyExpiryDate, homeRow.nextServiceDate)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenItem(homeRow.id) },
                                    onLongClick = { toDelete = homeRow },
                                ),
                            colors = CardDefaults.cardColors(containerColor = kb.card),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(categoryIcon(homeRow.category), contentDescription = null, tint = orange)
                                Column(Modifier.weight(1f)) {
                                    Text(homeRow.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                                    val bm = listOfNotNull(homeRow.brand, homeRow.model).joinToString(" ")
                                    if (bm.isNotBlank()) Text(bm, color = MaterialTheme.kidBoxColors.subtitle, style = MaterialTheme.typography.bodySmall)
                                }
                                deadline?.let {
                                    Surface(color = deadlineUrgencyColor(it), shape = RoundedCornerShape(12.dp)) {
                                        Text(
                                            formatItDate(it),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                        )
                                    }
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = kb.subtitle)
                            }
                        }
                    }
                    is CasaListRow.HousePaymentRow -> {
                        val p = row.payment
                        val deadline = HousePaymentDeadlineCalculator.earliestDisplayDeadlineMillis(p)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { onOpenHousePayment(p.id) }),
                            colors = CardDefaults.cardColors(containerColor = kb.card),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Filled.Event, contentDescription = null, tint = orange)
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold, color = kb.title)
                                    Text(
                                        housePaymentTypeLabelIt(p.typeRaw),
                                        color = kb.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                deadline?.let {
                                    Surface(color = deadlineUrgencyColor(it), shape = RoundedCornerShape(12.dp)) {
                                        Text(
                                            formatItDate(it),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                        )
                                    }
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = kb.subtitle)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAdd && homeAddDraftId != null) {
        val hid = homeAddDraftId!!
        AddHomeItemDialog(
            draftAttachments = homeDraftAttachments,
            attachmentUploading = draftAttachmentUploading,
            onPickFile = { pickFileHomeAdd.launch(arrayOf("*/*")) },
            onPickPhoto = {
                pickPhotoHomeAdd.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTakePhoto = {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED -> takePictureHomeAdd.launch(camUriHomeAdd)
                    else -> camPermHomeAdd.launch(Manifest.permission.CAMERA)
                }
            },
            onPickKidBoxDocuments = { kidBoxCasaDraftRef = KidBoxCasaDraftRef(hid, isHomeItem = true) },
            onOpenAttachment = { viewModel.openDraftAttachment(it) },
            onDeleteAttachment = { viewModel.deleteDraftAttachment(it) },
            onDismiss = {
                viewModel.discardDraftHomeItemAttachments(hid)
                homeAddDraftId = null
                showAdd = false
            },
            onConfirm = { name, category, brand, model, serial, purchase, warranty, nextSvc, months, notes, reminder ->
                viewModel.addHomeItem(
                    name = name,
                    category = category,
                    brand = brand,
                    model = model,
                    serialNumber = serial,
                    purchaseDate = purchase,
                    warrantyExpiryDate = warranty,
                    nextServiceDate = nextSvc,
                    servicePeriodMonths = months,
                    notes = notes,
                    reminderEnabled = reminder,
                    presetItemId = hid,
                ) { err -> toast = err }
                homeAddDraftId = null
                showAdd = false
            },
        )
    }

    if (showAddPayment && paymentAddDraftId != null) {
        val pid = paymentAddDraftId!!
        AddHousePaymentDialog(
            draftAttachments = paymentDraftAttachments,
            attachmentUploading = draftAttachmentUploading,
            onPickFile = { pickFilePaymentAdd.launch(arrayOf("*/*")) },
            onPickPhoto = {
                pickPhotoPaymentAdd.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTakePhoto = {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED -> takePicturePaymentAdd.launch(camUriPaymentAdd)
                    else -> camPermPaymentAdd.launch(Manifest.permission.CAMERA)
                }
            },
            onPickKidBoxDocuments = { kidBoxCasaDraftRef = KidBoxCasaDraftRef(pid, isHomeItem = false) },
            onOpenAttachment = { viewModel.openDraftAttachment(it) },
            onDeleteAttachment = { viewModel.deleteDraftAttachment(it) },
            onDismiss = {
                viewModel.discardDraftHousePaymentAttachments(pid)
                paymentAddDraftId = null
                showAddPayment = false
            },
            onConfirm = { name, typeRaw, subtypeRaw, importo, giorno, dataScadenza, dataContratto, fornitore, note, reminderOn ->
                viewModel.addHousePayment(
                    name = name,
                    typeRaw = typeRaw,
                    subtypeRaw = subtypeRaw,
                    importo = importo,
                    giornoDiScadenzaMensile = giorno,
                    dataScadenza = dataScadenza,
                    dataScadenzaContratto = dataContratto,
                    fornitore = fornitore,
                    note = note,
                    reminderOn = reminderOn,
                    presetPaymentId = pid,
                ) { err -> toast = err }
                paymentAddDraftId = null
                showAddPayment = false
            },
        )
    }

    val kidPick = kidBoxCasaDraftRef
    val fidPicker = viewModel.familyIdForPicker
    if (kidPick != null && fidPicker.isNotBlank()) {
        KidBoxDocumentPickerSheet(
            familyId = fidPicker,
            onDismiss = { kidBoxCasaDraftRef = null },
            onPickedUri = { uri ->
                if (kidPick.isHomeItem) {
                    viewModel.uploadDraftHomeItemAttachment(uri, kidPick.draftId)
                } else {
                    viewModel.uploadDraftHousePaymentAttachment(uri, kidPick.draftId)
                }
                kidBoxCasaDraftRef = null
            },
        )
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare ${target.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHomeItem(target) { err -> toast = err }
                    toDelete = null
                }) { Text(stringResource(R.string.life_delete), color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.life_cancel)) } },
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
private fun AddHomeItemDialog(
    draftAttachments: List<KBDocumentEntity>,
    attachmentUploading: Boolean,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickKidBoxDocuments: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        category: String,
        brand: String?,
        model: String?,
        serialNumber: String?,
        purchase: Long?,
        warranty: Long?,
        nextSvc: Long?,
        months: Int?,
        notes: String?,
        reminder: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("appliance") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var hasPurchase by remember { mutableStateOf(false) }
    var purchaseDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var hasWarranty by remember { mutableStateOf(false) }
    var warrantyDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var hasService by remember { mutableStateOf(false) }
    var serviceDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var hasPeriod by remember { mutableStateOf(false) }
    var serviceMonths by remember { mutableStateOf(12) }
    var notes by remember { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    val cats = listOf("appliance", "system", "contract", "other")
    val pickPurchase = rememberLifeDatePicker { purchaseDate = it }
    val pickWarranty = rememberLifeDatePicker { warrantyDate = it }
    val pickService = rememberLifeDatePicker { serviceDate = it }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
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
                    title = stringResource(R.string.home_items_new_item),
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val reminderEnabled = hasWarranty || hasService
                            onConfirm(
                                name.trim(),
                                category,
                                brand.trim().takeIf { it.isNotEmpty() },
                                model.trim().takeIf { it.isNotEmpty() },
                                serialNumber.trim().takeIf { it.isNotEmpty() },
                                if (hasPurchase) purchaseDate else null,
                                if (hasWarranty) warrantyDate else null,
                                if (hasService) serviceDate else null,
                                if (hasService && hasPeriod) serviceMonths else null,
                                notes.trim().takeIf { it.isNotEmpty() },
                                reminderEnabled,
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
                        IosPlainTextFieldRow(name, { name = it }, stringResource(R.string.life_name), kb = kb)
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
                                Text(stringResource(R.string.life_category), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(casaCategoryPickerLabel(context, category), color = kb.subtitle)
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
                                        text = { Text(casaCategoryPickerLabel(context, c)) },
                                        onClick = {
                                            category = c
                                            categoryMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(brand, { brand = it }, stringResource(R.string.home_items_brand), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(model, { model = it }, stringResource(R.string.home_items_model), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(serialNumber, { serialNumber = it }, stringResource(R.string.home_items_serial), kb = kb)
                    }

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.home_items_purchase_date), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasPurchase,
                                onCheckedChange = { on ->
                                    hasPurchase = on
                                    if (on) purchaseDate = System.currentTimeMillis()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
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
                                Text(stringResource(R.string.home_items_purchase), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    formatItDate(purchaseDate),
                                    color = orange,
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
                            Text(stringResource(R.string.home_items_warranty_expiry), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasWarranty,
                                onCheckedChange = { on ->
                                    hasWarranty = on
                                    if (on) warrantyDate = System.currentTimeMillis()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
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
                                Text(stringResource(R.string.home_items_warranty), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    formatItDate(warrantyDate),
                                    color = orange,
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
                            Text(stringResource(R.string.home_items_next_maintenance), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasService,
                                onCheckedChange = { on ->
                                    hasService = on
                                    if (on) serviceDate = System.currentTimeMillis()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
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
                                Text(stringResource(R.string.home_items_maintenance), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    formatItDate(serviceDate),
                                    color = orange,
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
                                Text(stringResource(R.string.home_items_period_months), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Switch(
                                    checked = hasPeriod,
                                    onCheckedChange = { hasPeriod = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = orange,
                                        checkedTrackColor = orange.copy(alpha = 0.35f),
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
                        stringResource(R.string.life_notes),
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
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.life_attachments),
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    HealthAttachmentsCard(
                        attachments = draftAttachments,
                        tintColor = orange,
                        isUploading = attachmentUploading,
                        onPickFile = onPickFile,
                        onPickPhoto = onPickPhoto,
                        onTakePhoto = onTakePhoto,
                        onOpenAttachment = onOpenAttachment,
                        onDeleteAttachment = onDeleteAttachment,
                        onPickFromKidBoxDocuments = onPickKidBoxDocuments,
                    )
                }
            }
        }
    }
}
