package it.vittorioscocca.kidbox.ui.screens.homeitems

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.ui.components.IosFormDivider
import it.vittorioscocca.kidbox.ui.components.IosGroupedCard
import it.vittorioscocca.kidbox.ui.components.IosPlainTextFieldRow
import it.vittorioscocca.kidbox.ui.components.KidBoxIosFormTopBar
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.housePaymentTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private fun billSubtypeLabelIt(raw: String): String = when (raw) {
    "luce" -> "Luce"
    "gas" -> "Gas"
    "internet" -> "Internet"
    "telefono" -> "Telefono"
    "acqua" -> "Acqua"
    "condominio" -> "Condominio"
    else -> raw
}

private fun taxSubtypeLabelIt(raw: String): String = when (raw) {
    "IMU" -> "IMU"
    "TARI" -> "TARI"
    "dichiarazione redditi" -> "Dichiarazione redditi"
    "bollo auto" -> "Bollo auto"
    "altre" -> "Altre"
    else -> raw
}

@Composable
fun AddHousePaymentDialog(
    draftAttachments: List<KBDocumentEntity>,
    attachmentUploading: Boolean,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickKidBoxDocuments: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        typeRaw: String,
        subtypeRaw: String?,
        importo: Double?,
        giorno: Int?,
        dataScadenza: Long?,
        dataContratto: Long?,
        fornitore: String?,
        note: String?,
        reminderOn: Boolean,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var typeRaw by remember { mutableStateOf("bolletta") }
    var subtype by remember { mutableStateOf("luce") }
    var subtypeFree by remember { mutableStateOf("") }
    var importoText by remember { mutableStateOf("") }
    var hasGiorno by remember { mutableStateOf(true) }
    var giorno by remember { mutableStateOf(5) }
    var hasDataScadenza by remember { mutableStateOf(false) }
    var dataScadenza by remember { mutableStateOf(System.currentTimeMillis()) }
    var hasContratto by remember { mutableStateOf(false) }
    var dataContratto by remember { mutableStateOf(System.currentTimeMillis()) }
    var fornitore by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var reminderOn by remember { mutableStateOf(true) }

    var typeMenuOpen by remember { mutableStateOf(false) }
    var subtypeMenuOpen by remember { mutableStateOf(false) }

    val types = listOf("mutuo", "affitto", "bolletta", "tassa", "altro")
    val billSub = listOf("luce", "gas", "internet", "telefono", "acqua", "condominio")
    val taxSub = listOf("IMU", "TARI", "dichiarazione redditi", "bollo auto", "altre")

    val pickScadenza = rememberLifeDatePicker { dataScadenza = it }
    val pickContratto = rememberLifeDatePicker { dataContratto = it }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val canSave = name.trim().isNotBlank()

    fun subtypeForSave(): String? = when (typeRaw) {
        "bolletta" -> subtype
        "tassa" -> subtype
        "altro" -> subtypeFree.trim().takeIf { it.isNotEmpty() }
        else -> subtypeFree.trim().takeIf { it.isNotEmpty() }
    }

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
                    title = "Nuova scadenza",
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val imp = importoText.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                            onConfirm(
                                name.trim(),
                                typeRaw,
                                subtypeForSave(),
                                imp,
                                if (hasGiorno && typeRaw in listOf("mutuo", "affitto", "bolletta", "altro")) giorno else null,
                                if (hasDataScadenza && typeRaw in listOf("tassa", "altro")) dataScadenza else null,
                                if (hasContratto && typeRaw in listOf("mutuo", "affitto")) dataContratto else null,
                                fornitore.trim().takeIf { it.isNotEmpty() },
                                note.trim().takeIf { it.isNotEmpty() },
                                reminderOn,
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
                                    .clickable { typeMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Tipo", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(housePaymentTypeLabelIt(typeRaw), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                                types.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(housePaymentTypeLabelIt(t)) },
                                        onClick = {
                                            typeRaw = t
                                            when (t) {
                                                "bolletta" -> if (subtype !in billSub) subtype = "luce"
                                                "tassa" -> if (subtype !in taxSub) subtype = "IMU"
                                            }
                                            typeMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        when (typeRaw) {
                            "bolletta", "tassa" -> {
                                IosFormDivider(kb)
                                Box(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { subtypeMenuOpen = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Tipologia", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                if (typeRaw == "bolletta") billSubtypeLabelIt(subtype) else taxSubtypeLabelIt(subtype),
                                                color = kb.subtitle,
                                            )
                                            Icon(
                                                Icons.Filled.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = kb.subtitle,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                    DropdownMenu(expanded = subtypeMenuOpen, onDismissRequest = { subtypeMenuOpen = false }) {
                                        if (typeRaw == "bolletta") {
                                            billSub.forEach { s ->
                                                DropdownMenuItem(
                                                    text = { Text(billSubtypeLabelIt(s)) },
                                                    onClick = {
                                                        subtype = s
                                                        subtypeMenuOpen = false
                                                    },
                                                )
                                            }
                                        } else {
                                            taxSub.forEach { s ->
                                                DropdownMenuItem(
                                                    text = { Text(taxSubtypeLabelIt(s)) },
                                                    onClick = {
                                                        subtype = s
                                                        subtypeMenuOpen = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                IosFormDivider(kb)
                                IosPlainTextFieldRow(
                                    subtypeFree,
                                    { subtypeFree = it },
                                    if (typeRaw == "altro") "Tipologia (libero)" else "Dettaglio (opzionale)",
                                    kb = kb,
                                )
                            }
                        }

                        IosFormDivider(kb)
                        IosPlainTextFieldRow(
                            importoText,
                            { importoText = it },
                            "Importo (opzionale)",
                            kb = kb,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }

                    IosGroupedCard(kb) {
                        if (typeRaw in listOf("mutuo", "affitto", "bolletta", "altro")) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Giorno di scadenza nel mese",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = kb.title,
                                )
                                Switch(
                                    checked = hasGiorno,
                                    onCheckedChange = { hasGiorno = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = orange,
                                        checkedTrackColor = orange.copy(alpha = 0.35f),
                                    ),
                                )
                            }
                            if (hasGiorno) {
                                IosFormDivider(kb)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { if (giorno > 1) giorno-- },
                                        enabled = giorno > 1,
                                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                    Text("Giorno $giorno", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                    TextButton(
                                        onClick = { if (giorno < 31) giorno++ },
                                        enabled = giorno < 31,
                                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                                }
                            }
                        }
                        if (typeRaw in listOf("tassa", "altro")) {
                            if (typeRaw in listOf("mutuo", "affitto", "bolletta", "altro")) {
                                IosFormDivider(kb)
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Data scadenza annuale",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = kb.title,
                                )
                                Switch(
                                    checked = hasDataScadenza,
                                    onCheckedChange = {
                                        hasDataScadenza = it
                                        if (it) dataScadenza = System.currentTimeMillis()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = orange,
                                        checkedTrackColor = orange.copy(alpha = 0.35f),
                                    ),
                                )
                            }
                            if (hasDataScadenza) {
                                IosFormDivider(kb)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { pickScadenza(dataScadenza) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Scadenza", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                    Text(
                                        formatItDate(dataScadenza),
                                        color = orange,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                        if (typeRaw in listOf("mutuo", "affitto")) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Scadenza contratto",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = kb.title,
                                )
                                Switch(
                                    checked = hasContratto,
                                    onCheckedChange = {
                                        hasContratto = it
                                        if (it) dataContratto = System.currentTimeMillis()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = orange,
                                        checkedTrackColor = orange.copy(alpha = 0.35f),
                                    ),
                                )
                            }
                            if (hasContratto) {
                                IosFormDivider(kb)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { pickContratto(dataContratto) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Contratto fino al", style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                    Text(
                                        formatItDate(dataContratto),
                                        color = orange,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Fornitore",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(
                            fornitore,
                            { fornitore = it },
                            "Banca / gestore / agenzia",
                            kb = kb,
                        )
                    }

                    Text(
                        "Note",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = note,
                            onValueChange = { note = it },
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

                    Text(
                        "Allegati",
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    HealthAttachmentsCard(
                        attachments = draftAttachments,
                        tintColor = orange,
                        isUploading = attachmentUploading,
                        onPickFile = onPickFile,
                        onPickPhoto = onPickPhoto,
                        onTakePhoto = onTakePhoto,
                        onOpenAttachment = onOpenAttachment,
                        onDeleteAttachment = onDeleteAttachment,
                        onPickFromKidBoxDocuments = onPickKidBoxDocuments,
                    )

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Promemoria (3 giorni prima)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = kb.title,
                            )
                            Switch(
                                checked = reminderOn,
                                onCheckedChange = { reminderOn = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
