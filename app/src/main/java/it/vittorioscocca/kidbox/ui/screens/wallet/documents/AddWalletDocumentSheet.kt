@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.PatenteCategory
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import kotlinx.coroutines.launch

/**
 * Scansione + acquisizione di un nuovo documento d'identità: scanner di sistema,
 * estrazione automatica locale (OCR/barcode) con opzione di lettura assistita AI
 * (solo piano Max), campi editabili, salvataggio nella cartella "Documenti d'identità".
 */
@Composable
fun AddWalletDocumentSheet(
    familyId: String,
    viewModel: WalletDocumentsViewModel,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(DocumentKind.TESSERA_SANITARIA) }
    var owner by remember { mutableStateOf<WalletDocumentOwner>(WalletDocumentOwner.Family) }
    var title by remember { mutableStateOf(kind.displayName) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var codiceFiscale by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var birthInfo by remember { mutableStateOf("") }
    var documentNumber by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var expiryDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var patenteCategories by remember { mutableStateOf<List<PatenteCategory>>(emptyList()) }
    var notifyBeforeExpiry by remember { mutableStateOf(true) }

    fun applyExtraction(ext: it.vittorioscocca.kidbox.data.wallet.WalletDocumentExtraction) {
        ext.codiceFiscale?.let { codiceFiscale = it }
        ext.holderName?.let { holderName = it }
        ext.birthInfo?.let { birthInfo = it }
        ext.documentNumber?.let { documentNumber = it }
        ext.issueDate?.let { issueDate = it }
        ext.expiryDate?.let { expiryDate = it }
        if (ext.patenteCategories.isNotEmpty()) patenteCategories = ext.patenteCategories
    }

    val launchScanner = rememberWalletDocumentScannerLauncher(
        pageLimit = if (kind == DocumentKind.PASSAPORTO) 3 else 2,
        onResult = { result ->
            pages = result.pages
            pdfBytes = result.pdfBytes
            scope.launch {
                isExtracting = true
                errorText = null
                runCatching { viewModel.runLocalExtraction(result.pages, kind) }
                    .onSuccess { applyExtraction(it) }
                    .onFailure { errorText = it.localizedMessage }
                isExtracting = false
            }
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.wallet_document_new_document_title), style = MaterialTheme.typography.titleLarge)

            KindDropdown(selected = kind, onSelected = { kind = it; title = it.displayName })
            OwnerDropdown(owners = state.owners, selected = owner, onSelected = { owner = it })

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.wallet_label_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (pages.isEmpty()) {
                val scanLabel = if (kind != DocumentKind.CODICE_FISCALE) stringResource(R.string.wallet_document_scan_both_sides) else stringResource(R.string.wallet_document_scan_single)
                Button(onClick = { launchScanner() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(scanLabel)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pages) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(120.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launchScanner() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.wallet_document_rescan))
                    }
                    if (state.currentPlan == KBPlan.MAX) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isExtracting = true
                                    errorText = null
                                    viewModel.runAIExtraction(pages, kind, familyId)
                                        .onSuccess { applyExtraction(it) }
                                        .onFailure { errorText = it.localizedMessage ?: context.getString(R.string.wallet_ai_read_failed) }
                                    isExtracting = false
                                }
                            },
                            enabled = !isExtracting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.wallet_document_read_with_ai_count, viewModel.estimatedAiMessageCost(pages.size)))
                        }
                    } else {
                        OutlinedButton(onClick = onUpgrade, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Leggi con AI (Max)")
                        }
                    }
                }
            }

            if (isExtracting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.wallet_document_extracting), style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            if (kind != DocumentKind.PATENTE) {
                OutlinedTextField(
                    value = codiceFiscale,
                    onValueChange = { codiceFiscale = it.uppercase() },
                    label = { Text(stringResource(R.string.wallet_document_codice_fiscale_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text(stringResource(R.string.wallet_document_full_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = birthInfo,
                onValueChange = { birthInfo = it },
                label = { Text(stringResource(R.string.wallet_document_birth_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind != DocumentKind.CODICE_FISCALE) {
                OutlinedTextField(
                    value = documentNumber,
                    onValueChange = { documentNumber = it },
                    label = { Text(stringResource(R.string.wallet_document_number_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (kind == DocumentKind.PATENTE) {
                PatenteCategoriesEditor(categories = patenteCategories, onCategoriesChange = { patenteCategories = it })
            } else if (kind != DocumentKind.CODICE_FISCALE) {
                DocumentDateField(label = stringResource(R.string.wallet_document_issue_date_label), date = issueDate, onChange = { issueDate = it })
                DocumentDateField(label = stringResource(R.string.wallet_document_expiry_date_label), date = expiryDate, onChange = { expiryDate = it })
            }

            NotifyRow(notifyBeforeExpiry) { notifyBeforeExpiry = it }

            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val bytes = pdfBytes
                    if (bytes == null) {
                        errorText = context.getString(R.string.wallet_document_scan_first_error)
                        return@Button
                    }
                    scope.launch {
                        isSaving = true
                        val metadata = WalletDocumentMetadata(
                            kind = kind,
                            codiceFiscale = codiceFiscale.takeIf { it.isNotBlank() },
                            holderName = holderName.takeIf { it.isNotBlank() },
                            birthInfo = birthInfo.takeIf { it.isNotBlank() },
                            documentNumber = documentNumber.takeIf { it.isNotBlank() },
                            issueDate = issueDate,
                            expiryDate = expiryDate,
                            patenteCategories = patenteCategories,
                            notifyBeforeExpiry = notifyBeforeExpiry,
                        )
                        viewModel.saveNewDocument(
                            familyId = familyId,
                            ownerId = owner.ownerId,
                            title = title,
                            fileName = "${kind.raw}.pdf",
                            mimeType = "application/pdf",
                            bytes = bytes,
                            metadata = metadata,
                        ).onSuccess {
                            isSaving = false
                            onDismiss()
                        }.onFailure {
                            isSaving = false
                            errorText = it.localizedMessage ?: context.getString(R.string.wallet_document_save_error)
                        }
                    }
                },
                enabled = !isSaving && !isExtracting && pdfBytes != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.wallet_document_save_button))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
