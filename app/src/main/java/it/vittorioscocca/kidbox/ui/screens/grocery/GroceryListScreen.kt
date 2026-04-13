package it.vittorioscocca.kidbox.ui.screens.grocery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import kotlinx.coroutines.launch

private val GroceryBg = Color(0xFFF5F4F1)
private val GroceryCard = Color.White
private val GrocerySection = Color(0xFF6F6F73)
private val GroceryTextMuted = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListScreen(
    onBack: () -> Unit,
    viewModel: GroceryListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<KBGroceryItemEntity?>(null) }
    var showDeletePurchasedAlert by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        val err = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.clearError()
    }

    Scaffold(
        containerColor = GroceryBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderCircleButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                    Spacer(Modifier.weight(1f))
                    HeaderCircleButton(onClick = {
                        editingItem = null
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi prodotto")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Spesa",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                    ),
                )
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Caricamento lista...")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GroceryBg)
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.items.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = GroceryCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        ) {
                            Text(
                                text = "Lista vuota",
                                color = GroceryTextMuted,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            )
                        }
                    }
                }

                state.groupedToBuy.forEach { (category, itemsInCategory) ->
                    item(key = "header_$category") {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = GrocerySection,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                    }
                    item(key = "group_$category") {
                        GroceryGroupCard(
                            items = itemsInCategory,
                            onToggle = { viewModel.togglePurchased(it.id) },
                            onClick = { item ->
                                editingItem = item
                                showAddDialog = true
                            },
                            onDelete = { viewModel.deleteItem(it.id) },
                        )
                    }
                }

                if (state.purchased.isNotEmpty()) {
                    item(key = "header_purchased") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Acquistati (${state.purchased.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = GrocerySection,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showDeletePurchasedAlert = true }) {
                                Text("Elimina tutti", color = Color.Red)
                            }
                        }
                    }
                    item(key = "group_purchased") {
                        GroceryGroupCard(
                            items = state.purchased,
                            onToggle = { viewModel.togglePurchased(it.id) },
                            onClick = { item ->
                                editingItem = item
                                showAddDialog = true
                            },
                            onDelete = { viewModel.deleteItem(it.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showAddDialog) {
        GroceryEditDialog(
            initialItem = editingItem,
            onDismiss = { showAddDialog = false },
            onSave = { name, category, notes ->
                if (editingItem == null) {
                    viewModel.addItem(name, category, notes)
                } else {
                    viewModel.updateItem(
                        itemId = editingItem!!.id,
                        name = name,
                        category = category,
                        notes = notes,
                    )
                }
                showAddDialog = false
            },
        )
    }

    if (showDeletePurchasedAlert) {
        AlertDialog(
            onDismissRequest = { showDeletePurchasedAlert = false },
            title = { Text("Elimina acquistati") },
            text = { Text("Vuoi eliminare tutti i prodotti già acquistati?") },
            confirmButton = {
                Button(onClick = {
                    showDeletePurchasedAlert = false
                    scope.launch { viewModel.deleteAllPurchased() }
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePurchasedAlert = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun GroceryGroupCard(
    items: List<KBGroceryItemEntity>,
    onToggle: (KBGroceryItemEntity) -> Unit,
    onClick: (KBGroceryItemEntity) -> Unit,
    onDelete: (KBGroceryItemEntity) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GroceryCard),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { idx, item ->
                GroceryRow(
                    item = item,
                    onToggle = { onToggle(item) },
                    onClick = { onClick(item) },
                    onDelete = { onDelete(item) },
                )
                if (idx < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 20.dp)
                            .background(Color(0xFFE7E7EA)),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroceryRow(
    item: KBGroceryItemEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            if (item.isPurchased) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Segna acquistato",
                    tint = Color(0xFF27AE60),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Transparent, CircleShape)
                        .border(width = 2.dp, color = Color(0xFF1C1C1E), shape = CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isPurchased) GroceryTextMuted else Color(0xFF1C1C1E),
            )
            if (!item.notes.isNullOrBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = GroceryTextMuted,
                    maxLines = 1,
                )
            }
        }
        TextButton(onClick = onDelete) { Text("Elimina", color = Color.Red) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryEditDialog(
    initialItem: KBGroceryItemEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String?, notes: String?) -> Unit,
) {
    var name by remember(initialItem?.id) { mutableStateOf(initialItem?.name.orEmpty()) }
    var category by remember(initialItem?.id) { mutableStateOf(initialItem?.category.orEmpty()) }
    var notes by remember(initialItem?.id) { mutableStateOf(initialItem?.notes.orEmpty()) }
    val categories = listOf(
        "Frutta e Verdura",
        "Carne e Pesce",
        "Latticini",
        "Pane e Cereali",
        "Surgelati",
        "Bevande",
        "Altro",
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFFEFEFF3),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    text = "Annulla",
                    onClick = onDismiss,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (initialItem == null) "Nuovo prodotto" else "Modifica prodotto",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = "Salva",
                    onClick = {
                        onSave(
                            name.trim(),
                            category.trim().takeIf { it.isNotEmpty() },
                            notes.trim().takeIf { it.isNotEmpty() },
                        )
                    },
                    enabled = name.trim().isNotEmpty(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Prodotto", style = MaterialTheme.typography.titleMedium.copy(color = GrocerySection, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            AppleTextField(value = name, onValueChange = { name = it }, placeholder = "Nome prodotto")

            Spacer(Modifier.height(16.dp))
            Text("Categoria", style = MaterialTheme.typography.titleMedium.copy(color = GrocerySection, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = GroceryCard),
            ) {
                AppleTextField(
                    value = category,
                    onValueChange = { category = it },
                    placeholder = "Es. Frutta e Verdura",
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(10.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE0E0E3)),
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (category == cat) Color(0xFF111111) else Color(0xFFF1F1F3),
                            modifier = Modifier.clickable { category = cat },
                        ) {
                            Text(
                                text = cat,
                                color = if (category == cat) Color.White else Color(0xFF2A2A2A),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Note (opzionale)", style = MaterialTheme.typography.titleMedium.copy(color = GrocerySection, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            AppleTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Es. marca preferita, quantità...",
                minLines = 3,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeaderCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) Color(0xFFF4F4F6) else Color(0xFFE5E5E8),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Color(0xFF1C1C1E) else Color(0xFFB0B0B5),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun AppleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(22.dp),
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFB0B0B5)) },
        modifier = modifier,
        minLines = minLines,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Color(0xFF1C1C1E),
            unfocusedTextColor = Color(0xFF1C1C1E),
            focusedPlaceholderColor = Color(0xFFB0B0B5),
            unfocusedPlaceholderColor = Color(0xFFB0B0B5),
        ),
    )
}
