package it.vittorioscocca.kidbox.ui.screens.expenses

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesHomeScreen(
    familyId: String,
    highlightExpenseId: String? = null,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<KBExpenseEntity?>(null) }
    var showDetail by rememberSaveable { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<KBExpenseEntity?>(null) }
    var pendingAttachment by remember { mutableStateOf<PendingExpenseAttachment?>(null) }
    var selectedKidBoxDocumentId by remember { mutableStateOf<String?>(null) }
    var consumedHighlightExpenseId by rememberSaveable(highlightExpenseId) { mutableStateOf(false) }
    var isSelectingExpenses by rememberSaveable { mutableStateOf(false) }
    var selectedExpenseIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelectedConfirm by rememberSaveable { mutableStateOf(false) }
    var isOpeningAttachment by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(familyId) { viewModel.bindFamily(familyId) }
    LaunchedEffect(highlightExpenseId, state.expenses, consumedHighlightExpenseId) {
        if (consumedHighlightExpenseId) return@LaunchedEffect
        val id = highlightExpenseId ?: return@LaunchedEffect
        val expense = state.expenses.firstOrNull { it.id == id } ?: return@LaunchedEffect
        selectedExpense = expense
        showDetail = true
        consumedHighlightExpenseId = true
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.visibleExpenses, isSelectingExpenses) {
        if (!isSelectingExpenses) return@LaunchedEffect
        val validIds = state.visibleExpenses.map { it.id }.toHashSet()
        selectedExpenseIds = selectedExpenseIds.filterTo(mutableSetOf()) { it in validIds }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        selectedKidBoxDocumentId = null
        pendingAttachment = PendingExpenseAttachment(
            fileName = "ricevuta_${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            bytes = stream.toByteArray(),
        )
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        if (bytes.isNotEmpty()) {
            selectedKidBoxDocumentId = null
            pendingAttachment = PendingExpenseAttachment(
                fileName = "foto_${System.currentTimeMillis()}.jpg",
                mimeType = mime,
                bytes = bytes,
            )
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        if (bytes.isNotEmpty()) {
            val ext = when {
                mime.contains("pdf") -> "pdf"
                mime.contains("png") -> "png"
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                else -> "bin"
            }
            pendingAttachment = PendingExpenseAttachment(
                fileName = "allegato_${System.currentTimeMillis()}.$ext",
                mimeType = mime,
                bytes = bytes,
            )
            selectedKidBoxDocumentId = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                HeaderCircleButton(icon = Icons.Default.Add) {
                    editingExpense = null
                    pendingAttachment = null
                    selectedKidBoxDocumentId = null
                    showEditor = true
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Spese di famiglia",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.kidBoxColors.title,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExpensePeriod.entries.forEach { p ->
                    PeriodPill(
                        label = p.label,
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ExpenseTotalCard(
                total = state.totalAmount,
                count = state.visibleExpenses.size,
            )
            Spacer(Modifier.height(12.dp))
            MonthlyChartCard(bars = state.monthlyBars)
            Spacer(Modifier.height(12.dp))
            CategoryChartCard(slices = state.categorySlices)
            Spacer(Modifier.height(12.dp))
            ExpenseListCard(
                expenses = state.visibleExpenses,
                categories = state.categories,
                selecting = isSelectingExpenses,
                selectedExpenseIds = selectedExpenseIds,
                onToggleSelecting = {
                    isSelectingExpenses = !isSelectingExpenses
                    if (!isSelectingExpenses) selectedExpenseIds = emptySet()
                },
                onToggleExpenseSelection = { expense ->
                    selectedExpenseIds = selectedExpenseIds.toMutableSet().apply {
                        if (!add(expense.id)) remove(expense.id)
                    }
                },
                onDeleteSelected = { showDeleteSelectedConfirm = true },
                onSelect = {
                    selectedExpense = it
                    showDetail = true
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEditor) {
        AddEditExpenseSheet(
            editing = editingExpense,
            categories = state.categories,
            availableDocuments = state.documents,
            availableFolders = state.documentFolders,
            pendingAttachment = pendingAttachment,
            selectedKidBoxDocumentId = selectedKidBoxDocumentId,
            onDismiss = {
                showEditor = false
                pendingAttachment = null
                selectedKidBoxDocumentId = null
            },
            onPickCamera = { cameraLauncher.launch(null) },
            onPickPhoto = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFile = { fileLauncher.launch(arrayOf("*/*")) },
            onPickKidBoxDocument = { docId ->
                selectedKidBoxDocumentId = docId
                pendingAttachment = null
            },
            onRemoveAttachment = {
                selectedKidBoxDocumentId = null
                pendingAttachment = null
            },
            onSave = { title, amount, dateMillis, categoryId, notes ->
                viewModel.saveExpense(
                    editing = editingExpense,
                    title = title,
                    amount = amount,
                    dateEpochMillis = dateMillis,
                    categoryId = categoryId,
                    notes = notes,
                    existingDocumentId = selectedKidBoxDocumentId,
                    attachment = pendingAttachment,
                )
                showEditor = false
                pendingAttachment = null
                selectedKidBoxDocumentId = null
            },
        )
    }

    if (showDetail) {
        selectedExpense?.let { expense ->
            ExpenseDetailSheet(
                expense = expense,
                categories = state.categories,
                isOpeningAttachment = isOpeningAttachment,
                onDismiss = {
                    showDetail = false
                    selectedExpense = null
                },
                onEdit = {
                    editingExpense = expense
                    selectedKidBoxDocumentId = expense.attachedDocumentId
                    showDetail = false
                    showEditor = true
                },
                onDelete = {
                    viewModel.deleteExpense(expense)
                    showDetail = false
                },
                onRemoveAttachment = {
                    viewModel.detachAttachmentFromExpense(expense)
                    selectedExpense = expense.copy(attachedDocumentId = null)
                },
                onOpenAttachment = {
                    scope.launch {
                        isOpeningAttachment = true
                        try {
                            openExpenseAttachmentPreview(
                                context = context,
                                viewModel = viewModel,
                                expense = expense,
                            )
                        } finally {
                            isOpeningAttachment = false
                        }
                    }
                },
            )
        } ?: run {
            showDetail = false
        }
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("Eliminare le spese selezionate?") },
            text = { Text("Questa azione eliminerà ${selectedExpenseIds.size} elementi.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedConfirm = false
                    val toDelete = state.visibleExpenses.filter { it.id in selectedExpenseIds }
                    viewModel.deleteExpenses(toDelete)
                    selectedExpenseIds = emptySet()
                    isSelectingExpenses = false
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("Annulla") }
            },
        )
    }

    if (isOpeningAttachment) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Apro allegato...",
                        modifier = Modifier.padding(start = 10.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.kidBoxColors.title,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseTotalCard(
    total: Double,
    count: Int,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Totale speso", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 18.sp)
                Text(
                    formatEuro(total),
                    color = MaterialTheme.kidBoxColors.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 42.sp,
                )
            }
            Text("$count spese", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 30.sp)
        }
    }
}

@Composable
private fun MonthlyChartCard(bars: List<MonthlyExpenseBar>) {
    val maxValue = (bars.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.kidBoxColors.title, modifier = Modifier.size(18.dp))
                Text(" Andamento mensile", fontWeight = FontWeight.Bold, fontSize = 30.sp, color = MaterialTheme.kidBoxColors.title)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    val ratio = (bar.total / maxValue).toFloat().coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = if (bar.total > 0.0) formatEuroCompact(bar.total) else "",
                            fontSize = 10.sp,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height((ratio * 110).dp.coerceAtLeast(4.dp))
                                .background(Color(0xFF1E88E5), RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = bar.label,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChartCard(slices: List<ExpenseCategorySlice>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.kidBoxColors.title, modifier = Modifier.size(18.dp))
                Text(" Per categoria", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.kidBoxColors.title)
            }
            Spacer(Modifier.height(12.dp))
            val total = slices.sumOf { it.total }.coerceAtLeast(0.001)
            Canvas(modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally)) {
                var start = -90f
                slices.forEach { slice ->
                    val sweep = ((slice.total / total) * 360f).toFloat()
                    drawArc(
                        color = parseHexColor(slice.colorHex),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(10f, 10f),
                        size = Size(size.width - 20f, size.height - 20f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 46f, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Spacer(Modifier.height(10.dp))
            slices.take(4).forEach { slice ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(10.dp).background(parseHexColor(slice.colorHex), CircleShape))
                    Text(
                        text = " ${slice.name}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.kidBoxColors.title,
                    )
                    Text(formatEuro(slice.total), fontWeight = FontWeight.Bold, color = MaterialTheme.kidBoxColors.title)
                }
            }
        }
    }
}

@Composable
private fun ExpenseListCard(
    expenses: List<KBExpenseEntity>,
    categories: List<KBExpenseCategoryEntity>,
    selecting: Boolean,
    selectedExpenseIds: Set<String>,
    onToggleSelecting: () -> Unit,
    onToggleExpenseSelection: (KBExpenseEntity) -> Unit,
    onDeleteSelected: () -> Unit,
    onSelect: (KBExpenseEntity) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tutte le spese", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.kidBoxColors.title)
                Spacer(modifier = Modifier.weight(1f))
                if (selecting && selectedExpenseIds.isNotEmpty()) {
                    Surface(
                        onClick = onDeleteSelected,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFFEBEE),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE35156), modifier = Modifier.size(14.dp))
                            Text(
                                text = " Elimina (${selectedExpenseIds.size})",
                                color = Color(0xFFE35156),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onToggleSelecting) {
                    Text(if (selecting) "Fine" else "Seleziona")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (expenses.isEmpty()) {
                Text("Nessuna spesa nel periodo selezionato", color = MaterialTheme.kidBoxColors.subtitle)
            } else {
                expenses.forEachIndexed { index, expense ->
                    val category = categories.firstOrNull { it.id == expense.categoryId }
                    val iconTint = parseHexColor(category?.colorHex ?: "#9E9E9E")
                    val selected = expense.id in selectedExpenseIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selecting) onToggleExpenseSelection(expense) else onSelect(expense)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selecting) {
                            Icon(
                                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) Color(0xFF1E88E5) else Color(0xFFBDBDBD),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(iconTint.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = categoryIcon(category),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    expense.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.kidBoxColors.title,
                                )
                                if (!expense.attachedDocumentId.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Ha allegato",
                                        tint = Color(0xFF1E88E5),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Text(formatDate(expense.dateEpochMillis), color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                        }
                        Text(formatEuro(expense.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.kidBoxColors.title)
                    }
                    if (index < expenses.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.kidBoxColors.divider),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditExpenseSheet(
    editing: KBExpenseEntity?,
    categories: List<KBExpenseCategoryEntity>,
    availableDocuments: List<KBDocumentEntity>,
    availableFolders: List<KBDocumentCategoryEntity>,
    pendingAttachment: PendingExpenseAttachment?,
    selectedKidBoxDocumentId: String?,
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickKidBoxDocument: (String?) -> Unit,
    onRemoveAttachment: () -> Unit,
    onSave: (title: String, amount: Double, dateEpochMillis: Long, categoryId: String?, notes: String?) -> Unit,
) {
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var amountText by remember(editing?.id) { mutableStateOf(editing?.amount?.let { "%.2f".format(Locale.US, it) } ?: "") }
    var notes by remember(editing?.id) { mutableStateOf(editing?.notes.orEmpty()) }
    var selectedCategoryId by remember(editing?.id) { mutableStateOf(editing?.categoryId) }
    var dateEpochMillis by remember(editing?.id) { mutableStateOf(editing?.dateEpochMillis ?: System.currentTimeMillis()) }
    val context = LocalContext.current
    val date = Instant.ofEpochMilli(dateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleText = if (editing == null) "Nuova spesa" else "Modifica spesa"
    var showKidBoxPicker by remember { mutableStateOf(false) }
    val selectedKidBoxDocument = availableDocuments.firstOrNull { it.id == selectedKidBoxDocumentId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.kidBoxColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleActionButton(label = "Annulla", onClick = onDismiss, enabled = true, modifier = Modifier.width(92.dp))
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.kidBoxColors.title,
                )
                CapsuleActionButton(
                    label = if (editing == null) "Aggiungi" else "Salva",
                    onClick = {
                        val amount = amountText.replace(",", ".").toDoubleOrNull()
                        if (title.isBlank() || amount == null) {
                            Toast.makeText(context, "Inserisci titolo e importo validi", Toast.LENGTH_SHORT).show()
                            return@CapsuleActionButton
                        }
                        onSave(title.trim(), amount, dateEpochMillis, selectedCategoryId, notes.trim().ifEmpty { null })
                    },
                    enabled = true,
                    modifier = Modifier.width(92.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Importo", color = MaterialTheme.kidBoxColors.subtitle)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0,00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Descrizione") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Data", fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.kidBoxColors.rowBackground,
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = dateEpochMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val local = LocalDate.of(y, m + 1, d)
                                        dateEpochMillis = local.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH),
                                ).show()
                            },
                        ) {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ITALIAN)),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = MaterialTheme.kidBoxColors.title,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Categoria", color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryPill(
                            label = "Nessuna",
                            color = Color(0xFF9E9E9E),
                            icon = Icons.Default.MoreHoriz,
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                        )
                        categories.forEach { cat ->
                            CategoryPill(
                                label = cat.name,
                                color = parseHexColor(cat.colorHex),
                                icon = categoryIcon(cat),
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Note", color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Aggiungi una nota...") },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Ricevute e allegati", color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = pendingAttachment?.fileName
                            ?: selectedKidBoxDocument?.fileName
                            ?: "Aggiungi foto, PDF o documenti alla spesa",
                        color = MaterialTheme.kidBoxColors.subtitle,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AttachmentPill(label = "Scatta foto", onClick = onPickCamera)
                        AttachmentPill(label = "Carica foto", onClick = onPickPhoto)
                        AttachmentPill(label = "Carica file", onClick = onPickFile)
                        AttachmentPill(label = "Da Documenti KidBox", onClick = { showKidBoxPicker = true })
                        if (selectedKidBoxDocumentId != null || pendingAttachment != null) {
                            AttachmentPill(label = "Elimina dalla spesa", onClick = onRemoveAttachment)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showKidBoxPicker) {
        KidBoxDocumentPickerSheet(
            documents = availableDocuments,
            folders = availableFolders,
            onDismiss = { showKidBoxPicker = false },
            onSelect = { doc ->
                onPickKidBoxDocument(doc.id)
                showKidBoxPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidBoxDocumentPickerSheet(
    documents: List<KBDocumentEntity>,
    folders: List<KBDocumentCategoryEntity>,
    onDismiss: () -> Unit,
    onSelect: (KBDocumentEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentFolderId by remember { mutableStateOf<String?>(null) }
    val breadcrumbs = remember(currentFolderId, folders) {
        val result = mutableListOf<KBDocumentCategoryEntity>()
        var cursor = currentFolderId
        while (!cursor.isNullOrBlank()) {
            val folder = folders.firstOrNull { it.id == cursor } ?: break
            result.add(folder)
            cursor = folder.parentId
        }
        result.reversed()
    }
    val visibleFolders = remember(currentFolderId, folders) {
        folders.filter { it.parentId == currentFolderId }
    }
    val visibleDocuments = remember(currentFolderId, documents) {
        documents.filter { it.categoryId == currentFolderId }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentFolderId != null) {
                    TextButton(onClick = {
                        currentFolderId = folders.firstOrNull { it.id == currentFolderId }?.parentId
                    }) { Text("Indietro") }
                } else {
                    Spacer(modifier = Modifier.width(68.dp))
                }
                Text(
                    "Seleziona da Documenti KidBox",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.kidBoxColors.title,
                )
                Spacer(modifier = Modifier.width(68.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (breadcrumbs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    Text("Documenti", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                    breadcrumbs.forEach { crumb ->
                        Text(" / ${crumb.title}", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                    }
                }
            }
            if (visibleFolders.isEmpty() && visibleDocuments.isEmpty()) {
                Text("Nessun elemento in questa cartella", color = MaterialTheme.kidBoxColors.subtitle)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleFolders) { folder ->
                        Surface(
                            onClick = { currentFolderId = folder.id },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.kidBoxColors.card,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF0A12B))
                                Text(
                                    text = folder.title,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.kidBoxColors.title,
                                )
                            }
                        }
                    }
                    items(visibleDocuments) { doc ->
                        Surface(
                            onClick = { onSelect(doc) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.kidBoxColors.card,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF1E88E5))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        doc.title.ifBlank { doc.fileName },
                                        color = MaterialTheme.kidBoxColors.title,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(doc.fileName, color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Annulla") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailSheet(
    expense: KBExpenseEntity,
    categories: List<KBExpenseCategoryEntity>,
    isOpeningAttachment: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onOpenAttachment: () -> Unit,
) {
    val category = categories.firstOrNull { it.id == expense.categoryId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onDismiss)
                Text(
                    "Dettaglio spesa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.kidBoxColors.title,
                )
                HeaderCircleButton(icon = Icons.Default.Edit, onClick = onEdit)
            }
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(parseHexColor(category?.colorHex ?: "#E8D7F4"), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(formatEuro(expense.amount), fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = MaterialTheme.kidBoxColors.title)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = parseHexColor(category?.colorHex ?: "#9E9E9E").copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = category?.name ?: "Nessuna",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = parseHexColor(category?.colorHex ?: "#9E9E9E"),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    DetailRow(icon = Icons.Default.Description, label = "Descrizione", value = expense.title)
                    DividerLine()
                    DetailRow(icon = Icons.Default.CalendarMonth, label = "Data", value = formatDate(expense.dateEpochMillis))
                    DividerLine()
                    DetailRow(icon = Icons.Default.LocalOffer, label = "Categoria", value = category?.name ?: "Nessuna")
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF1E88E5))
                        Text(" Allegati", color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!expense.attachedDocumentId.isNullOrBlank()) {
                        Surface(
                            onClick = {
                                if (!isOpeningAttachment) onOpenAttachment()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.kidBoxColors.rowBackground,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF1E88E5))
                                Text(
                                    text = if (isOpeningAttachment) "Caricamento allegato..." else "Documento allegato",
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (isOpeningAttachment) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF1E88E5),
                                    )
                                } else {
                                    Text("Apri", color = Color(0xFF1E88E5), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = onRemoveAttachment,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFEBEE),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE35156), modifier = Modifier.size(16.dp))
                                Text(
                                    text = " Elimina allegato dalla spesa",
                                    color = Color(0xFFE35156),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    } else {
                        Text("Nessun allegato", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Visibili anche in Documenti › Spese", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    DetailMetaRow(label = "Aggiunta il", value = formatDateTime(expense.createdAtEpochMillis))
                    DividerLine()
                    DetailMetaRow(label = "Modificata il", value = formatDateTime(expense.updatedAtEpochMillis))
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { showDeleteConfirm = true },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE35156))
                    Text(" Elimina spesa", color = Color(0xFFE35156), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare questa spesa?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.size(18.dp))
        Text("  $label", color = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.kidBoxColors.title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.kidBoxColors.title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.kidBoxColors.divider),
    )
}

@Composable
private fun CategoryPill(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = if (selected) 0.2f else 0.12f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, color) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = if (selected) 1f else 0.86f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = color.copy(alpha = if (selected) 1f else 0.86f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AttachmentPill(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFEAF3FE),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF1E88E5),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PeriodPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFF1E88E5) else Color.Transparent,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.White else MaterialTheme.kidBoxColors.title,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.kidBoxColors.card,
        modifier = Modifier.size(44.dp),
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

@Composable
private fun CapsuleActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) MaterialTheme.kidBoxColors.card else MaterialTheme.kidBoxColors.divider,
        modifier = modifier.height(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.kidBoxColors.title else Color(0xFFB8B8B8),
            )
        }
    }
}

private suspend fun openExpenseAttachmentPreview(
    context: android.content.Context,
    viewModel: ExpensesViewModel,
    expense: KBExpenseEntity,
) {
    val documentId = expense.attachedDocumentId
    if (documentId.isNullOrBlank()) {
        Toast.makeText(context, "Nessun allegato disponibile", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val prepared = withTimeout(20_000L) {
            viewModel.prepareAttachmentPreviewFile(documentId)
        }
        val (document, file) = prepared
            ?: run {
                Toast.makeText(context, "Allegato non disponibile", Toast.LENGTH_LONG).show()
                return
            }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mime = document.mimeType.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nessuna app disponibile per aprire questo file", Toast.LENGTH_LONG).show()
    } catch (_: TimeoutCancellationException) {
        Toast.makeText(context, "Timeout apertura allegato", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Impossibile aprire allegato: ${e.localizedMessage ?: "errore sconosciuto"}",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun parseHexColor(hex: String): Color {
    val raw = hex.removePrefix("#")
    val value = raw.toLongOrNull(16) ?: 0x9E9E9E
    return if (raw.length <= 6) {
        Color((0xFF000000 or value).toInt())
    } else {
        Color(value.toInt())
    }
}

private fun formatEuro(value: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.ITALY)
    return fmt.format(value)
}

private fun formatEuroCompact(value: Double): String {
    val rounded = kotlin.math.round(value)
    return "${rounded.toInt()}€"
}

private fun formatDate(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN))
}

private fun formatDateTime(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ITALIAN))
}

private fun categoryIcon(category: KBExpenseCategoryEntity?): androidx.compose.ui.graphics.vector.ImageVector {
    val raw = (category?.icon ?: "").lowercase(Locale.ROOT)
    val name = (category?.name ?: "").lowercase(Locale.ROOT)
    return when {
        raw.contains("cart") || name.contains("spesa") -> Icons.Default.LocalGroceryStore
        raw.contains("house") || name.contains("casa") -> Icons.Default.Home
        raw.contains("car") || name.contains("trasport") -> Icons.Default.DirectionsCar
        raw.contains("heart") || name.contains("salute") -> Icons.Default.Favorite
        raw.contains("book") || name.contains("istruz") -> Icons.Default.MenuBook
        raw.contains("run") || name.contains("sport") -> Icons.Default.DirectionsRun
        raw.contains("tshirt") || name.contains("abbigli") -> Icons.Default.Checkroom
        raw.contains("fork") || name.contains("ristor") -> Icons.Default.Restaurant
        raw.contains("game") || name.contains("intratten") -> Icons.Default.SportsEsports
        raw.contains("airplane") || name.contains("viagg") -> Icons.Default.Flight
        raw.contains("desktop") || name.contains("elettron") -> Icons.Default.DesktopWindows
        raw.contains("paw") || name.contains("animal") -> Icons.Default.Pets
        else -> Icons.Default.MoreHoriz
    }
}
