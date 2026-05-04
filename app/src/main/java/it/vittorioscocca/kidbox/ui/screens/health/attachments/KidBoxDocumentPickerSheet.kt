package it.vittorioscocca.kidbox.ui.screens.health.attachments

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val PickerTint = Color(0xFF9573D9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidBoxDocumentPickerSheet(
    familyId: String,
    onDismiss: () -> Unit,
    onPickedUri: (Uri) -> Unit,
    viewModel: KidBoxDocumentPickerViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(familyId) { viewModel.bindFamily(familyId) }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = kb.background,
        dragHandle = {
            val handleKb = MaterialTheme.kidBoxColors
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(handleKb.subtitle.copy(alpha = 0.35f)),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.canGoUp) {
                        TextButton(onClick = { viewModel.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = PickerTint,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("Indietro", color = PickerTint, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    Text(
                        state.title,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = kb.title,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Annulla", color = PickerTint, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Scegli da KidBox",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = kb.subtitle,
                )
                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                ) {
                    if (state.folders.isEmpty() && state.documents.isEmpty() && !state.isBusy) {
                        item {
                            Text(
                                "Cartella vuota",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = kb.subtitle,
                            )
                        }
                    }
                    items(state.folders, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openFolder(folder) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = PickerTint, modifier = Modifier.size(26.dp))
                            Text(folder.title, fontSize = 15.sp, color = kb.title, modifier = Modifier.weight(1f))
                            Text("›", color = kb.subtitle, fontSize = 20.sp)
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp),
                            color = kb.subtitle.copy(alpha = 0.12f),
                        )
                    }
                    items(state.documents, key = { it.id }) { doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.pickDocument(doc) { result ->
                                        result.onSuccess { uri ->
                                            onPickedUri(uri)
                                            onDismiss()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                docIcon(doc),
                                contentDescription = null,
                                tint = PickerTint,
                                modifier = Modifier.size(26.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    doc.title.ifBlank { doc.fileName },
                                    fontSize = 15.sp,
                                    color = kb.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(formatDocSize(doc.fileSize), fontSize = 12.sp, color = kb.subtitle)
                            }
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp),
                            color = kb.subtitle.copy(alpha = 0.12f),
                        )
                    }
                }
            }
            if (state.isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PickerTint)
                        Spacer(Modifier.height(12.dp))
                        Text("Preparazione allegato…", fontSize = 14.sp, color = kb.subtitle)
                    }
                }
            }
        }
    }
}

private fun docIcon(doc: KBDocumentEntity): ImageVector =
    when {
        doc.mimeType.contains("pdf", ignoreCase = true) -> Icons.Default.Description
        doc.mimeType.startsWith("image/", ignoreCase = true) -> Icons.Default.Image
        else -> Icons.Default.InsertDriveFile
    }

private fun formatDocSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024f * 1024f))
}
