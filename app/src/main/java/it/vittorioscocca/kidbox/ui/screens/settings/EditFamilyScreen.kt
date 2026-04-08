package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChildEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var birthDate: LocalDate? = null,
)

@Composable
fun EditFamilyScreen(
    onBack: () -> Unit,
    onEditChild: (String) -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var familyName by remember { mutableStateOf("") }
    var children by remember { mutableStateOf(listOf(ChildEntry())) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.family?.id, state.children) {
        familyName = state.family?.name.orEmpty()
        if (state.children.isNotEmpty() && (children.size == 1 && children.first().name.isBlank())) {
            children = state.children.map {
                ChildEntry(
                    id = it.id,
                    name = it.name,
                    birthDate = it.birthDateEpochMillis?.let { millis ->
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    },
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text("Modifica famiglia", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Famiglia", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(familyName, { familyName = it }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(14.dp))
        Text("Figli", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column {
                children.forEachIndexed { index, child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        OutlinedTextField(
                            value = child.name,
                            onValueChange = { value ->
                                children = children.toMutableList().also {
                                    it[index] = it[index].copy(name = value)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Nome figlio") },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(0.dp).weight(0.05f))
                        Text(
                            child.birthDate?.format(dateFormat) ?: "-",
                            color = Color(0xFF777777),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { children = children + ChildEntry() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
        ) {
            androidx.compose.material3.Icon(Icons.Filled.Add, null)
            Text(" Aggiungi figlio")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.saveFamilyWithChildren(
                    newName = familyName,
                    childrenInputs = children.map { entry ->
                        ChildInput(
                            id = entry.id,
                            name = entry.name,
                            birthDateEpochMillis = entry.birthDate
                                ?.atStartOfDay(ZoneId.systemDefault())
                                ?.toInstant()
                                ?.toEpochMilli(),
                        )
                    },
                    onDone = onBack,
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
        ) {
            Text("Salva modifiche")
        }
        state.error?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = Color(0xFFE53E3E), fontSize = 12.sp)
        }
    }
}
