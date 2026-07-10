package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity

/**
 * Contenuto embeddato della sezione "Documenti" del Wallet (usato dentro
 * `WalletHomeScreen`, come tab accanto a "Biglietti"). I controlli
 * Seleziona/+/Elimina vivono nella `TopAppBar` di `WalletHomeScreen` (stessa
 * altezza di quelli di "Biglietti"); questo componente mostra solo lo stack
 * di card sovrapposte e gestisce l'eliminazione singola (tenere premuto).
 */
@Composable
fun WalletDocumentsSectionContent(
    familyId: String,
    onDocumentClick: (documentId: String) -> Unit,
    onUpgrade: () -> Unit,
    showAddSheet: Boolean,
    onShowAddSheetChange: (Boolean) -> Unit,
    showLinkSheet: Boolean,
    onShowLinkSheetChange: (Boolean) -> Unit,
    viewModel: WalletDocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingSingleDelete by remember { mutableStateOf<KBDocumentEntity?>(null) }

    LaunchedEffect(familyId) { viewModel.bind(familyId) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.items.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Acquisisci la Tessera Sanitaria o un altro documento d'identità.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy((-90).dp),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.document.id }) { index, item ->
                    WalletDocumentCard(
                        item = item,
                        isSelectionMode = state.isSelecting,
                        isSelected = state.selectedIds.contains(item.document.id),
                        modifier = Modifier.zIndex(index.toFloat()),
                        onLongClick = if (!state.isSelecting) {
                            { pendingSingleDelete = item.document }
                        } else {
                            null
                        },
                        onClick = {
                            if (state.isSelecting) viewModel.toggleSelection(item.document.id) else onDocumentClick(item.document.id)
                        },
                    )
                }
            }
        }
    }

    pendingSingleDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingSingleDelete = null },
            title = { Text("Elimina documento") },
            text = { Text("\"${doc.title.ifBlank { "Documento" }}\" verrà eliminato. L'operazione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSingle(doc)
                    pendingSingleDelete = null
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSingleDelete = null }) { Text("Annulla") }
            },
        )
    }

    if (showAddSheet) {
        AddWalletDocumentSheet(
            familyId = familyId,
            viewModel = viewModel,
            onUpgrade = onUpgrade,
            onDismiss = { onShowAddSheetChange(false) },
        )
    }
    if (showLinkSheet) {
        LinkExistingWalletDocumentSheet(
            familyId = familyId,
            viewModel = viewModel,
            onUpgrade = onUpgrade,
            onDismiss = { onShowLinkSheetChange(false) },
        )
    }
}
