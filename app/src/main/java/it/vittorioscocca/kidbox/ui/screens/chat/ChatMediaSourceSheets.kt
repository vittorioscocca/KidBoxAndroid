@file:OptIn(ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.runtime.LaunchedEffect
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale

/** Tetto allineato al resto degli allegati chat ("Foto e Video (max 10)"). */
private const val MAX_KIDBOX_SELECTION = 10

/**
 * Chiede da dove prendere il media: galleria del telefono o libreria KidBox.
 *
 * Prima "Foto e Video" apriva direttamente la galleria di sistema, e i media già
 * caricati in KidBox si potevano condividere in chat solo riesportandoli a mano.
 */
@Composable
fun MediaSourceSheet(
    onDismiss: () -> Unit,
    onPickPhoneGallery: () -> Unit,
    onPickKidBox: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.chat_media_source_title),
                color = MaterialTheme.kidBoxColors.title,
                fontWeight = FontWeight.SemiBold,
            )
            SourceRow(
                icon = Icons.Default.Image,
                text = stringResource(R.string.chat_media_source_gallery),
                onClick = onPickPhoneGallery,
            )
            SourceRow(
                icon = Icons.Default.PhotoLibrary,
                text = stringResource(R.string.chat_media_source_kidbox),
                onClick = onPickKidBox,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        Spacer(Modifier.size(14.dp))
        Text(text, color = MaterialTheme.kidBoxColors.title)
    }
}

/**
 * Griglia dei media già presenti in KidBox, con selezione multipla.
 *
 * Mostra la miniatura salvata in locale (`thumbnailBase64` via `downloadURL`):
 * caricare gli originali servirebbe solo al momento dell'invio, e qui
 * appesantirebbe lo scorrimento.
 */
@Composable
fun KidBoxMediaPickerSheet(
    familyId: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    viewModel: ChatKidBoxPickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) { viewModel.bind(familyId) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.kidBoxColors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.chat_kidbox_picker_title),
                color = MaterialTheme.kidBoxColors.title,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            if (photos.isEmpty()) {
                Text(
                    stringResource(R.string.chat_kidbox_picker_empty),
                    color = MaterialTheme.kidBoxColors.subtitle,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(360.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val isSelected = photo.id in selected
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selected = when {
                                        isSelected -> selected - photo.id
                                        // Oltre il tetto il tocco non fa nulla:
                                        // meglio di un errore dopo la conferma.
                                        selected.size >= MAX_KIDBOX_SELECTION -> selected
                                        else -> selected + photo.id
                                    }
                                },
                        ) {
                            // La miniatura cifrata sta in `thumbnailBase64`, non
                            // in `downloadURL` (spesso nullo per i media cifrati):
                            // stessa sorgente usata dalla griglia di Foto e Video.
                            val thumbBytes = remember(photo.thumbnailBase64) {
                                photo.thumbnailBase64?.let {
                                    runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
                                }
                            }
                            if (thumbBytes != null) {
                                AsyncImage(
                                    model = thumbBytes,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(MaterialTheme.kidBoxColors.rowBackground),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.kidBoxColors.subtitle,
                                    )
                                }
                            }
                            if (photo.mimeType.startsWith("video/")) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (selected.isEmpty()) {
                        stringResource(R.string.chat_kidbox_picker_send)
                    } else {
                        "${stringResource(R.string.chat_kidbox_picker_send)} (${selected.size})"
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
