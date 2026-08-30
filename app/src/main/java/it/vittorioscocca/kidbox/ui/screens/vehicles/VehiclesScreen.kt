package it.vittorioscocca.kidbox.ui.screens.vehicles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.vehicles.VehicleReminderOffsets
import it.vittorioscocca.kidbox.ui.screens.life.deadlineUrgencyColor
import it.vittorioscocca.kidbox.ui.screens.life.earliestNonNull
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import it.vittorioscocca.kidbox.ui.components.KBSectionHeader
import androidx.compose.material.icons.filled.AddCircle
import it.vittorioscocca.kidbox.ui.screens.life.vehicleFuelLabel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.components.KidBoxFormPage
import it.vittorioscocca.kidbox.ui.components.FormSectionTitle
import it.vittorioscocca.kidbox.ui.components.FormSectionHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VehiclesScreen(
    onNavigateBack: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel(),
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<VehicleEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)

    Scaffold(
        containerColor = kb.background,
        topBar = {
            KBSectionHeader(
                title = stringResource(R.string.vehicles_garage),
                onBack = onNavigateBack,
                onAdd = { showAdd = true },
                addContentDescription = stringResource(R.string.vehicles_add),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (vehicles.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    KBEmptyState(
                        icon = Icons.Filled.DirectionsCar,
                        title = stringResource(R.string.empty_vehicles_title),
                        body = stringResource(R.string.empty_vehicles_body),
                        primaryIcon = Icons.Filled.AddCircle,
                        primaryLabel = stringResource(R.string.empty_vehicles_action),
                        accent = orange,
                        onPrimary = { showAdd = true },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vehicles, key = { it.id }) { v ->
                        val next = earliestNonNull(
                            v.insuranceExpiryDate,
                            v.revisionExpiryDate,
                            v.taxExpiryDate,
                            v.nextServiceDate,
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenVehicle(v.id) },
                                    onLongClick = { toDelete = v },
                                ),
                            colors = CardDefaults.cardColors(containerColor = kb.card),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = orange)
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, fontWeight = FontWeight.SemiBold, color = kb.title)
                                    v.licensePlate?.takeIf { it.isNotBlank() }?.let { Text(it, color = kb.subtitle) }
                                }
                                next?.let {
                                    Surface(color = deadlineUrgencyColor(it), shape = RoundedCornerShape(12.dp)) {
                                        Text(
                                            formatItDate(it),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = kb.subtitle)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AddVehicleDialog(
            onDismiss = { showAdd = false },
            onConfirm = { fields ->
                viewModel.addVehicle(
                    name = fields.name,
                    licensePlate = fields.plate,
                    brand = fields.brand,
                    model = fields.model,
                    year = fields.year,
                    fuelType = fields.fuel,
                    color = fields.color,
                    vin = fields.vin,
                    insuranceExpiryDate = fields.ins,
                    revisionExpiryDate = fields.rev,
                    taxExpiryDate = fields.tax,
                    lastServiceDate = fields.lastSvc,
                    nextServiceDate = fields.nextSvc,
                    currentKm = fields.km,
                    notes = fields.notes,
                    reminderEnabled = fields.reminder,
                    reminderOffsetsJson = fields.reminderOffsetsJson,
                ) { err -> toast = err }
                showAdd = false
            },
        )
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare ${target.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVehicle(target) { err -> toast = err }
                    toDelete = null
                }) { Text(stringResource(R.string.life_delete), color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.life_cancel)) } },
        )
    }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("OK") } },
            title = { Text(stringResource(R.string.life_error)) },
            text = { Text(msg) },
        )
    }
}

private data class VehicleFormFields(
    val name: String,
    val plate: String?,
    val brand: String?,
    val model: String?,
    val year: Int?,
    val fuel: String?,
    val color: String?,
    val vin: String?,
    val ins: Long?,
    val rev: Long?,
    val tax: Long?,
    val lastSvc: Long?,
    val nextSvc: Long?,
    val km: Int?,
    val notes: String?,
    val reminder: Boolean,
    val reminderOffsetsJson: String?,
)

@Composable
private fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onConfirm: (VehicleFormFields) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var fuel by remember { mutableStateOf("benzina") }
    var color by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var ins by remember { mutableStateOf<Long?>(null) }
    var insOffsets by remember { mutableStateOf(VehicleReminderOffsets.DEFAULT_OFFSETS.toSet()) }
    var rev by remember { mutableStateOf<Long?>(null) }
    var revOffsets by remember { mutableStateOf(VehicleReminderOffsets.DEFAULT_OFFSETS.toSet()) }
    var tax by remember { mutableStateOf<Long?>(null) }
    var taxOffsets by remember { mutableStateOf(VehicleReminderOffsets.DEFAULT_OFFSETS.toSet()) }
    var lastSvc by remember { mutableStateOf<Long?>(null) }
    var nextSvc by remember { mutableStateOf<Long?>(null) }
    var nextSvcOffsets by remember { mutableStateOf(VehicleReminderOffsets.DEFAULT_OFFSETS.toSet()) }
    var kmText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(false) }
    val fuels = listOf("benzina", "diesel", "elettrica", "ibrida", "gpl")
    val pickIns = rememberLifeDatePicker { ins = it }
    val pickRev = rememberLifeDatePicker { rev = it }
    val pickTax = rememberLifeDatePicker { tax = it }
    val pickLast = rememberLifeDatePicker { lastSvc = it }
    val pickNext = rememberLifeDatePicker { nextSvc = it }

    val accent = Color(0xFFFF6B00)
    val kb = MaterialTheme.kidBoxColors

    @Composable
    fun DateRow(label: String, value: Long?, onPick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick() }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 14.sp, color = kb.subtitle)
            Text(
                value?.let { formatItDate(it) } ?: stringResource(R.string.vehicles_no_date),
                fontSize = 15.sp,
                color = kb.title,
            )
        }
    }

    KidBoxFormPage(
        title = stringResource(R.string.vehicles_new_vehicle),
        onDismiss = onDismiss,
        saveLabel = stringResource(R.string.life_save),
        saveEnabled = name.isNotBlank(),
        accent = accent,
        onSave = {
            if (name.isNotBlank()) {
                onConfirm(
                    VehicleFormFields(
                                name = name.trim(),
                                plate = plate.trim().takeIf { it.isNotEmpty() },
                                brand = brand.trim().takeIf { it.isNotEmpty() },
                                model = model.trim().takeIf { it.isNotEmpty() },
                                year = yearText.toIntOrNull(),
                                fuel = fuel,
                                color = color.trim().takeIf { it.isNotEmpty() },
                                vin = vin.trim().takeIf { it.isNotEmpty() },
                                ins = ins,
                                rev = rev,
                                tax = tax,
                                lastSvc = lastSvc,
                                nextSvc = nextSvc,
                                km = kmText.toIntOrNull(),
                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                reminder = reminder,
                                reminderOffsetsJson = VehicleReminderOffsets(
                                    insurance = insOffsets.sorted(),
                                    revision = revOffsets.sorted(),
                                    tax = taxOffsets.sorted(),
                                    service = nextSvcOffsets.sorted(),
                                ).encode(),
                            ),
                )
            }
        },
    ) {
        FormSectionTitle(stringResource(R.string.form_section_details))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.life_name)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text(stringResource(R.string.vehicles_plate)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text(stringResource(R.string.home_items_brand)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.home_items_model)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = yearText, onValueChange = { yearText = it }, label = { Text(stringResource(R.string.vehicles_year)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text(stringResource(R.string.vehicles_color)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = vin, onValueChange = { vin = it }, label = { Text("VIN") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

        FormSectionHeader(stringResource(R.string.vehicles_fuel), Icons.Default.LocalGasStation, accent)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = kb.rowBackground)) {
            Column {
                fuels.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = fuel == f, onClick = { fuel = f })
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = fuel == f, onClick = { fuel = f })
                        Text(vehicleFuelLabel(context, f), color = kb.title)
                    }
                }
            }
        }

        FormSectionHeader(stringResource(R.string.form_section_deadlines), Icons.Default.Event, accent)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = kb.rowBackground)) {
            Column {
                DateRow(stringResource(R.string.vehicles_insurance), ins) { pickIns(ins) }
                if (ins != null) {
                    ReminderOffsetChips(selected = insOffsets, onToggle = { d -> insOffsets = if (d in insOffsets) insOffsets - d else insOffsets + d })
                }
                DateRow(stringResource(R.string.vehicles_inspection), rev) { pickRev(rev) }
                if (rev != null) {
                    ReminderOffsetChips(selected = revOffsets, onToggle = { d -> revOffsets = if (d in revOffsets) revOffsets - d else revOffsets + d })
                }
                DateRow(stringResource(R.string.vehicles_road_tax), tax) { pickTax(tax) }
                if (tax != null) {
                    ReminderOffsetChips(selected = taxOffsets, onToggle = { d -> taxOffsets = if (d in taxOffsets) taxOffsets - d else taxOffsets + d })
                }
            }
        }

        FormSectionHeader(stringResource(R.string.form_section_service), Icons.Default.Build, accent)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = kb.rowBackground)) {
            Column {
                DateRow(stringResource(R.string.vehicles_last_service), lastSvc) { pickLast(lastSvc) }
                DateRow(stringResource(R.string.vehicles_next_service), nextSvc) { pickNext(nextSvc) }
                if (nextSvc != null) {
                    ReminderOffsetChips(selected = nextSvcOffsets, onToggle = { d -> nextSvcOffsets = if (d in nextSvcOffsets) nextSvcOffsets - d else nextSvcOffsets + d })
                }
            }
        }

        FormSectionHeader(stringResource(R.string.form_section_options), Icons.AutoMirrored.Filled.Note, accent)
        OutlinedTextField(value = kmText, onValueChange = { kmText = it }, label = { Text(stringResource(R.string.vehicles_current_km_short)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.life_notes)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 3)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = reminder, onCheckedChange = { reminder = it })
            Text(stringResource(R.string.vehicles_reminder), color = kb.title)
        }
        Spacer(Modifier.height(8.dp))
    }
}
