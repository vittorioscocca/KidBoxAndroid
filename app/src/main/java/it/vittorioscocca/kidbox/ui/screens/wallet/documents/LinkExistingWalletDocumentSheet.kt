@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.PatenteCategory
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import it.vittorioscocca.kidbox.ui.screens.documents.DocumentsViewModel
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Collega un documento già presente in Documenti (senza duplicarlo): lo si
 * sceglie sfogliando l'albero di cartelle (mirror di `WalletDocumentPickerSheet`
 * iOS, non più un elenco piatto), si esegue OCR/AI sulle sue pagine, si
 * confermano i campi e lo si sposta nella cartella "Documenti d'identità".
 * Layout a schermo intero con sezioni raggruppate, come `EditWalletDocumentSheet`.
 */
@Composable
fun LinkExistingWalletDocumentSheet(
    familyId: String,
    viewModel: WalletDocumentsViewModel,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    documentsViewModel: DocumentsViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showPicker by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<KBDocumentEntity?>(null) }
    var kind by remember { mutableStateOf(DocumentKind.TESSERA_SANITARIA) }
    var owner by remember { mutableStateOf<WalletDocumentOwner>(WalletDocumentOwner.Family) }
    var title by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var codiceFiscale by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var birthInfo by remember { mutableStateOf("") }
    var documentNumber by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var expiryDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var patenteCategories by remember { mutableStateOf<List<PatenteCategory>>(emptyList()) }
    var notifyBeforeExpiry by remember { mutableStateOf(true) }

    val isPatente = kind == DocumentKind.PATENTE

    fun applyExtraction(ext: it.vittorioscocca.kidbox.data.wallet.WalletDocumentExtraction) {
        ext.codiceFiscale?.let { codiceFiscale = it }
        ext.holderName?.let { holderName = it }
        ext.birthInfo?.let { birthInfo = it }
        ext.documentNumber?.let { documentNumber = it }
        ext.issueDate?.let { issueDate = it }
        ext.expiryDate?.let { expiryDate = it }
        if (ext.patenteCategories.isNotEmpty()) patenteCategories = ext.patenteCategories
    }

    fun pick(doc: KBDocumentEntity) {
        picked = doc
        title = doc.title
        showPicker = false
        scope.launch {
            isExtracting = true
            errorText = null
            val bitmaps = withContext(Dispatchers.IO) {
                runCatching { documentsViewModel.preparePreviewFile(doc) }
                    .mapCatching { renderPdfPages(it) }
                    .getOrDefault(emptyList())
            }
            pages = bitmaps
            if (bitmaps.isNotEmpty()) {
                runCatching { viewModel.runLocalExtraction(bitmaps, kind) }
                    .onSuccess { applyExtraction(it) }
                    .onFailure { errorText = it.localizedMessage }
            }
            isExtracting = false
        }
    }

    if (showPicker) {
        WalletDocumentPickerSheet(
            familyId = familyId,
            viewModel = viewModel,
            onSelect = { pick(it) },
            onCancel = { showPicker = false },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.kidBoxColors.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.kidBoxColors.background),
                    title = { Text(stringResource(R.string.wallet_document_link_title), fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.wallet_cancel)) }
                    },
                    actions = {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).height(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = {
                                    val doc = picked ?: return@TextButton
                                    scope.launch {
                                        isSaving = true
                                        val metadata = WalletDocumentMetadata(
                                            kind = kind,
                                            codiceFiscale = codiceFiscale.takeIf { it.isNotBlank() },
                                            holderName = holderName.takeIf { it.isNotBlank() },
                                            birthInfo = birthInfo.takeIf { it.isNotBlank() },
                                            documentNumber = documentNumber.takeIf { it.isNotBlank() },
                                            issueDate = if (!isPatente) issueDate else null,
                                            expiryDate = if (!isPatente) expiryDate else null,
                                            patenteCategories = if (isPatente) patenteCategories else emptyList(),
                                            notifyBeforeExpiry = notifyBeforeExpiry,
                                        )
                                        viewModel.linkExisting(doc, owner.ownerId, metadata)
                                            .onSuccess {
                                                isSaving = false
                                                onDismiss()
                                            }
                                            .onFailure {
                                                isSaving = false
                                                errorText = it.localizedMessage ?: context.getString(R.string.wallet_document_link_error)
                                            }
                                    }
                                },
                                enabled = picked != null && !isExtracting,
                            ) { Text(stringResource(R.string.wallet_save)) }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FormSection(title = stringResource(R.string.wallet_document_section_type)) {
                    KindDropdown(selected = kind, onSelected = { kind = it })
                    OwnerDropdown(owners = state.owners, selected = owner, onSelected = { owner = it })
                }

                FormSection(title = stringResource(R.string.wallet_document_section_document)) {
                    val doc = picked
                    if (doc == null) {
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.wallet_document_choose_from_documents))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                if (doc.mimeType == "application/pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.kidBoxColors.subtitle,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.title.ifBlank { doc.fileName }, style = MaterialTheme.typography.bodyMedium)
                                Text(doc.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.kidBoxColors.subtitle, maxLines = 1)
                            }
                        }
                        TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.wallet_document_change_document)) }
                    }
                }

                FormSection(title = stringResource(R.string.wallet_document_section_data)) {
                    if (isExtracting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.wallet_document_reading_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kidBoxColors.subtitle)
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.wallet_label_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = holderName,
                        onValueChange = { holderName = it },
                        label = { Text(stringResource(R.string.wallet_document_holder_full_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = birthInfo,
                        onValueChange = { birthInfo = it },
                        label = { Text(stringResource(R.string.wallet_document_birth_date_place_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = documentNumber,
                        onValueChange = { documentNumber = it },
                        label = { Text(if (isPatente) stringResource(R.string.wallet_document_license_number_label) else stringResource(R.string.wallet_document_number_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isPatente) {
                        OutlinedTextField(
                            value = codiceFiscale,
                            onValueChange = { codiceFiscale = it.uppercase() },
                            label = { Text(stringResource(R.string.wallet_document_codice_fiscale_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DocumentDateField(label = stringResource(R.string.wallet_document_issue_date_full_label), date = issueDate, onChange = { issueDate = it })
                        DocumentDateField(label = stringResource(R.string.wallet_document_expiry_date_full_label), date = expiryDate, onChange = { expiryDate = it })
                        if (expiryDate != null) {
                            NotifyRow(notifyBeforeExpiry) { notifyBeforeExpiry = it }
                        }
                    }
                }

                if (picked != null) {
                    FormSection(title = stringResource(R.string.wallet_document_section_assisted_reading)) {
                        if (state.currentPlan == KBPlan.MAX) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isExtracting = true
                                        viewModel.runAIExtraction(pages, kind, familyId)
                                            .onSuccess { applyExtraction(it) }
                                            .onFailure { errorText = it.localizedMessage ?: context.getString(R.string.wallet_ai_read_failed) }
                                        isExtracting = false
                                    }
                                },
                                enabled = !isExtracting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(stringResource(R.string.wallet_document_read_with_ai_msg_count, viewModel.estimatedAiMessageCost(pages.size)))
                            }
                        } else {
                            OutlinedButton(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(stringResource(R.string.wallet_read_ai_plan_max))
                            }
                        }
                        Text(
                            stringResource(R.string.wallet_document_ai_assisted_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                    }
                }

                if (isPatente) {
                    FormSection(title = stringResource(R.string.wallet_document_section_categories)) {
                        PatenteCategoriesEditor(categories = patenteCategories, onCategoriesChange = { patenteCategories = it })
                    }
                    FormSection(title = null) {
                        NotifyRow(notifyBeforeExpiry) { notifyBeforeExpiry = it }
                    }
                }

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun renderPdfPages(file: File): List<Bitmap> {
    if (file.extension.lowercase() != "pdf") {
        return listOfNotNull(android.graphics.BitmapFactory.decodeFile(file.absolutePath))
    }
    val bitmaps = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            for (i in 0 until minOf(renderer.pageCount, 2)) {
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
