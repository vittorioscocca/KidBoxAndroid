@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.repository.DocumentBrowserData
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private data class FolderLevel(val id: String?, val title: String)

/**
 * Sfoglia l'albero di cartelle/documenti della sezione "Documenti" per
 * scegliere un file esistente da collegare al Wallet, senza duplicarlo.
 * Mirror di `WalletDocumentPickerSheet` (iOS): stack di navigazione a
 * cartelle, sezioni "Cartelle"/"Documenti".
 */
@Composable
fun WalletDocumentPickerSheet(
    familyId: String,
    viewModel: WalletDocumentsViewModel,
    onSelect: (KBDocumentEntity) -> Unit,
    onCancel: () -> Unit,
) {
    val rootTitle = stringResource(R.string.wallet_document_documents_root_title)
    val stack = remember { mutableStateListOf(FolderLevel(id = null, title = rootTitle)) }
    val current = stack.last()
    val data by viewModel.browse(familyId, current.id).collectAsStateWithLifecycle(
        initialValue = DocumentBrowserData(emptyList(), emptyList()),
    )

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.kidBoxColors.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.kidBoxColors.background),
                    title = { Text(current.title, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    navigationIcon = {
                        if (stack.size > 1) {
                            IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wallet_back))
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.wallet_cancel)) }
                    },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (data.folders.isEmpty() && data.documents.isEmpty()) {
                    Text(
                        stringResource(R.string.wallet_document_no_documents_in_folder),
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        color = MaterialTheme.kidBoxColors.subtitle,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (data.folders.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.wallet_document_section_folders))
                            }
                            items(data.folders, key = { it.id }) { folder ->
                                FolderRow(folder) { stack.add(FolderLevel(id = folder.id, title = folder.title)) }
                                HorizontalDivider()
                            }
                        }
                        if (data.documents.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.wallet_document_section_documents_header))
                            }
                            items(data.documents, key = { it.id }) { doc ->
                                DocumentRow(doc) { onSelect(doc) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.kidBoxColors.subtitle,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FolderRow(folder: KBDocumentCategoryEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.title) },
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.kidBoxColors.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DocumentRow(document: KBDocumentEntity, onClick: () -> Unit) {
    val isPdf = document.mimeType == "application/pdf"
    ListItem(
        headlineContent = { Text(document.title.ifBlank { document.fileName }) },
        supportingContent = { Text(document.fileName, maxLines = 1) },
        leadingContent = {
            Icon(
                if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.kidBoxColors.subtitle,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.kidBoxColors.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
