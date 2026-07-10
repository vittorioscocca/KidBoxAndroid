@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.domain.model.PatenteCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Editor delle categorie di patente (A, B, C, ...), ciascuna con la propria
 * data di rilascio e scadenza. Mirror di `PatenteCategoriesEditor` (iOS).
 */
@Composable
fun PatenteCategoriesEditor(
    categories: List<PatenteCategory>,
    onCategoriesChange: (List<PatenteCategory>) -> Unit,
) {
    Column {
        Text(
            "Categorie patente",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (categories.isEmpty()) {
            Text(
                "Aggiungi le categorie (A, B, C, …) con le rispettive date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        categories.forEachIndexed { index, category ->
            CategoryRow(
                category = category,
                onChange = { updated ->
                    onCategoriesChange(categories.toMutableList().also { it[index] = updated })
                },
                onDelete = {
                    onCategoriesChange(categories.toMutableList().also { it.removeAt(index) })
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        }
        Button(onClick = { onCategoriesChange(categories + PatenteCategory(code = "")) }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Aggiungi categoria")
        }
    }
}

@Composable
private fun CategoryRow(
    category: PatenteCategory,
    onChange: (PatenteCategory) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedTextField(
                value = category.code,
                onValueChange = { onChange(category.copy(code = it.uppercase())) },
                label = { Text("Categoria (es. B)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Elimina categoria")
            }
        }
        DateField(
            label = "Rilascio",
            date = category.issueDate,
            onChange = { onChange(category.copy(issueDate = it)) },
        )
        DateField(
            label = "Scadenza",
            date = category.expiryDate,
            onChange = { onChange(category.copy(expiryDate = it)) },
        )
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
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
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .padding(top = 12.dp),
                )
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Cambia data")
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
                }) { Text("OK") }
            },
            dismissButton = {
                Button(onClick = { showPicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
