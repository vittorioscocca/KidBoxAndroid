package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun NoteDetailScreen(
    familyId: String,
    noteId: String,
    isNewNote: Boolean,
    onBack: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showVisibilityPick by remember { mutableStateOf(false) }
    var showVisibilityLocked by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, noteId, isNewNote) {
        viewModel.bind(familyId, noteId, isNewRoute = isNewNote)
    }

    BackHandler {
        viewModel.saveSilently()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            // L'IME non è gestito dallo Scaffold (vedi MainActivity).
            // imePadding() spinge il contenuto sopra la tastiera quando è aperta.
            .imePadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NoteHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = {
                    viewModel.saveSilently()
                    onBack()
                },
            )
            Text(
                text = "Nota",
                color = kb.title,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            IconButton(
                onClick = {
                    viewModel.save(onDone = {})
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
                enabled = !state.isSaving,
            ) {
                Icon(Icons.Default.Check, contentDescription = "Salva", tint = kb.title)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Card(
                modifier = Modifier.clickable {
                    if (state.canEditVisibility) {
                        showVisibilityPick = true
                    } else {
                        showVisibilityLocked = true
                    }
                },
                shape = RoundedCornerShape(100.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F1E9)),
            ) {
                Text(
                    KBVisibilityScope.chipLabel(state.visibilityScope),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    color = kb.title,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RichNoteEditor(
            title = state.title,
            onTitleChange = viewModel::updateTitle,
            body = state.body,
            onBodyChange = viewModel::updateBody,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        if (!state.errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }
    }

    if (showVisibilityPick) {
        VisibilityPickerBottomSheet(
            currentUid = state.currentUid,
            scopeSectionTitle = "Chi può vedere questa nota",
            membersExcludingSelf = state.pickerMembersExcludingSelf,
            initialScope = state.visibilityScope,
            initialMemberIds = state.visibilityMemberIds,
            onDismiss = { showVisibilityPick = false },
            onConfirmed = { scope, ids ->
                viewModel.setVisibilityConfirmed(scope, ids)
                showVisibilityPick = false
            },
        )
    }

    if (showVisibilityLocked) {
        AlertDialog(
            onDismissRequest = { showVisibilityLocked = false },
            title = { Text("Visibilità bloccata") },
            text = { Text("Solo chi ha creato la nota può modificare la visibilità.") },
            confirmButton = {
                TextButton(onClick = { showVisibilityLocked = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun NoteHeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}
