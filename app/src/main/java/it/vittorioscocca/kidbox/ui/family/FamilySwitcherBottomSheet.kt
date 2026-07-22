@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File

private val KidBoxAccentBlue = Color(0xFF0A84FF)

@Composable
fun FamilySwitcherBottomSheet(
    sheetState: SheetState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSwitchComplete: () -> Unit,
    onJoinFamily: () -> Unit,
    viewModel: FamilySwitcherViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val families by viewModel.families.collectAsStateWithLifecycle()
    val activeFamilyId by viewModel.activeFamilyId.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFamilyName by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FamilySwitcherViewModel.Event.SwitchComplete -> {
                    onDismiss()
                    onSwitchComplete()
                }
                FamilySwitcherViewModel.Event.FamilyCreated -> {
                    showCreateDialog = false
                    newFamilyName = ""
                    snackbarHostState.showSnackbar(context.getString(R.string.family_switcher_created_snackbar))
                }
                is FamilySwitcherViewModel.Event.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = kb.background,
        contentColor = kb.title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.family_switcher_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = kb.title,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(families, key = { it.id }) { family ->
                    FamilySwitcherRow(
                        family = family,
                        isActive = family.id == activeFamilyId,
                        onClick = { viewModel.switchToFamily(family.id) },
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = kb.subtitle.copy(alpha = 0.2f),
            )

            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, kb.divider),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = kb.card,
                    contentColor = KidBoxAccentBlue,
                ),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = KidBoxAccentBlue,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.family_switcher_create_button))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onJoinFamily()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, kb.divider),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = kb.card,
                    contentColor = Color(0xFF30B0C7),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF30B0C7),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.family_switcher_join_button))
            }

            Text(
                text = stringResource(R.string.family_switcher_join_hint),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateDialog = false },
            containerColor = kb.card,
            titleContentColor = kb.title,
            textContentColor = kb.subtitle,
            title = { Text(stringResource(R.string.family_switcher_new_family_title)) },
            text = {
                OutlinedTextField(
                    value = newFamilyName,
                    onValueChange = { newFamilyName = it },
                    placeholder = { Text(stringResource(R.string.family_switcher_new_family_placeholder), color = kb.subtitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createNewFamily(newFamilyName) },
                    enabled = newFamilyName.isNotBlank() && !isCreating,
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.family_switcher_create_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    enabled = !isCreating,
                ) { Text(stringResource(R.string.family_switcher_cancel)) }
            },
        )
    }
}

@Composable
private fun FamilySwitcherRow(
    family: KBFamilyEntity,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val imageModel = family.heroPhotoLocalPath?.let { path ->
        val file = File(path)
        if (file.exists()) file else family.heroPhotoURL
    } ?: family.heroPhotoURL

    val cardColor = if (isActive) kb.card else kb.rowBackground
    val borderColor = if (isActive) KidBoxAccentBlue.copy(alpha = 0.45f) else kb.divider

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(kb.divider, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = kb.subtitle,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = kb.title,
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.family_switcher_active_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = KidBoxAccentBlue,
                    )
                }
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = KidBoxAccentBlue,
                )
            }
        }
    }
}
