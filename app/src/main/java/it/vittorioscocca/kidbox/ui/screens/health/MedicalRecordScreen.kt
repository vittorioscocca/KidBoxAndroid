@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.core.content.ContextCompat
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
    var draftContact by remember { mutableStateOf<KBEmergencyContact?>(null) }
    var bloodMenuExpanded by remember { mutableStateOf(false) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = readContact(context, uri)
        if (selected == null) {
            Toast.makeText(context, "Impossibile leggere il contatto selezionato", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        draftContact = KBEmergencyContact(
            id = UUID.randomUUID().toString(),
            name = selected.first,
            relation = "",
            phone = selected.second,
        )
        showAddContact = true
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            contactPicker.launch(null)
        } else {
            Toast.makeText(
                context,
                "Per selezionare un contatto, consenti l'accesso alla rubrica.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

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
                TextField(
                    value = state.bloodGroup,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
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
            TextField(
                value = state.allergies,
                onValueChange = viewModel::setAllergies,
                placeholder = { Text("es. Latte, uova, pollini") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(16.dp))

            // ── Medico di riferimento ─────────────────────────────────────────────
            SectionLabel(primaryDoctorLabel)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = state.doctorName,
                    onValueChange = viewModel::setDoctorName,
                    placeholder = { Text("Dott./Dott.ssa") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                TextField(
                    value = state.doctorPhone,
                    onValueChange = viewModel::setDoctorPhone,
                    placeholder = { Text("Telefono") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            }
            Spacer(Modifier.height(20.dp))

            // ── Contatti di emergenza ──────────────────────────────────────────
            SectionLabel("Contatti emergenza")
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.emergencyContacts.isEmpty()) {
                    Text(
                        "Nessun contatto aggiunto",
                        color = kb.subtitle,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                } else {
                    state.emergencyContacts.forEachIndexed { index, contact ->
                        ContactListRow(
                            contact = contact,
                            onTap = { editingContact = contact },
                            onDelete = { viewModel.removeContact(contact.id) },
                        )
                        if (index < state.emergencyContacts.lastIndex) {
                            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                        }
                    }
                }
                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0A84FF))
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                            if (permission == PackageManager.PERMISSION_GRANTED) {
                                contactPicker.launch(null)
                            } else {
                                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                    ) {
                        Text("Aggiungi contatto", color = Color(0xFF0A84FF), fontSize = 18.sp)
                    }
                }
            }
            Text(
                "Persone da contattare in caso di emergenza (nonni, babysitter, secondo genitore...)",
                fontSize = 12.sp,
                color = kb.subtitle,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))

            // ── Note mediche ───────────────────────────────────────────────────
            SectionLabel("Note mediche")
            TextField(
                value = state.medicalNotes,
                onValueChange = viewModel::setMedicalNotes,
                placeholder = { Text("Eventuali condizioni o note importanti") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(20.dp))

            // ── Save button ────────────────────────────────────────────────────
            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0A84FF),
                    )
                } else {
                    Text(
                        "Salva scheda",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0A84FF),
                    )
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (showAddContact) {
        EmergencyContactDialog(
            initial = draftContact,
            onDismiss = {
                draftContact = null
                showAddContact = false
            },
            onSave = { c ->
                viewModel.upsertContact(c.copy(id = UUID.randomUUID().toString()))
                draftContact = null
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
private fun ContactListRow(
    contact: KBEmergencyContact,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontSize = 18.sp, color = kb.title)
            if (contact.phone.isNotBlank()) {
                Text(contact.phone, fontSize = 14.sp, color = kb.subtitle)
            }
            if (contact.relation.isNotBlank()) {
                Text(contact.relation, fontSize = 12.sp, color = kb.subtitle)
            }
        }
        TextButton(onClick = onTap) { Text("Modifica", color = Color(0xFF0A84FF)) }
        TextButton(onClick = onDelete) { Text("Elimina", color = Color(0xFFFF3B30)) }
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

private fun readContact(context: Context, uri: android.net.Uri): Pair<String, String>? {
    val resolver = context.contentResolver
    var name = ""
    var phone = ""
    resolver.query(
        uri,
        arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val contactId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
            name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
            if (contactId.isNotBlank()) {
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null,
                )?.use { phones ->
                    if (phones.moveToFirst()) {
                        val numIdx = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        phone = if (numIdx >= 0) phones.getString(numIdx).orEmpty() else ""
                    }
                }
            }
        }
    }
    if (name.isBlank()) return null
    return name to phone
}
