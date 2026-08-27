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
import androidx.compose.material.icons.filled.Share
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
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.components.KidBoxChip
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsFeature
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.CircularProgressIndicator
import it.vittorioscocca.kidbox.ui.util.visibilityChipLabel

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
    val context = LocalContext.current
    val view = LocalView.current
    var showVisibilityPick by remember { mutableStateOf(false) }
    var showVisibilityLocked by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, noteId, isNewNote) {
        viewModel.bind(familyId, noteId, isNewRoute = isNewNote)
    }

    // Solo per note esistenti: aprire il form di una nota nuova non è una lettura.
    LaunchedEffect(state.noteId, state.isNewRoute, state.noteCreatedBy) {
        if (state.isNewRoute || state.noteId.isBlank()) return@LaunchedEffect
        KBAnalytics.logRetrieval(
            feature = KBAnalyticsFeature.NOTE,
            uploaderUid = state.noteCreatedBy,
            createdAtEpochMillis = state.noteCreatedAtEpochMillis,
            entryPoint = KBAnalyticsOrigin.consume(),
        )
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (state.noteCreatedBy.isNotBlank() && state.noteCreatedBy != currentUid) {
            AppAnalytics.contentSharedRead(context, "notes")
        }
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
                text = stringResource(R.string.notes_detail_title),
                color = kb.title,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Come su iOS: la condivisione resta sempre visibile (disabilitata
                // se la nota è vuota), la spunta compare solo con modifiche non salvate.
                IconButton(
                    onClick = { shareNote(context, state.title, state.body) },
                    enabled = state.title.htmlToPlainText().isNotBlank() || state.body.htmlToPlainText().isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.notes_detail_share_cd),
                        tint = kb.title,
                    )
                }
                // Slot a larghezza fissa per non far saltare gli altri bottoni
                // quando la spunta scompare.
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isDirty) {
                        IconButton(
                            onClick = {
                                viewModel.save(onDone = {})
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                            },
                            enabled = !state.isSaving,
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.notes_detail_save_cd), tint = kb.title)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            KidBoxChip(
                label = visibilityChipLabel(state.visibilityScope),
                onClick = {
                    if (state.canEditVisibility) {
                        showVisibilityPick = true
                    } else {
                        showVisibilityLocked = true
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (state.isLoading) {
            // Nota aperta da notifica e non ancora sincronizzata: mostrare
            // l'editor adesso significherebbe mostrarlo VUOTO, facendo credere
            // che la nota non abbia contenuto. Meglio dichiarare l'attesa.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            RichNoteEditor(
                title = state.title,
                onTitleChange = viewModel::updateTitle,
                body = state.body,
                onBodyChange = viewModel::updateBody,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
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
            scopeSectionTitle = stringResource(R.string.notes_visibility_section_title),
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
            title = { Text(stringResource(R.string.notes_visibility_locked_title)) },
            text = { Text(stringResource(R.string.notes_visibility_locked_message)) },
            confirmButton = {
                TextButton(onClick = { showVisibilityLocked = false }) {
                    Text(stringResource(R.string.notes_ok))
                }
            },
        )
    }
}

/**
 * Stesso comportamento di `presentShareSheet()` su iOS: titolo e corpo in
 * plain text (HTML ripulito), uniti da una riga vuota, condivisi come testo.
 */
private fun shareNote(context: Context, title: String, bodyHtml: String) {
    val plainTitle = title.htmlToPlainText().trim()
    val plainBody = bodyHtml.htmlToPlainText().trim()
    val text = listOf(plainTitle, plainBody).filter { it.isNotBlank() }.joinToString("\n\n")
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
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
