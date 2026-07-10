@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.launch

/**
 * Modifica di tutti i campi di un documento Wallet già salvato. Layout a
 * schermo intero con sezioni raggruppate (mirror di `EditWalletDocumentSheet`
 * iOS: `Form` con sezioni "Tipo documento" / "Dati", `Annulla`/`Salva` in barra).
 */
@Composable
fun EditWalletDocumentSheet(
    document: KBDocumentEntity,
    metadata: WalletDocumentMetadata?,
    owners: List<WalletDocumentOwner>,
    viewModel: WalletDocumentsViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(metadata?.kind ?: DocumentKind.ALTRO) }
    var title by remember { mutableStateOf(document.title) }
    var owner by remember {
        mutableStateOf(owners.firstOrNull { it.ownerId == document.childId } ?: WalletDocumentOwner.Family)
    }
    var codiceFiscale by remember { mutableStateOf(metadata?.codiceFiscale.orEmpty()) }
    var holderName by remember { mutableStateOf(metadata?.holderName.orEmpty()) }
    var birthInfo by remember { mutableStateOf(metadata?.birthInfo.orEmpty()) }
    var documentNumber by remember { mutableStateOf(metadata?.documentNumber.orEmpty()) }
    var issueDate by remember { mutableStateOf(metadata?.issueDate) }
    var expiryDate by remember { mutableStateOf(metadata?.expiryDate) }
    var patenteCategories by remember { mutableStateOf(metadata?.patenteCategories ?: emptyList()) }
    var notifyBeforeExpiry by remember { mutableStateOf(metadata?.notifyBeforeExpiry ?: true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val cleanTitle = title.trim()
    val isPatente = kind == DocumentKind.PATENTE

    fun save() {
        scope.launch {
            isSaving = true
            val updated = WalletDocumentMetadata(
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
            viewModel.updateExisting(document, owner.ownerId, updated, cleanTitle)
                .onSuccess {
                    isSaving = false
                    onDismiss()
                }
                .onFailure {
                    isSaving = false
                    errorText = it.localizedMessage ?: "Errore di salvataggio."
                }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.kidBoxColors.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.kidBoxColors.background),
                    title = { Text("Modifica documento", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) { Text("Annulla") }
                    },
                    actions = {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).height(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { save() }, enabled = cleanTitle.isNotEmpty()) { Text("Salva") }
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
                FormSection(title = "Tipo documento") {
                    KindDropdown(selected = kind, onSelected = { kind = it })
                    OwnerDropdown(owners = owners, selected = owner, onSelected = { owner = it })
                }

                FormSection(title = "Dati") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titolo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = holderName,
                        onValueChange = { holderName = it },
                        label = { Text("Nome e cognome titolare") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = birthInfo,
                        onValueChange = { birthInfo = it },
                        label = { Text("Data e luogo di nascita") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = documentNumber,
                        onValueChange = { documentNumber = it },
                        label = { Text(if (isPatente) "Numero patente" else "Numero documento") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isPatente) {
                        OutlinedTextField(
                            value = codiceFiscale,
                            onValueChange = { codiceFiscale = it.uppercase() },
                            label = { Text("Codice Fiscale") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DocumentDateField(label = "Data di rilascio", date = issueDate, onChange = { issueDate = it })
                        DocumentDateField(label = "Data di scadenza", date = expiryDate, onChange = { expiryDate = it })
                        if (expiryDate != null) {
                            NotifyRow(notifyBeforeExpiry) { notifyBeforeExpiry = it }
                        }
                    }
                }

                if (isPatente) {
                    FormSection(title = "Categorie") {
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
