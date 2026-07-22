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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import it.vittorioscocca.kidbox.ui.screens.life.deadlineUrgencyColor
import it.vittorioscocca.kidbox.ui.screens.life.earliestNonNull
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.screens.life.vehicleFuelLabelIt
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VehiclesScreen(
    onNavigateBack: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel(),
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<VehicleEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicles_garage), color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.background,
                    titleContentColor = kb.title,
                    navigationIconContentColor = kb.title,
                    actionIconContentColor = kb.title,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = orange, contentColor = Color.White) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vehicles_add))
            }
        },
    ) { padding ->
        if (vehicles.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = kb.title)
                Text(stringResource(R.string.vehicles_none), color = kb.title, style = MaterialTheme.typography.titleMedium)
                Surface(
                    onClick = { showAdd = true },
                    color = orange,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        stringResource(R.string.vehicles_add_vehicle),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
)

@Composable
private fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onConfirm: (VehicleFormFields) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var fuel by remember { mutableStateOf("benzina") }
    var color by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var ins by remember { mutableStateOf<Long?>(null) }
    var rev by remember { mutableStateOf<Long?>(null) }
    var tax by remember { mutableStateOf<Long?>(null) }
    var lastSvc by remember { mutableStateOf<Long?>(null) }
    var nextSvc by remember { mutableStateOf<Long?>(null) }
    var kmText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(false) }
    val fuels = listOf("benzina", "diesel", "elettrica", "ibrida", "gpl")
    val pickIns = rememberLifeDatePicker { ins = it }
    val pickRev = rememberLifeDatePicker { rev = it }
    val pickTax = rememberLifeDatePicker { tax = it }
    val pickLast = rememberLifeDatePicker { lastSvc = it }
    val pickNext = rememberLifeDatePicker { nextSvc = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicles_new_vehicle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.life_name)) })
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text(stringResource(R.string.vehicles_plate)) })
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text(stringResource(R.string.home_items_brand)) })
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.home_items_model)) })
                OutlinedTextField(value = yearText, onValueChange = { yearText = it }, label = { Text(stringResource(R.string.vehicles_year)) })
                Text(stringResource(R.string.vehicles_fuel), style = MaterialTheme.typography.labelLarge)
                fuels.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = fuel == f, onClick = { fuel = f })
                        Text(vehicleFuelLabelIt(f))
                    }
                }
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text(stringResource(R.string.vehicles_color)) })
                OutlinedTextField(value = vin, onValueChange = { vin = it }, label = { Text("VIN") })
                TextButton(onClick = { pickIns(ins) }) { Text("Scad. assicurazione: ${ins?.let { formatItDate(it) } ?: "—"}") }
                TextButton(onClick = { pickRev(rev) }) { Text("Scad. revisione: ${rev?.let { formatItDate(it) } ?: "—"}") }
                TextButton(onClick = { pickTax(tax) }) { Text("Scad. bollo: ${tax?.let { formatItDate(it) } ?: "—"}") }
                TextButton(onClick = { pickLast(lastSvc) }) { Text("Ultimo tagliando: ${lastSvc?.let { formatItDate(it) } ?: "—"}") }
                TextButton(onClick = { pickNext(nextSvc) }) { Text("Prossimo tagliando: ${nextSvc?.let { formatItDate(it) } ?: "—"}") }
                OutlinedTextField(value = kmText, onValueChange = { kmText = it }, label = { Text(stringResource(R.string.vehicles_current_km_short)) })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.life_notes)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                    Text(stringResource(R.string.vehicles_reminder))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
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
                            ),
                        )
                    }
                },
            ) { Text(stringResource(R.string.life_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_cancel)) } },
    )
}
