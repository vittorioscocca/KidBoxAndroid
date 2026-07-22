package it.vittorioscocca.kidbox.ui.share

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.R

@Composable
fun ShareBottomSheet(
    contentType: ShareContentType,
    title: String,
    onTitleChange: (String) -> Unit,
    suggestions: List<ShareDestination>,
    selectedDestination: ShareDestination,
    aiSuggestedDestination: ShareDestination?,
    isSubmitting: Boolean,
    onSelectDestination: (ShareDestination) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 5.dp)
                    .background(Color(0x33000000), RoundedCornerShape(99.dp)),
            )
            Text(stringResource(R.string.share_sheet_title), style = MaterialTheme.typography.titleLarge)
            SharePreview(contentType)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.share_sheet_title_field_label)) },
                maxLines = 1,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { destination ->
                    val aiHighlighted = aiSuggestedDestination == destination
                    AssistChip(
                        onClick = { onSelectDestination(destination) },
                        label = { Text(stringResource(destination.labelRes)) },
                        leadingIcon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when {
                                selectedDestination == destination -> Color(0xFFF26010)
                                aiHighlighted -> Color(0xFFFFD8C2)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            labelColor = if (selectedDestination == destination) Color.White else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF26010)),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(stringResource(R.string.share_sheet_confirm))
                }
            }
            TextButton(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onCancel, enabled = !isSubmitting) {
                Text(stringResource(R.string.share_sheet_cancel))
            }
        }
    }
}

@Composable
private fun SharePreview(contentType: ShareContentType) {
    when (contentType) {
        is ShareContentType.TextContent -> {
            PreviewText(contentType.text)
        }
        is ShareContentType.UrlContent -> {
            PreviewText(contentType.url)
        }
        is ShareContentType.ImageContent -> {
            PreviewImage(contentType.uri)
        }
        is ShareContentType.VideoContent -> {
            PreviewFileRow(name = stringResource(R.string.share_preview_video_name), subtitle = contentType.uri.lastPathSegment.orEmpty())
        }
        is ShareContentType.PdfContent -> {
            PreviewFileRow(name = stringResource(R.string.share_preview_pdf_name), subtitle = contentType.uri.lastPathSegment.orEmpty())
        }
        is ShareContentType.FileContent -> {
            PreviewFileRow(name = stringResource(R.string.share_preview_file_name), subtitle = contentType.uri.lastPathSegment ?: contentType.mimeType)
        }
        ShareContentType.Unknown -> {
            PreviewFileRow(
                name = stringResource(R.string.share_preview_unknown_name),
                subtitle = stringResource(R.string.share_preview_unknown_subtitle),
            )
        }
    }
}

@Composable
private fun PreviewText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            text = text.trim(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PreviewImage(uri: Uri) {
    AsyncImage(
        model = uri,
        contentDescription = stringResource(R.string.share_preview_image_desc),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun PreviewFileRow(name: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Description, contentDescription = null)
        Spacer(Modifier.size(10.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

