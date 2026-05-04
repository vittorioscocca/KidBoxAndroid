@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBEmergencyContact
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.UUID

private val BLOOD_GROUPS = listOf(
    "Non specificato", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-",
)

@Composable
fun MedicalRecordScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    viewModel: MedicalRecordViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingContact by remember { mutableStateOf<KBEmergencyContact?>(null) }
    var showAddContact by remember { mutableStateOf(false) }
    var bloodMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    LaunchedEffect(state.savedAt) {
        if (state.savedAt != null) {
            Toast.makeText(context, "Scheda salvata", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val primaryDoctorLabel = if (state.isChild) "Pediatra di riferimento" else "Medico di base"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Scheda Medica",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
            Spacer(Modifier.height(20.dp))

            // ── Gruppo sanguigno ───────────────────────────────────────────────
            SectionLabel("Gruppo sanguigno")
            ExposedDropdownMenuBox(
                expanded = bloodMenuExpanded,
                onExpandedChange = { bloodMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.bloodGroup,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = bloodMenuExpanded,
                    onDismissRequest = { bloodMenuExpanded = false },
                ) {
                    BLOOD_GROUPS.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g) },
                            onClick = {
                                viewModel.setBloodGroup(g)
                                bloodMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Allergie ───────────────────────────────────────────────────────
            SectionLabel("Allergie conosciute")
            OutlinedTextField(
                value = state.allergies,
                onValueChange = viewModel::setAllergies,
                placeholder = { Text("es. Pollini, latticini…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
            )
            Spacer(Modifier.height(16.dp))

            // ── Note mediche ───────────────────────────────────────────────────
            SectionLabel("Note mediche")
            OutlinedTextField(
                value = state.medicalNotes,
                onValueChange = viewModel::setMedicalNotes,
                placeholder = { Text("Patologie pregresse, terapie continuative…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))

            // ── Medico di riferimento ─────────────────────────────────────────────
            SectionLabel(primaryDoctorLabel)
            OutlinedTextField(
                value = state.doctorName,
                onValueChange = viewModel::setDoctorName,
                placeholder = { Text("Nome e cognome") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.doctorPhone,
                onValueChange = viewModel::setDoctorPhone,
                placeholder = { Text("Telefono") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Spacer(Modifier.height(20.dp))

            // ── Contatti di emergenza ──────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Contatti di emergenza",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = kb.title,
                )
                Spacer(Modifier.weight(1f))
                KidBoxHeaderCircleButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Aggiungi contatto",
                    onClick = { showAddContact = true },
                    iconTint = Color(0xFFFF6B00),
                )
            }
            if (state.emergencyContacts.isEmpty()) {
                Text(
                    "Nessun contatto di emergenza.",
                    fontSize = 13.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                state.emergencyContacts.forEach { contact ->
                    EmergencyContactRow(
                        contact = contact,
                        onTap = { editingContact = contact },
                        onDelete = { viewModel.removeContact(contact.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(28.dp))

            // ── Save button ────────────────────────────────────────────────────
            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B00),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        "Salva scheda",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (showAddContact) {
        EmergencyContactDialog(
            initial = null,
            onDismiss = { showAddContact = false },
            onSave = { c ->
                viewModel.upsertContact(c.copy(id = UUID.randomUUID().toString()))
                showAddContact = false
            },
        )
    }
    editingContact?.let { contact ->
        EmergencyContactDialog(
            initial = contact,
            onDismiss = { editingContact = null },
            onSave = { updated ->
                viewModel.upsertContact(updated)
                editingContact = null
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.subtitle)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EmergencyContactRow(
    contact: KBEmergencyContact,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        onClick = onTap,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF66BFA6).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF40A6BF),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                )
                if (contact.relation.isNotBlank()) {
                    Text(contact.relation, fontSize = 12.sp, color = kb.subtitle)
                }
                if (contact.phone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF40A6BF),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            contact.phone,
                            fontSize = 12.sp,
                            color = Color(0xFF40A6BF),
                        )
                    }
                }
            }
            KidBoxHeaderCircleButton(
                icon = Icons.Default.Delete,
                contentDescription = "Elimina",
                onClick = onDelete,
                iconTint = Color(0xFFE53935),
            )
        }
    }
}

@Composable
private fun EmergencyContactDialog(
    initial: KBEmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (KBEmergencyContact) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var relation by remember { mutableStateOf(initial?.relation.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    val canSave = name.isNotBlank() && phone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuovo contatto" else "Modifica contatto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome e cognome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relazione (es. Nonna)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefono") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        KBEmergencyContact(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            relation = relation.trim(),
                            phone = phone.trim(),
                        )
                    )
                },
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
