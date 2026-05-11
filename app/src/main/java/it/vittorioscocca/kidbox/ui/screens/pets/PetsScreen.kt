package it.vittorioscocca.kidbox.ui.screens.pets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.ui.screens.life.speciesEmoji
import it.vittorioscocca.kidbox.ui.screens.life.speciesLabelIt
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PetsScreen(
    onNavigateBack: () -> Unit,
    onOpenPet: (String) -> Unit,
    viewModel: PetsViewModel = hiltViewModel(),
) {
    val pets by viewModel.pets.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var petToDelete by remember { mutableStateOf<PetEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text("Animali", color = kb.title) },
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
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = orange,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Aggiungi")
            }
        },
    ) { padding ->
        if (pets.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Pets, contentDescription = null, tint = orange, modifier = Modifier.padding(bottom = 8.dp))
                Text("Nessun animale ancora", style = MaterialTheme.typography.titleMedium, color = kb.title)
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = { showAdd = true },
                    color = orange,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "Aggiungi animale",
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
                items(pets, key = { it.id }) { pet ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenPet(pet.id) },
                                onLongClick = { petToDelete = pet },
                            ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(speciesEmoji(pet.species), style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.weight(1f)) {
                                Text(pet.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                                Text(speciesLabelIt(pet.species), color = MaterialTheme.kidBoxColors.subtitle, style = MaterialTheme.typography.bodySmall)
                                pet.breed?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = MaterialTheme.kidBoxColors.subtitle, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.kidBoxColors.subtitle,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAdd) {
        AddPetDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, species, breed, notes ->
                viewModel.addPet(name, species, breed, null, null, null, notes) { err -> toast = err }
                showAdd = false
            },
        )
    }

    petToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { petToDelete = null },
            title = { Text("Eliminare ${target.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePet(target) { err -> toast = err }
                        petToDelete = null
                    },
                ) { Text("Elimina", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { petToDelete = null }) { Text("Annulla") } },
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
private fun AddPetDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, species: String, breed: String?, notes: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("cane") }
    var breed by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val speciesOptions = listOf("cane", "gatto", "coniglio", "criceto", "uccello", "altro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo animale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                Text("Specie", style = MaterialTheme.typography.labelLarge)
                Column {
                    speciesOptions.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .selectable(selected = species == opt, onClick = { species = opt }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = species == opt, onClick = { species = opt })
                            Text(speciesLabelIt(opt))
                        }
                    }
                }
                OutlinedTextField(value = breed, onValueChange = { breed = it }, label = { Text("Razza (opzionale)") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), species, breed.trim().takeIf { it.isNotEmpty() }, notes.trim().takeIf { it.isNotEmpty() })
                    }
                },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
