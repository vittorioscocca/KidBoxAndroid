package it.vittorioscocca.kidbox.ui.screens.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.formatItDateTime
import it.vittorioscocca.kidbox.ui.screens.life.petEventTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.screens.life.speciesEmoji
import it.vittorioscocca.kidbox.ui.screens.life.speciesLabelIt
import it.vittorioscocca.kidbox.ui.components.IosFormDivider
import it.vittorioscocca.kidbox.ui.components.IosGroupedCard
import it.vittorioscocca.kidbox.ui.components.IosPlainTextFieldRow
import it.vittorioscocca.kidbox.ui.components.KidBoxIosFormTopBar
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
    onNavigateBack: () -> Unit,
    onAddTreatment: () -> Unit,
    onOpenTreatment: (String) -> Unit,
    viewModel: PetDetailViewModel = hiltViewModel(),
) {
    val pet by viewModel.pet.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val treatments by viewModel.treatments.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showEditPet by remember { mutableStateOf(false) }
    var showAddEvent by remember { mutableStateOf(false) }
    var confirmDeletePet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pet?.name ?: "Animale",
                        color = kb.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddEvent = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Nuovo evento", tint = orange)
                    }
                    IconButton(onClick = { showEditPet = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica", tint = kb.title)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Altro", tint = kb.title)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Nuova cura") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Medication, contentDescription = null, tint = orange)
                                },
                                onClick = {
                                    menuOpen = false
                                    onAddTreatment()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Elimina animale") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDeletePet = true
                                },
                            )
                        }
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
    ) { padding ->
        val p = pet
        if (p == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                PetHeaderCard(p = p, kb = kb)
            }
            item {
                Text(
                    "STORICO EVENTI",
                    color = kb.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                if (events.isEmpty()) {
                    Text(
                        "Nessun evento registrato",
                        color = kb.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column {
                            events.forEachIndexed { index, ev ->
                                PetEventRow(
                                    ev = ev,
                                    orange = orange,
                                    kb = kb,
                                )
                                if (index < events.lastIndex) {
                                    HorizontalDivider(color = kb.divider, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "CURE",
                    color = kb.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                val care = treatments.filter { !it.isDeleted }
                if (care.isEmpty()) {
                    Text(
                        "Nessuna cura registrata",
                        color = kb.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column {
                            care.forEachIndexed { index, t ->
                                PetTreatmentRow(
                                    t = t,
                                    orange = orange,
                                    kb = kb,
                                    onClick = { onOpenTreatment(t.id) },
                                )
                                if (index < care.lastIndex) {
                                    HorizontalDivider(color = kb.divider, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddEvent) {
        AddPetEventDialog(
            onDismiss = { showAddEvent = false },
            onConfirm = { title, type, date, nextDue, vet, cost, notes, reminder ->
                viewModel.addPetEvent(title, type, date, nextDue, vet, cost, notes, reminder) { err -> toast = err }
                showAddEvent = false
            },
        )
    }

    if (showEditPet && pet != null) {
        EditPetDialog(
            initial = pet!!,
            onDismiss = { showEditPet = false },
            onConfirm = { updated ->
                viewModel.updatePet(updated) { err -> toast = err }
                showEditPet = false
            },
        )
    }

    if (confirmDeletePet && pet != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePet = false },
            title = { Text("Eliminare questo animale?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pet?.let { viewModel.deletePet(it) { err -> toast = err }; onNavigateBack() }
                        confirmDeletePet = false
                    },
                ) { Text("Elimina", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePet = false }) { Text("Annulla") } },
        )
    }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("OK") } },
            title = { Text("Errore") },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun PetHeaderCard(
    p: PetEntity,
    kb: KidBoxColorScheme,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    speciesEmoji(p.species),
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                    )
                    p.breed?.takeIf { it.isNotBlank() }?.let { breed ->
                        Text(breed, style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
                    }
                }
            }
            p.birthDate?.let { bd ->
                val years = petAgeYearsEuropeRome(bd)
                PetLabeledBlock(
                    label = "Data di nascita",
                    value = "${formatItDate(bd)} ($years anni)",
                    kb = kb,
                )
            }
            p.chipCode?.takeIf { it.isNotBlank() }?.let { chip ->
                PetLabeledBlock(label = "Microchip", value = chip, kb = kb)
            }
            p.color?.takeIf { it.isNotBlank() }?.let { col ->
                PetLabeledBlock(label = "Colore", value = col, kb = kb)
            }
            p.notes?.takeIf { it.isNotBlank() }?.let { n ->
                Text(n, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun PetLabeledBlock(label: String, value: String, kb: KidBoxColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = kb.title)
    }
}

private fun petAgeYearsEuropeRome(birthMillis: Long): Int {
    val zone = ZoneId.of("Europe/Rome")
    val birth = Instant.ofEpochMilli(birthMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return ChronoUnit.YEARS.between(birth, today).toInt().coerceAtLeast(0)
}

@Composable
private fun PetTreatmentRow(
    t: KBTreatment,
    orange: Color,
    kb: KidBoxColorScheme,
    onClick: () -> Unit,
) {
    val reminderBg = orange.copy(alpha = 0.2f)
    val dosageStr = if (t.dosageValue % 1.0 == 0.0) "%.0f".format(t.dosageValue) else "%.1f".format(t.dosageValue)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Medication,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t.drugName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = kb.title,
            )
            Text(
                "$dosageStr ${t.dosageUnit} · ${t.dailyFrequency}x al giorno",
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            if (t.reminderEnabled) {
                Surface(
                    color = reminderBg,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "Promemoria attivo",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = orange,
                    )
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = kb.subtitle.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun petEventEmoji(type: String): String = when (type) {
    "vaccine" -> "💉"
    "vet_visit" -> "🩺"
    "medication" -> "💊"
    "grooming" -> "✂️"
    else -> "📅"
}

@Composable
private fun PetEventRow(
    ev: PetEventEntity,
    orange: Color,
    kb: KidBoxColorScheme,
) {
    val nextDuePillBackground = orange.copy(alpha = 0.2f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                petEventEmoji(ev.eventType),
                fontSize = 20.sp,
                color = orange,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                ev.title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = kb.title,
            )
            Text(
                formatItDateTime(ev.date),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            ev.nextDueDate?.let { nd ->
                Surface(
                    color = nextDuePillBackground,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "Prossima: ${formatItDate(nd)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = orange,
                    )
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = kb.subtitle.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AddPetEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        type: String,
        dateMillis: Long,
        nextDue: Long?,
        vet: String?,
        cost: Double?,
        notes: String?,
        reminder: Boolean,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("vaccine") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var nextDue by remember { mutableStateOf<Long?>(null) }
    var vet by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    val types = listOf("vaccine", "vet_visit", "medication", "grooming", "other")
    val pickDate = rememberLifeDatePicker { dateMillis = it }
    val pickNext = rememberLifeDatePicker { picked -> nextDue = picked }
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val canSave = title.trim().isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = kb.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                KidBoxIosFormTopBar(
                    title = "Nuovo evento",
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val cost = costText.replace(',', '.').toDoubleOrNull()
                            onConfirm(
                                title.trim(),
                                type,
                                dateMillis,
                                nextDue,
                                vet.trim().takeIf { it.isNotEmpty() },
                                cost,
                                notes.trim().takeIf { it.isNotEmpty() },
                                nextDue != null,
                            )
                        }
                    },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = orange,
                )
                HorizontalDivider(color = kb.divider)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(title, { title = it }, "Titolo", kb = kb)
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { typeMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Tipo evento", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(petEventTypeLabelIt(type), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = typeMenuOpen,
                                onDismissRequest = { typeMenuOpen = false },
                            ) {
                                types.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(petEventTypeLabelIt(t)) },
                                        onClick = {
                                            type = t
                                            typeMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { pickDate(dateMillis) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Data", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Text(formatItDate(dateMillis), color = orange, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Prossima scadenza", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = nextDue != null,
                                onCheckedChange = { on ->
                                    if (on) nextDue = nextDue ?: dateMillis
                                    if (!on) nextDue = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (nextDue != null) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickNext(nextDue) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Prossima scadenza", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    nextDue?.let { formatItDate(it) }.orEmpty(),
                                    color = orange,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(vet, { vet = it }, "Veterinario", kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(costText, { costText = it }, "Costo", kb = kb)
                    }

                    Text(
                        "Note",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            placeholder = { Text("Note", color = kb.subtitle) },
                            singleLine = false,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kb.title,
                                unfocusedTextColor = kb.title,
                                cursorColor = kb.title,
                                focusedPlaceholderColor = kb.subtitle,
                                unfocusedPlaceholderColor = kb.subtitle,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPetDialog(
    initial: PetEntity,
    onDismiss: () -> Unit,
    onConfirm: (PetEntity) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var species by remember(initial.id) { mutableStateOf(initial.species) }
    var breed by remember(initial.id) { mutableStateOf(initial.breed.orEmpty()) }
    var hasBirthDate by remember(initial.id) { mutableStateOf(initial.birthDate != null) }
    var birthDate by remember(initial.id) { mutableStateOf(initial.birthDate ?: System.currentTimeMillis()) }
    var color by remember(initial.id) { mutableStateOf(initial.color.orEmpty()) }
    var chipCode by remember(initial.id) { mutableStateOf(initial.chipCode.orEmpty()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var speciesMenuOpen by remember { mutableStateOf(false) }

    val speciesOptions = listOf("cane", "gatto", "coniglio", "criceto", "uccello", "altro")
    val pickBirth = rememberLifeDatePicker { birthDate = it }
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val canSave = name.trim().isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = kb.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                KidBoxIosFormTopBar(
                    title = "Modifica animale",
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val trimmed = name.trim()
                            onConfirm(
                                initial.copy(
                                    name = trimmed,
                                    species = species,
                                    breed = breed.trim().takeIf { it.isNotEmpty() },
                                    birthDate = if (hasBirthDate) birthDate else null,
                                    color = color.trim().takeIf { it.isNotEmpty() },
                                    chipCode = chipCode.trim().takeIf { it.isNotEmpty() },
                                    notes = notes.trim().takeIf { it.isNotEmpty() },
                                ),
                            )
                        }
                    },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = orange,
                )
                HorizontalDivider(color = kb.divider)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(name, { name = it }, "Nome", kb = kb)
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { speciesMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Specie", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(speciesLabelIt(species), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = speciesMenuOpen,
                                onDismissRequest = { speciesMenuOpen = false },
                            ) {
                                speciesOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(speciesLabelIt(opt)) },
                                        onClick = {
                                            species = opt
                                            speciesMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(breed, { breed = it }, "Razza (opzionale)", kb = kb)
                    }

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Data di nascita", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasBirthDate,
                                onCheckedChange = { on ->
                                    hasBirthDate = on
                                    if (on && initial.birthDate == null) {
                                        birthDate = System.currentTimeMillis()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (hasBirthDate) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickBirth(birthDate) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Data", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(formatItDate(birthDate), color = orange, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(color, { color = it }, "Colore", kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(chipCode, { chipCode = it }, "Microchip", kb = kb)
                    }

                    Text(
                        "Note",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            placeholder = { Text("Note", color = kb.subtitle) },
                            singleLine = false,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kb.title,
                                unfocusedTextColor = kb.title,
                                cursorColor = kb.title,
                                focusedPlaceholderColor = kb.subtitle,
                                unfocusedPlaceholderColor = kb.subtitle,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
