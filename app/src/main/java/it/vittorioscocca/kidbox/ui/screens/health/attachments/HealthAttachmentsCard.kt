package it.vittorioscocca.kidbox.ui.screens.health.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthAttachmentsCard(
    attachments: List<KBDocumentEntity>,
    tintColor: Color,
    isUploading: Boolean,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
    /** Se non `null`, mostra la riga «Da KidBox Documenti» (picker gestito dal chiamante). */
    onPickFromKidBoxDocuments: (() -> Unit)? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<KBDocumentEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ALLEGATI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tintColor,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.weight(1f))
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = tintColor,
                    )
                } else {
                    IconButton(
                        onClick = { showSheet = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Aggiungi allegato",
                            tint = tintColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (attachments.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nessun allegato",
                    fontSize = 13.sp,
                    color = kb.subtitle,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                attachments.forEach { doc ->
                    AttachmentRow(
                        doc = doc,
                        tintColor = tintColor,
                        onClick = { onOpenAttachment(doc) },
                        onDelete = { pendingDelete = doc },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    // Add-attachment bottom sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = kb.background,
            dragHandle = {
                val hKb = MaterialTheme.kidBoxColors
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(hKb.subtitle.copy(alpha = 0.35f)),
                    )
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    "Aggiungi allegato",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = tintColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                AttachmentSourceRow(
                    label = "Scatta foto",
                    icon = Icons.Default.PhotoCamera,
                    tintColor = tintColor,
                    kb = kb,
                    showLeadingDivider = false,
                    onClick = { showSheet = false; onTakePhoto() },
                )
                AttachmentSourceRow(
                    label = "Scegli dalla libreria",
                    icon = Icons.Default.PhotoLibrary,
                    tintColor = tintColor,
                    kb = kb,
                    showLeadingDivider = true,
                    onClick = { showSheet = false; onPickPhoto() },
                )
                AttachmentSourceRow(
                    label = "File del telefono",
                    icon = Icons.Default.InsertDriveFile,
                    tintColor = tintColor,
                    kb = kb,
                    showLeadingDivider = true,
                    onClick = { showSheet = false; onPickFile() },
                )
                if (onPickFromKidBoxDocuments != null) {
                    AttachmentSourceRow(
                        label = "Da KidBox Documenti",
                        icon = Icons.Default.Folder,
                        tintColor = tintColor,
                        kb = kb,
                        showLeadingDivider = true,
                        onClick = {
                            showSheet = false
                            onPickFromKidBoxDocuments()
                        },
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Elimina allegato?") },
            text = { Text("\"${doc.title}\" verrà eliminato definitivamente.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAttachment(doc)
                    pendingDelete = null
                }) {
                    Text("Elimina", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun AttachmentRow(
    doc: KBDocumentEntity,
    tintColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tintColor.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // File type icon / thumbnail
        FileIcon(doc = doc, tintColor = tintColor)

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                doc.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = kb.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatFileSize(doc.fileSize),
                fontSize = 11.sp,
                color = kb.subtitle,
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Elimina",
                tint = kb.subtitle,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FileIcon(doc: KBDocumentEntity, tintColor: Color) {
    val size = 36.dp
    when {
        doc.mimeType.startsWith("image/") -> {
            val localFile = doc.localPath?.let { File(it) }?.takeIf { it.exists() }
            if (localFile != null) {
                AsyncImage(
                    model = localFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tintColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = tintColor, modifier = Modifier.size(20.dp))
                }
            }
        }
        doc.mimeType == "application/pdf" -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFD32F2F).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tintColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = tintColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AttachmentSourceRow(
    label: String,
    icon: ImageVector,
    tintColor: Color,
    kb: KidBoxColorScheme,
    showLeadingDivider: Boolean,
    onClick: () -> Unit,
) {
    if (showLeadingDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = kb.subtitle.copy(alpha = 0.12f),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tintColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            fontSize = 15.sp,
            color = kb.title,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = kb.subtitle.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024f * 1024f))
}
