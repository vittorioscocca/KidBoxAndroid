@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Selettore del titolare del documento (figlio, membro famiglia, o famiglia in generale). */
@Composable
fun OwnerDropdown(
    owners: List<WalletDocumentOwner>,
    selected: WalletDocumentOwner,
    onSelected: (WalletDocumentOwner) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.wallet_holder_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, owners = owners) { owner ->
            onSelected(owner)
            expanded = false
        }
    }
}

@Composable
private fun ExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    owners: List<WalletDocumentOwner>,
    onPick: (WalletDocumentOwner) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        owners.forEach { owner ->
            DropdownMenuItem(text = { Text(owner.displayName) }, onClick = { onPick(owner) })
        }
    }
}

/** Selettore del tipo di documento (Tessera Sanitaria, CIE, Patente, ...). */
@Composable
fun KindDropdown(
    selected: DocumentKind,
    onSelected: (DocumentKind) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.wallet_document_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DocumentKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.displayName) },
                    onClick = { onSelected(kind); expanded = false },
                )
            }
        }
    }
}

/** Riga data (etichetta + switch attiva/disattiva + valore + editor), riusata da add/link/edit/patente. */
@Composable
fun DocumentDateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row {
            Switch(
                checked = date != null,
                onCheckedChange = { onChange(if (it) LocalDate.now() else null) },
            )
            if (date != null) {
                Text(
                    date.format(fmt),
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.wallet_document_change_date_cd))
                }
            }
        }
    }

    if (showPicker) {
        val initialMillis = (date ?: LocalDate.now())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                Button(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.wallet_ok)) }
            },
            dismissButton = {
                Button(onClick = { showPicker = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
