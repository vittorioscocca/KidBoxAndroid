package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import androidx.compose.material.icons.filled.AddCircle

/**
 * Contenuto del terzo tab "Carte" del Wallet: griglia 2 colonne di carte
 * fedeltà e ricerca. I controlli Seleziona/+/Elimina vivono nella `TopAppBar`
 * di `WalletHomeScreen` (stessa disposizione della sezione "Documenti"): qui
 * arrivano solo gli stati sollevati (`showAddFlow`, `showDeleteConfirm`).
 * Mirror Compose di `LoyaltyCardsSectionView` (iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaltyCardsSectionContent(
    familyId: String,
    onCardClick: (cardId: String) -> Unit,
    showAddFlow: Boolean,
    onShowAddFlowChange: (Boolean) -> Unit,
    showDeleteConfirm: Boolean,
    onShowDeleteConfirmChange: (Boolean) -> Unit,
    viewModel: LoyaltyCardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(familyId) { viewModel.bind(familyId) }

    // La selezione si azzera quando cambia il filtro di ricerca: altrimenti si
    // finirebbe per eliminare carte non più visibili.
    LaunchedEffect(searchText) { viewModel.clearSelection() }

    val visibleCards = remember(state.cards, searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) state.cards else state.cards.filter { it.brandName.contains(q, ignoreCase = true) }
    }

    if (showAddFlow) {
        AddLoyaltyCardFlow(
            viewModel = viewModel,
            onDismiss = { onShowAddFlowChange(false) },
            onSaved = { cardId ->
                onShowAddFlowChange(false)
                onCardClick(cardId)
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // La barra di ricerca ha senso solo con almeno una carta: senza, resta il solo empty state.
        if (!state.isLoading && state.cards.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.wallet_loyalty_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.cards.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyLoyaltyCardsState(onAddCard = { onShowAddFlowChange(true) })
            }
            return@Column
        }

        if (visibleCards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.wallet_loyalty_no_results, searchText),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(visibleCards, key = { it.id }) { card ->
                LoyaltyCardTile(
                    card = card,
                    isSelecting = state.isSelecting,
                    isSelected = state.selectedIds.contains(card.id),
                    modifier = Modifier.clickable {
                        if (state.isSelecting) viewModel.toggleSelected(card.id) else onCardClick(card.id)
                    },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onShowDeleteConfirmChange(false) },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.wallet_loyalty_delete_selected_confirm_title,
                        state.selectedIds.size,
                        state.selectedIds.size,
                    ),
                )
            },
            text = { Text(stringResource(R.string.wallet_loyalty_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        onShowDeleteConfirmChange(false)
                        viewModel.deleteSelectedCards()
                    },
                ) { Text(stringResource(R.string.location_delete_content_description)) }
            },
            dismissButton = {
                TextButton(onClick = { onShowDeleteConfirmChange(false) }) {
                    Text(stringResource(R.string.location_cancel_button))
                }
            },
        )
    }
}

@Composable
private fun EmptyLoyaltyCardsState(onAddCard: () -> Unit) {
    KBEmptyState(
        icon = Icons.Filled.CreditCard,
        title = stringResource(R.string.empty_loyalty_title),
        body = stringResource(R.string.empty_loyalty_body),
        primaryIcon = Icons.Filled.AddCircle,
        primaryLabel = stringResource(R.string.empty_loyalty_action),
        onPrimary = onAddCard,
    )
}
