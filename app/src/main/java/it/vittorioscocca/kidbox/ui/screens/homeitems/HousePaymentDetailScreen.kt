package it.vittorioscocca.kidbox.ui.screens.homeitems

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.life.HousePaymentDeadlineCalculator
import it.vittorioscocca.kidbox.ui.screens.life.deadlineUrgencyColor
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.housePaymentTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousePaymentDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HousePaymentDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pay by viewModel.payment.collectAsStateWithLifecycle()
    val paymentAttachments by viewModel.paymentAttachments.collectAsStateWithLifecycle()
    val attachmentUploading by viewModel.attachmentUploading.collectAsStateWithLifecycle()
    val openFileEvent by viewModel.openFileEvent.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showKidBoxPicker by remember { mutableStateOf(false) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)

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
    val camFile = remember { File(camDir, "house_payment_cam.jpg") }
    val camUri = remember(camFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", camFile)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.uploadHousePaymentAttachment(camUri)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) takePicture.launch(camUri) else Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        u?.let { viewModel.uploadHousePaymentAttachment(it) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { viewModel.uploadHousePaymentAttachment(it) }
    }
    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(pay?.name ?: "Dettaglio", color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = kb.title)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Modifica") }, onClick = { menuOpen = false; editing = true })
                        DropdownMenuItem(
                            text = { Text("Elimina") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
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
        val p = pay
        if (p == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = kb.card)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(housePaymentTypeLabelIt(p.typeRaw), color = kb.subtitle)
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = kb.title)
                    p.subtypeRaw?.takeIf { it.isNotBlank() }?.let { Text(it, color = kb.subtitle) }
                    p.importo?.let { v ->
                        val cur = NumberFormat.getCurrencyInstance(Locale.ITALY).format(v)
                        Text("Importo: $cur", color = kb.subtitle)
                    }
                    p.fornitore?.takeIf { it.isNotBlank() }?.let { Text("Fornitore: $it", color = kb.subtitle) }
                    p.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = kb.subtitle) }
                }
            }
            Text("Scadenze", fontWeight = FontWeight.SemiBold, color = kb.title)
            p.giornoDiScadenzaMensile?.let { giorno ->
                HousePaymentDeadlineCalculator.nextMonthlyDeadlineOnly(p)?.let { d ->
                    deadlineChip("Prossima scadenza mensile (giorno $giorno)", d)
                }
            }
            p.dataScadenza?.let {
                HousePaymentDeadlineCalculator.nextAnnualDeadlineOnly(p)?.let { d ->
                    deadlineChip("Prossima scadenza annuale", d)
                }
            }
            p.dataScadenzaContratto?.let { c ->
                deadlineChip("Scadenza contratto", c)
            }
            Text("ALLEGATI", fontWeight = FontWeight.SemiBold, color = kb.subtitle, style = MaterialTheme.typography.labelLarge)
            HealthAttachmentsCard(
                attachments = paymentAttachments,
                tintColor = orange,
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

    pay?.familyId?.let { fid ->
        if (showKidBoxPicker && fid.isNotBlank()) {
            KidBoxDocumentPickerSheet(
                familyId = fid,
                onDismiss = { showKidBoxPicker = false },
                onPickedUri = { viewModel.uploadHousePaymentAttachment(it); showKidBoxPicker = false },
            )
        }
    }

    if (editing && pay != null) {
        val base = pay!!
        var name by remember(base.id) { mutableStateOf(base.name) }
        var note by remember(base.id) { mutableStateOf(base.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Modifica") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") })
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") })
                    HealthAttachmentsCard(
                        attachments = paymentAttachments,
                        tintColor = orange,
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateHousePayment(
                            base.copy(name = name.trim(), note = note.trim().takeIf { it.isNotEmpty() }),
                        ) { err -> toast = err }
                        editing = false
                    },
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Annulla") } },
        )
    }

    if (confirmDelete && pay != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare questa voce?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHousePayment(pay!!) { err -> toast = err }
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

@Composable
private fun deadlineChip(label: String, deadlineMillis: Long) {
    val kb = MaterialTheme.kidBoxColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = kb.subtitle, style = MaterialTheme.typography.labelLarge)
        Surface(color = deadlineUrgencyColor(deadlineMillis), shape = RoundedCornerShape(12.dp)) {
            Text(
                formatItDate(deadlineMillis),
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
