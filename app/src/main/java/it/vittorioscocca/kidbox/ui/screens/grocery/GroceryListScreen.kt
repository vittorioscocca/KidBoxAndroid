package it.vittorioscocca.kidbox.ui.screens.grocery

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBShoppingTripEntity
import it.vittorioscocca.kidbox.domain.model.ShoppingTripLines
import it.vittorioscocca.kidbox.notifications.AppSection
import it.vittorioscocca.kidbox.notifications.TrackSectionPresence
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.launch

private val GroceryGreen = Color(0xFF27AE60)
private val GroceryRed = Color(0xFFE35156)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListScreen(
    onBack: () -> Unit,
    viewModel: GroceryListViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val groceryBg = kb.background
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    TrackSectionPresence(AppSection.SHOPPING_LIST, state.familyId)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<KBGroceryItemEntity?>(null) }
    var showDeletePurchasedAlert by remember { mutableStateOf(false) }
    var showSaveTripSheet by remember { mutableStateOf(false) }
    var showTripsHistory by remember { mutableStateOf(false) }
    val tripFallbackTitle = stringResource(R.string.grocery_trip_fallback_title)

    LaunchedEffect(state.errorMessage) {
        val err = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.clearError()
    }

    Scaffold(
        containerColor = groceryBg,
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.grocery_back),
                            tint = kb.title,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HeaderCircleButton(onClick = { showTripsHistory = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.grocery_trips_title),
                            tint = kb.title,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    HeaderCircleButton(onClick = {
                        editingItem = null
                        showAddDialog = true
                    }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.grocery_add_product),
                            tint = kb.title,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.grocery_title),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                    ),
                    color = kb.title,
                )
                Text(
                    text = stringResource(
                        R.string.grocery_counts,
                        state.toBuy.size,
                        state.purchased.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(10.dp))
                GroceryFilterChips(
                    selected = state.filter,
                    onSelect = { viewModel.setFilter(it) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.grocery_loading))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(groceryBg)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // La scorciatoia sta dove serve: guardando cosa è già stato preso.
                    if (state.filter == GroceryFilter.PURCHASED && state.purchased.isNotEmpty()) {
                        item(key = "save_trip") {
                            Button(
                                onClick = { showSaveTripSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = GroceryGreen,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.grocery_save_trip),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                    }

                    if (state.items.isEmpty()) {
                        item {
                            KBEmptyState(
                                icon = Icons.Filled.ShoppingCart,
                                title = stringResource(R.string.empty_grocery_title),
                                body = stringResource(R.string.empty_grocery_body),
                                primaryIcon = Icons.Filled.AddCircle,
                                primaryLabel = stringResource(R.string.empty_grocery_action),
                                onPrimary = { showAddDialog = true },
                            )
                        }
                    } else if (state.visibleItems.isEmpty()) {
                        // La lista non è vuota: è vuoto questo filtro. Dirlo evita di
                        // far credere che la spesa sia sparita.
                        item {
                            Text(
                                text = if (state.filter == GroceryFilter.PURCHASED) {
                                    stringResource(R.string.grocery_nothing_purchased)
                                } else {
                                    stringResource(R.string.grocery_nothing_to_buy)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = kb.subtitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                            )
                        }
                    }

                    state.groupedVisible.forEach { (category, itemsInCategory) ->
                        item(key = "header_$category") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = groceryCategoryLabel(category),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = kb.title,
                                )
                                Spacer(Modifier.weight(1f))
                                if (state.filter == GroceryFilter.PURCHASED) {
                                    TextButton(onClick = { showDeletePurchasedAlert = true }) {
                                        Text(stringResource(R.string.grocery_delete_all), color = GroceryRed)
                                    }
                                }
                            }
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
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        GroceryEditDialog(
            initialItem = editingItem,
            onDismiss = { showAddDialog = false },
            onSave = { name, category, notes, quantity ->
                if (editingItem == null) {
                    viewModel.addItem(name, category, notes, quantity)
                } else {
                    viewModel.updateItem(
                        itemId = editingItem!!.id,
                        name = name,
                        category = category,
                        notes = notes,
                        quantity = quantity,
                    )
                }
                showAddDialog = false
            },
        )
    }

    if (showSaveTripSheet) {
        SaveShoppingTripSheet(
            purchasedItems = state.purchased,
            isSaving = state.isSavingTrip,
            onDismiss = { showSaveTripSheet = false },
            onSave = { store, total, dateEpochMillis ->
                viewModel.saveTrip(
                    storeName = store,
                    total = total,
                    dateEpochMillis = dateEpochMillis,
                    fallbackTitle = tripFallbackTitle,
                    onSaved = { showSaveTripSheet = false },
                )
            },
        )
    }

    if (showTripsHistory) {
        ShoppingTripsSheet(
            trips = state.trips,
            onDismiss = { showTripsHistory = false },
            onDelete = { viewModel.deleteTrip(it.id) },
        )
    }

    if (showDeletePurchasedAlert) {
        AlertDialog(
            onDismissRequest = { showDeletePurchasedAlert = false },
            title = { Text(stringResource(R.string.grocery_delete_bought)) },
            text = { Text(stringResource(R.string.grocery_delete_bought_q)) },
            confirmButton = {
                Button(onClick = {
                    showDeletePurchasedAlert = false
                    scope.launch { viewModel.deleteAllPurchased() }
                }) { Text(stringResource(R.string.life_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePurchasedAlert = false }) { Text(stringResource(R.string.life_cancel)) }
            },
        )
    }
}

@Composable
private fun GroceryFilterChips(
    selected: GroceryFilter,
    onSelect: (GroceryFilter) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GroceryFilter.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) GroceryGreen else kb.card,
                onClick = { onSelect(option) },
            ) {
                Text(
                    text = stringResource(
                        when (option) {
                            GroceryFilter.ALL -> R.string.grocery_filter_all
                            GroceryFilter.TO_BUY -> R.string.grocery_filter_to_buy
                            GroceryFilter.PURCHASED -> R.string.grocery_filter_purchased
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) Color.White else kb.subtitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun GroceryGroupCard(
    items: List<KBGroceryItemEntity>,
    onToggle: (KBGroceryItemEntity) -> Unit,
    onClick: (KBGroceryItemEntity) -> Unit,
    onDelete: (KBGroceryItemEntity) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
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
                            .background(kb.divider),
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
    val kb = MaterialTheme.kidBoxColors
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
                    contentDescription = stringResource(R.string.grocery_mark_bought),
                    tint = GroceryGreen,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Transparent, CircleShape)
                        .border(width = 2.dp, color = kb.title, shape = CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isPurchased) kb.subtitle else MaterialTheme.colorScheme.onSurface,
            )
            // Quantità e note nella stessa riga: sono entrambe dettagli del
            // prodotto, e due righe separate spezzerebbero la scheda.
            val detail = groceryDetailLine(item)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = kb.subtitle,
                    maxLines = 1,
                )
            }
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.life_delete), color = GroceryRed) }
    }
}

/** "x 3", "x 3 · senza glutine", "senza glutine" o niente. */
private fun groceryDetailLine(item: KBGroceryItemEntity): String? {
    val parts = buildList {
        item.quantity?.takeIf { it > 1 }?.let { add("x $it") }
        item.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryEditDialog(
    initialItem: KBGroceryItemEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String?, notes: String?, quantity: Int?) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var name by remember(initialItem?.id) { mutableStateOf(initialItem?.name.orEmpty()) }
    var category by remember(initialItem?.id) { mutableStateOf(initialItem?.category.orEmpty()) }
    var notes by remember(initialItem?.id) { mutableStateOf(initialItem?.notes.orEmpty()) }
    var quantity by remember(initialItem?.id) {
        mutableStateOf((initialItem?.quantity ?: 1).coerceIn(1, 99))
    }
    val categories = GroceryCategory.entries
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kb.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    text = stringResource(R.string.life_cancel),
                    onClick = onDismiss,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (initialItem == null) stringResource(R.string.grocery_new_product) else stringResource(R.string.grocery_edit_product),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = stringResource(R.string.life_save),
                    onClick = {
                        onSave(
                            name.trim(),
                            category.trim().takeIf { it.isNotEmpty() },
                            notes.trim().takeIf { it.isNotEmpty() },
                            quantity.takeIf { it > 1 },
                        )
                    },
                    enabled = name.trim().isNotEmpty(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.grocery_product), style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            AppleTextField(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.grocery_product_name))

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.grocery_quantity), style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.grocery_quantity),
                        style = MaterialTheme.typography.bodyLarge,
                        color = kb.title,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { quantity = (quantity - 1).coerceAtLeast(1) },
                        enabled = quantity > 1,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "−", tint = kb.title)
                    }
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = kb.title,
                    )
                    IconButton(
                        onClick = { quantity = (quantity + 1).coerceAtMost(99) },
                        enabled = quantity < 99,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+", tint = kb.title)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.life_category), style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
            ) {
                val knownCategory = GroceryCategory.fromStored(category)
                AppleTextField(
                    value = if (knownCategory != null) stringResource(knownCategory.labelRes) else category,
                    onValueChange = { category = it },
                    placeholder = stringResource(R.string.grocery_cat_hint),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(10.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(kb.divider),
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it.name }) { cat ->
                        val isSelected = GroceryCategory.fromStored(category) == cat
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) kb.title else kb.background,
                            modifier = Modifier.clickable { category = cat.key },
                        ) {
                            Text(
                                text = stringResource(cat.labelRes),
                                color = if (isSelected) kb.card else kb.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.grocery_notes_optional), style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            AppleTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = stringResource(R.string.grocery_notes_hint),
                minLines = 3,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * "Salva spesa": archivia quello che è stato preso come uno scontrino, e ne crea
 * la spesa corrispondente nella sezione Spese.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveShoppingTripSheet(
    purchasedItems: List<KBGroceryItemEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (storeName: String, total: Double, dateEpochMillis: Long) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var storeName by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var dateEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val total = totalText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kb.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(text = stringResource(R.string.life_cancel), onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.grocery_save_trip),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = stringResource(R.string.life_save),
                    onClick = { total?.let { onSave(storeName, it, dateEpochMillis) } },
                    enabled = total != null && purchasedItems.isNotEmpty() && !isSaving,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.grocery_trip_store), style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            AppleTextField(
                value = storeName,
                onValueChange = { storeName = it },
                placeholder = stringResource(R.string.grocery_trip_store_hint),
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.grocery_trip_total), style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            AppleTextField(
                value = totalText,
                onValueChange = { totalText = it },
                placeholder = "0,00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.grocery_trip_date),
                        fontWeight = FontWeight.SemiBold,
                        color = kb.title,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = kb.background,
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = dateEpochMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    dateEpochMillis = LocalDate.of(y, m + 1, d)
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        },
                    ) {
                        Text(
                            text = formatTripDate(dateEpochMillis),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = kb.title,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.grocery_trip_products_taken, purchasedItems.size),
                style = MaterialTheme.typography.titleMedium.copy(color = kb.subtitle, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    purchasedItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.name, color = kb.title, modifier = Modifier.weight(1f))
                            item.quantity?.takeIf { it > 1 }?.let {
                                Text("x $it", color = kb.subtitle)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // Detto prima di salvare, non scoperto dopo.
            Text(
                stringResource(R.string.grocery_trip_save_note),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Lo storico delle spese fatte, con il dettaglio dello scontrino. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingTripsSheet(
    trips: List<KBShoppingTripEntity>,
    onDismiss: () -> Unit,
    onDelete: (KBShoppingTripEntity) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedTripId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kb.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.grocery_trips_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = kb.title,
                )
                Spacer(Modifier.weight(1f))
                PillButton(text = stringResource(R.string.grocery_close), onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))

            if (trips.isEmpty()) {
                // Stato vuoto senza azione: da qui non si crea uno scontrino,
                // nasce dai prodotti spuntati nella lista.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = kb.subtitle,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.grocery_trips_empty_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = kb.title,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.grocery_trips_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kb.subtitle,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(trips, key = { it.id }) { trip ->
                        ShoppingTripCard(
                            trip = trip,
                            isExpanded = expandedTripId == trip.id,
                            onToggle = {
                                expandedTripId = if (expandedTripId == trip.id) null else trip.id
                            },
                            onDelete = { onDelete(trip) },
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ShoppingTripCard(
    trip: KBShoppingTripEntity,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val fallbackTitle = stringResource(R.string.grocery_trip_fallback_title)
    val lines = remember(trip.linesJson) { ShoppingTripLines.decode(trip.linesJson) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GroceryGreen.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = GroceryGreen)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trip.storeName?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = kb.title,
                    )
                    Text(
                        text = formatTripDate(trip.dateEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = kb.subtitle,
                    )
                }
                Text(
                    text = formatTripTotal(trip.total),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = kb.title,
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(kb.divider),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.grocery_trip_products, lines.size),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(6.dp))
                if (lines.isEmpty()) {
                    Text(
                        stringResource(R.string.grocery_trip_no_products),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kb.subtitle,
                    )
                } else {
                    lines.forEach { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Text(line.name, color = kb.title, modifier = Modifier.weight(1f))
                            line.quantity?.takeIf { it > 1 }?.let {
                                Text("x $it", color = kb.subtitle)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trip.linkedExpenseId != null) {
                        Text(
                            stringResource(R.string.grocery_trip_linked_expense),
                            style = MaterialTheme.typography.bodySmall,
                            color = kb.subtitle,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.life_delete), color = GroceryRed)
                    }
                }
            }
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
        color = MaterialTheme.kidBoxColors.card,
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
    val kb = MaterialTheme.kidBoxColors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) kb.background else kb.divider,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) kb.title else kb.subtitle,
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val kb = MaterialTheme.kidBoxColors
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = kb.subtitle) },
        modifier = modifier,
        minLines = minLines,
        shape = shape,
        keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = kb.card,
            unfocusedContainerColor = kb.card,
            disabledContainerColor = kb.card,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = kb.title,
            unfocusedTextColor = kb.title,
            focusedPlaceholderColor = kb.subtitle,
            unfocusedPlaceholderColor = kb.subtitle,
        ),
    )
}

private fun formatTripDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", KBLocale.current()))

private fun formatTripTotal(value: Double): String =
    NumberFormat.getCurrencyInstance(KBLocale.current()).format(value)
