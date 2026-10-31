package it.vittorioscocca.kidbox.ui.screens.work

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBWorkRecordEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.work.PendingWorkShare
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.work.WorkFields
import it.vittorioscocca.kidbox.domain.work.WorkLeavePeriod
import it.vittorioscocca.kidbox.domain.work.WorkRecordKind
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentSourcePickerSheet
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WorkTint = Color(0xFF4073BF)

/**
 * Scheda «Lavoro»: busta paga, contratto e foglio ferie di un adulto, letti
 * dall'AI e salvati con i loro valori. Gemella di `WorkHomeView` (iOS). Privata
 * di default; si può condividere con altri adulti della famiglia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(
    familyId: String,
    onBack: () -> Unit,
    onAskAssistant: (String) -> Unit,
    onUpgrade: () -> Unit,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedFile by PendingWorkShare.file.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    var showSource by remember { mutableStateOf(false) }
    var showKidBoxPicker by remember { mutableStateOf(false) }
    var showSharing by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(familyId) { viewModel.bind(familyId) }
    // File condiviso da un'altra app: all'apertura o con la scheda già aperta.
    LaunchedEffect(sharedFile) { if (sharedFile != null) viewModel.consumeSharedFile() }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); viewModel.dismissMessage() }
    }
    val droppedMsg = stringResource(R.string.work_shared_dropped)
    LaunchedEffect(state.sharedDropped) {
        if (state.sharedDropped) { snack.showSnackbar(droppedMsg); viewModel.dismissMessage() }
    }

    // ── Sorgenti del «+»: fotocamera, libreria, file, Documenti di KidBox ──
    val camDir = remember { File(context.cacheDir, "work-camera").apply { mkdirs() } }
    val camFile = remember { File(camDir, "work_tmp.jpg") }
    val camUri = remember(camFile) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", camFile) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.stageUri(camUri)
    }
    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(camUri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(viewModel::stageUri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::stageUri)
    }

    Scaffold(
        containerColor = kb.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.work_title), fontWeight = FontWeight.SemiBold, color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.work_back), tint = kb.title)
                    }
                },
                actions = {
                    if (state.isPaid && state.isMine) {
                        IconButton(onClick = { showSource = true }, enabled = !state.isAnalyzing) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.work_upload), tint = kb.title)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.isPaid) {
                WorkLocked(onUpgrade)
            } else {
                WorkContent(
                    state = state,
                    viewModel = viewModel,
                    onSharing = { viewModel.ensureProfile(); showSharing = true },
                    onAsk = onAskAssistant,
                    onOpen = { detailId = it },
                )
            }
            if (state.isAnalyzing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(kb.card).padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = WorkTint)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.work_reading), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = kb.title)
                    }
                }
            }
        }
    }

    if (showSource) {
        HealthAttachmentSourcePickerSheet(
            onDismiss = { showSource = false },
            tintColor = WorkTint,
            onTakePhoto = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    takePicture.launch(camUri)
                } else cameraPerm.launch(Manifest.permission.CAMERA)
            },
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPickFile = { pickFile.launch(arrayOf("application/pdf", "image/*")) },
            onPickFromKidBoxDocuments = { showKidBoxPicker = true },
        )
    }
    if (showKidBoxPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            onDismiss = { showKidBoxPicker = false },
            onPickedUri = { uri -> showKidBoxPicker = false; viewModel.stageUri(uri) },
        )
    }
    if (showSharing) {
        WorkSharingSheet(
            state = state,
            onDismiss = { showSharing = false },
            onConfirm = { ids -> viewModel.setSharing(ids); showSharing = false },
        )
    }
    state.draft?.let { draft ->
        WorkConfirmSheet(
            draft = draft,
            onChange = { viewModel.updateDraft(it) },
            onDismiss = { viewModel.discardDraft() },
            onSave = { viewModel.saveDraft() },
        )
    }
    detailId?.let { id ->
        state.records.firstOrNull { it.id == id }?.let { record ->
            WorkDetailSheet(
                record = record,
                fields = viewModel.fields(record),
                canEdit = record.ownerUid == state.me,
                onDismiss = { detailId = null },
                onEdit = { detailId = null; viewModel.startEdit(record) },
                onDelete = { detailId = null; viewModel.deleteRecord(record) },
                onOpenFile = {
                    viewModel.openFile(record) { file, mime ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                },
            )
        }
    }
}

// ── Piano Free ──────────────────────────────────────────────────────────

@Composable
private fun WorkLocked(onUpgrade: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Work, null, tint = kb.subtitle, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.work_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.work_locked_body), fontSize = 14.sp, color = kb.subtitle, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUpgrade) { Text(stringResource(R.string.ai_discover_plans)) }
    }
}

// ── Contenuto ───────────────────────────────────────────────────────────

@Composable
private fun WorkContent(
    state: WorkUiState,
    viewModel: WorkViewModel,
    onSharing: () -> Unit,
    onAsk: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val mineLabel = stringResource(R.string.work_me)
    val otherLabel = stringResource(R.string.work_family_member)
    val ownerRecords = state.records.filter { it.ownerUid == state.owner }.sortedByDescending { it.referenceDateEpochMillis }
    val decoded = remember(ownerRecords) { ownerRecords.associate { it.id to viewModel.fields(it) } }
    val latestPayslip = ownerRecords.firstOrNull { it.kind == WorkRecordKind.PAYSLIP }?.let { decoded[it.id] }
    val latestContract = ownerRecords.firstOrNull { it.kind == WorkRecordKind.CONTRACT }?.let { decoded[it.id] }
    val today = LocalDate.now()
    val upcomingLeave = ownerRecords.filter { it.kind == WorkRecordKind.LEAVE_PLAN }
        .mapNotNull { decoded[it.id] }
        .flatMap { it.leavePeriods }
        .filter { (it.endDay ?: LocalDate.MIN) >= today }
        .sortedBy { it.startDay }

    val qLeavePermits = stringResource(R.string.work_q_leave_permits)
    val qTaxes = stringResource(R.string.work_q_taxes)
    val qWhenLeave = stringResource(R.string.work_q_when_leave)
    val qContractEnd = stringResource(R.string.work_q_contract_end)
    val qGrossAnnual = stringResource(R.string.work_q_gross_annual)
    val questions = buildList {
        if (latestPayslip != null) { add(qLeavePermits); add(qTaxes) }
        if (upcomingLeave.isNotEmpty()) add(qWhenLeave)
        if (latestContract != null) { add(qContractEnd); add(qGrossAnnual) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.owners.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.owners.forEach { uid ->
                        val selected = state.owner == uid
                        Text(
                            state.nameOf(uid, mineLabel, otherLabel),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (selected) Color.White else kb.title,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) WorkTint else kb.card)
                                .clickable { viewModel.selectOwner(uid) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (state.isMine) {
            item { SharingRow(state, onSharing) }
            state.staged?.let { staged -> item { StagedCard(staged, viewModel) } }
        }
        if (latestPayslip != null) item {
            WorkCard(stringResource(R.string.work_latest_payslip, latestPayslip.period.ifBlank { latestPayslip.title })) {
                val f = latestPayslip
                Row2(R.string.work_f_leave_remaining, f.vacationRemaining?.let { days(it) })
                Row2(R.string.work_f_permit_remaining, f.permitHoursRemaining?.let { hours(it) })
                Row2(R.string.work_f_gross, f.gross?.let(::euro))
                Row2(R.string.work_f_taxes, f.taxes?.let(::euro))
                Row2(R.string.work_f_net, f.net?.let(::euro))
                Row2(R.string.work_f_tfr, f.tfr?.let(::euro))
            }
        }
        if (latestContract != null) item {
            WorkCard(stringResource(R.string.work_contract)) {
                val f = latestContract
                Row2(R.string.work_f_contract_type, f.contractType.ifBlank { null })
                Row2(R.string.work_f_level, f.level.ifBlank { null })
                Row2(R.string.work_f_gross_annual, f.grossAnnual?.let(::euro))
                Row2(R.string.work_f_start, WorkFields.parseDay(f.startDate)?.let(::fmtDay))
                Row2(
                    R.string.work_f_end,
                    WorkFields.parseDay(f.endDate)?.let(::fmtDay)
                        ?: if (f.contractType.lowercase().contains("indeterm")) stringResource(R.string.work_no_end) else null,
                )
                Row2(R.string.work_f_notice, f.noticePeriod.ifBlank { null })
            }
        }
        if (upcomingLeave.isNotEmpty()) item {
            WorkCard(stringResource(R.string.work_planned_leave)) {
                upcomingLeave.take(6).forEach { p ->
                    Row2Text(p.label.ifBlank { stringResource(R.string.work_leave_default) }, periodText(p))
                }
            }
        }
        if (latestPayslip == null && latestContract == null && upcomingLeave.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Description, null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (state.isMine) R.string.work_empty_mine else R.string.work_empty_other),
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = kb.title,
                )
                if (state.isMine) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.work_empty_hint), fontSize = 13.sp, color = kb.subtitle,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (state.isMine && questions.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.work_ask_assistant), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = kb.subtitle)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    questions.forEach { q ->
                        Text(
                            q, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WorkTint,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.dp, WorkTint.copy(alpha = 0.5f), CircleShape)
                                .clickable { onAsk(q) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (ownerRecords.isNotEmpty()) {
            item { Text(stringResource(R.string.work_documents), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = kb.subtitle) }
            items(ownerRecords, key = { it.id }) { r ->
                val f = decoded[r.id]
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(kb.card)
                        .clickable { onOpen(r.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(WorkTint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(
                            if (r.kind == WorkRecordKind.LEAVE_PLAN) Icons.Default.CalendarMonth else Icons.Default.Description,
                            null, tint = WorkTint, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(recordTitle(r, f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmtDay(Instant.ofEpochMilli(r.referenceDateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()), fontSize = 12.sp, color = kb.subtitle)
                    }
                    if (r.attachmentDocumentId != null) Text("📎", fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = kb.subtitle)
                }
            }
        }
    }
}

@Composable
private fun recordTitle(r: KBWorkRecordEntity, f: WorkFields?): String {
    if (f != null && f.title.isNotBlank()) return f.title
    return stringResource(
        when (r.kind) {
            WorkRecordKind.LEAVE_PLAN -> R.string.work_leave_plan
            WorkRecordKind.PAYSLIP -> R.string.work_payslip
            else -> R.string.work_contract
        },
    )
}

@Composable
private fun SharingRow(state: WorkUiState, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val p = state.myProfile
    val ids = p?.takeIf { it.visibilityScope == KBVisibilityScope.MEMBERS }?.let { decodeStringList(it.visibilityMemberIdsJson) }.orEmpty()
    val memberFallback = stringResource(R.string.work_family_member)
    val title = if (ids.isEmpty()) stringResource(R.string.work_only_me) else {
        stringResource(R.string.work_shared_with, ids.joinToString(", ") { uid -> state.members.firstOrNull { it.uid == uid }?.name ?: memberFallback })
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(kb.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ids.isEmpty()) Icons.Default.Lock else Icons.Default.People, null, tint = WorkTint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
            Text(stringResource(R.string.work_sharing_hint), fontSize = 12.sp, color = kb.subtitle)
        }
        Icon(Icons.Default.ChevronRight, null, tint = kb.subtitle)
    }
}

// ── Documento in attesa ─────────────────────────────────────────────────

@Composable
private fun StagedCard(staged: WorkStaged, viewModel: WorkViewModel) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(kb.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StagedThumb(staged.file, onRemove = viewModel::discardStaged)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.work_staged_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = kb.title)
                Text(staged.file.name, fontSize = 12.sp, color = kb.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = viewModel::readStaged, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.work_read_document))
        }
        Text(stringResource(R.string.work_cost_hint), fontSize = 12.sp, color = kb.subtitle)
    }
}

/** Anteprima del documento in attesa: la prima pagina per un PDF, la foto per un'immagine. */
@Composable
private fun StagedThumb(file: File, onRemove: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val isPdf = file.extension.equals("pdf", ignoreCase = true)
    val pdfThumb by produceState<Bitmap?>(null, file) {
        if (!isPdf) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val w = 240
                            val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
    }
    Box {
        val shape = RoundedCornerShape(12.dp)
        val thumb = Modifier.size(72.dp).clip(shape).background(kb.surfaceOverlay)
        when {
            isPdf && pdfThumb != null -> Image(pdfThumb!!.asImageBitmap(), null, thumb, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            !isPdf -> AsyncImage(file, null, thumb, contentScale = ContentScale.Crop)
            else -> Box(thumb, contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = kb.subtitle) }
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(28.dp)) {
            Icon(Icons.Default.Cancel, stringResource(R.string.work_remove_attachment), tint = Color(0xBF000000), modifier = Modifier.background(Color.White, CircleShape))
        }
    }
}

// ── Mattoncini ──────────────────────────────────────────────────────────

@Composable
private fun WorkCard(title: String, content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(kb.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = kb.title)
        content()
    }
}

@Composable
private fun Row2(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row2Text(stringResource(labelRes), value)
}

@Composable
private fun Row2Text(label: String, value: String) {
    val kb = MaterialTheme.kidBoxColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = kb.subtitle)
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
    }
}

// ── Condivisione ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkSharingSheet(state: WorkUiState, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val current = state.myProfile?.takeIf { it.visibilityScope == KBVisibilityScope.MEMBERS }
        ?.let { decodeStringList(it.visibilityMemberIdsJson) }.orEmpty()
    var selected by remember { mutableStateOf(current.toSet()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = kb.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.work_sharing_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = kb.title)
            Spacer(Modifier.height(12.dp))
            val others = state.members.filter { it.uid != state.me }
            if (others.isEmpty()) {
                Text(stringResource(R.string.work_sharing_none), fontSize = 14.sp, color = kb.subtitle)
            } else {
                others.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (m.uid in selected) selected - m.uid else selected + m.uid }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.name, fontSize = 15.sp, color = kb.title, modifier = Modifier.weight(1f))
                        Switch(checked = m.uid in selected, onCheckedChange = { on -> selected = if (on) selected + m.uid else selected - m.uid })
                    }
                    HorizontalDivider(color = kb.divider)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.work_sharing_note), fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onConfirm(selected.toList()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.work_save)) }
        }
    }
}

// ── Conferma / modifica ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkConfirmSheet(
    draft: WorkDraftUi,
    onChange: ((WorkDraftUi) -> WorkDraftUi) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val f = draft.fields
    fun setFields(t: (WorkFields) -> WorkFields) = onChange { it.copy(fields = t(it.fields)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = kb.background) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.work_cancel)) }
                    Text(
                        stringResource(kindTitleRes(draft.kind)), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = kb.title,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = onSave) { Text(stringResource(R.string.work_save), fontWeight = FontWeight.Bold) }
                }
            }
            item { Field(R.string.work_f_title, f.title) { v -> setFields { it.copy(title = v) } } }
            item { Field(R.string.work_f_employer, f.employer) { v -> setFields { it.copy(employer = v) } } }
            item { DateField(R.string.work_f_reference_date, draft.referenceDate) { d -> onChange { it.copy(referenceDate = d) } } }
            when (draft.kind) {
                WorkRecordKind.LEAVE_PLAN -> {
                    item { Text(stringResource(R.string.work_periods), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = kb.subtitle) }
                    items(f.leavePeriods, key = { it.id }) { p ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(kb.card).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            fun setP(t: (WorkLeavePeriod) -> WorkLeavePeriod) =
                                setFields { w -> w.copy(leavePeriods = w.leavePeriods.map { if (it.id == p.id) t(it) else it }) }
                            Field(R.string.work_period_type, p.label) { v -> setP { it.copy(label = v) } }
                            DateField(R.string.work_period_from, p.startDay ?: LocalDate.now()) { d ->
                                setP { it.copy(start = d.toString(), end = if ((it.endDay ?: d).isBefore(d)) d.toString() else it.end) }
                            }
                            DateField(R.string.work_period_to, p.endDay ?: p.startDay ?: LocalDate.now()) { d -> setP { it.copy(end = d.toString()) } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.work_add_to_calendar), fontSize = 14.sp, color = kb.title, modifier = Modifier.weight(1f))
                                Switch(checked = p.inCalendar, onCheckedChange = { on -> setP { it.copy(inCalendar = on) } })
                            }
                            TextButton(onClick = { setFields { w -> w.copy(leavePeriods = w.leavePeriods.filter { it.id != p.id }) } }) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.work_remove_period))
                            }
                        }
                    }
                    item {
                        TextButton(onClick = {
                            val today = LocalDate.now().toString()
                            setFields { w ->
                                w.copy(leavePeriods = w.leavePeriods + WorkLeavePeriod(start = today, end = today, label = "", inCalendar = true))
                            }
                        }) { Text(stringResource(R.string.work_add_period)) }
                    }
                    item {
                        Text(
                            stringResource(if (draft.recordId != null) R.string.work_leave_footer_edit else R.string.work_leave_footer_new),
                            fontSize = 12.sp, color = kb.subtitle,
                        )
                    }
                }
                WorkRecordKind.PAYSLIP -> {
                    item { Field(R.string.work_f_period, f.period) { v -> setFields { it.copy(period = v) } } }
                    item { Num(R.string.work_f_gross_eur, f.gross) { v -> setFields { it.copy(gross = v) } } }
                    item { Num(R.string.work_f_net_eur, f.net) { v -> setFields { it.copy(net = v) } } }
                    item { Num(R.string.work_f_taxes_eur, f.taxes) { v -> setFields { it.copy(taxes = v) } } }
                    item { Num(R.string.work_f_contributions_eur, f.contributions) { v -> setFields { it.copy(contributions = v) } } }
                    item { Num(R.string.work_f_tfr_eur, f.tfr) { v -> setFields { it.copy(tfr = v) } } }
                    item { Num(R.string.work_f_leave_accrued_d, f.vacationAccrued) { v -> setFields { it.copy(vacationAccrued = v) } } }
                    item { Num(R.string.work_f_leave_taken_d, f.vacationTaken) { v -> setFields { it.copy(vacationTaken = v) } } }
                    item { Num(R.string.work_f_leave_remaining_d, f.vacationRemaining) { v -> setFields { it.copy(vacationRemaining = v) } } }
                    item { Num(R.string.work_f_permit_accrued_h, f.permitHoursAccrued) { v -> setFields { it.copy(permitHoursAccrued = v) } } }
                    item { Num(R.string.work_f_permit_remaining_h, f.permitHoursRemaining) { v -> setFields { it.copy(permitHoursRemaining = v) } } }
                }
                else -> {
                    item { Field(R.string.work_f_contract_type, f.contractType) { v -> setFields { it.copy(contractType = v) } } }
                    item { Field(R.string.work_f_level, f.level) { v -> setFields { it.copy(level = v) } } }
                    item { Num(R.string.work_f_gross_annual_eur, f.grossAnnual) { v -> setFields { it.copy(grossAnnual = v) } } }
                    item { Num(R.string.work_f_leave_year_d, f.vacationDaysPerYear) { v -> setFields { it.copy(vacationDaysPerYear = v) } } }
                    item { Field(R.string.work_f_notice, f.noticePeriod) { v -> setFields { it.copy(noticePeriod = v) } } }
                    item { OptionalDate(R.string.work_f_start, f.startDate) { v -> setFields { it.copy(startDate = v) } } }
                    item { OptionalDate(R.string.work_f_end, f.endDate) { v -> setFields { it.copy(endDate = v) } } }
                }
            }
            item { Field(R.string.work_f_notes, f.notes) { v -> setFields { it.copy(notes = v) } } }
            item { Text(stringResource(R.string.work_check_values), fontSize = 12.sp, color = kb.subtitle) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun kindTitleRes(kind: String) = when (kind) {
    WorkRecordKind.PAYSLIP -> R.string.work_payslip
    WorkRecordKind.LEAVE_PLAN -> R.string.work_leave_plan
    else -> R.string.work_contract
}

@Composable
private fun Field(labelRes: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) }, singleLine = false,
    )
}

@Composable
private fun Num(labelRes: Int, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value == null) { mutableStateOf(value?.let { trimNum(it) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; onChange(t.replace(',', '.').toDoubleOrNull()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun trimNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(labelRes: Int, date: LocalDate, onChange: (LocalDate) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), fontSize = 14.sp, color = kb.subtitle, modifier = Modifier.weight(1f))
        Text(fmtDay(date), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WorkTint)
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> onChange(Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.work_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.work_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun OptionalDate(labelRes: Int, value: String?, onChange: (String?) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(labelRes), fontSize = 14.sp, color = kb.title, modifier = Modifier.weight(1f))
            Switch(checked = value != null, onCheckedChange = { on -> onChange(if (on) LocalDate.now().toString() else null) })
        }
        if (value != null) {
            DateField(labelRes, WorkFields.parseDay(value) ?: LocalDate.now()) { d -> onChange(d.toString()) }
        }
    }
}

// ── Dettaglio ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkDetailSheet(
    record: KBWorkRecordEntity,
    fields: WorkFields?,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = kb.background) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.work_close)) }
                    Text(
                        stringResource(kindTitleRes(record.kind)), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = kb.title,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (canEdit && fields != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.work_edit), fontWeight = FontWeight.Bold) }
                    else Spacer(Modifier.width(48.dp))
                }
            }
            if (record.attachmentDocumentId != null) {
                item {
                    Button(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.work_open_original))
                    }
                    Text(stringResource(R.string.work_doc_stays_here), fontSize = 12.sp, color = kb.subtitle, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (fields == null) {
                item { Text(stringResource(R.string.work_unreadable), fontSize = 14.sp, color = kb.subtitle) }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(kb.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row2(R.string.work_f_title, fields.title.ifBlank { null })
                        Row2(R.string.work_f_employer, fields.employer.ifBlank { null })
                        Row2(R.string.work_f_period, fields.period.ifBlank { null })
                        Row2(R.string.work_f_gross, fields.gross?.let(::euro))
                        Row2(R.string.work_f_net, fields.net?.let(::euro))
                        Row2(R.string.work_f_taxes, fields.taxes?.let(::euro))
                        Row2(R.string.work_f_contributions, fields.contributions?.let(::euro))
                        Row2(R.string.work_f_tfr, fields.tfr?.let(::euro))
                        Row2(R.string.work_f_leave_accrued, fields.vacationAccrued?.let { days(it) })
                        Row2(R.string.work_f_leave_taken, fields.vacationTaken?.let { days(it) })
                        Row2(R.string.work_f_leave_remaining, fields.vacationRemaining?.let { days(it) })
                        Row2(R.string.work_f_permit_accrued, fields.permitHoursAccrued?.let { hours(it) })
                        Row2(R.string.work_f_permit_remaining, fields.permitHoursRemaining?.let { hours(it) })
                        Row2(R.string.work_f_contract_type, fields.contractType.ifBlank { null })
                        Row2(R.string.work_f_level, fields.level.ifBlank { null })
                        Row2(R.string.work_f_gross_annual, fields.grossAnnual?.let(::euro))
                        Row2(R.string.work_f_leave_year, fields.vacationDaysPerYear?.let { days(it) })
                        Row2(R.string.work_f_start, WorkFields.parseDay(fields.startDate)?.let(::fmtDay))
                        Row2(R.string.work_f_end, WorkFields.parseDay(fields.endDate)?.let(::fmtDay))
                        Row2(R.string.work_f_notice, fields.noticePeriod.ifBlank { null })
                        Row2(R.string.work_f_notes, fields.notes.ifBlank { null })
                    }
                }
                if (fields.leavePeriods.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(kb.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.work_periods), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = kb.title)
                            fields.leavePeriods.forEach { p ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.label.ifBlank { stringResource(R.string.work_leave_default) }, fontSize = 14.sp, color = kb.subtitle, modifier = Modifier.weight(1f))
                                    Text(periodText(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
                                    if (p.inCalendar) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.CalendarMonth, null, tint = kb.subtitle, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (canEdit) {
                item {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.work_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.work_delete_q)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.work_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.work_cancel)) } },
        )
    }
}

// ── Formati ─────────────────────────────────────────────────────────────

private fun euro(v: Double): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { currency = Currency.getInstance("EUR") }.format(v)

private fun num(v: Double): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }.format(v)

@Composable
private fun days(v: Double): String = stringResource(R.string.work_days_fmt, num(v))

@Composable
private fun hours(v: Double): String = stringResource(R.string.work_hours_fmt, num(v))

private fun fmtDay(d: LocalDate): String =
    d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

private fun periodText(p: WorkLeavePeriod): String {
    val s = p.startDay ?: return ""
    val e = p.endDay
    return if (e != null && e.isAfter(s)) "${fmtDay(s)} – ${fmtDay(e)}" else fmtDay(s)
}
