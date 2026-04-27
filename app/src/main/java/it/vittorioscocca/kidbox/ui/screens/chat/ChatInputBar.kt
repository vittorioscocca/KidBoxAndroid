@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun ChatInputBar(
    text: String,
    isSending: Boolean,
    recordingState: ChatInputBarUiState,
    recordingTimeLabel: String,
    recordingWaveformBars: List<Int>,
    onTextChange: (String) -> Unit,
    onOpenAttachments: () -> Unit,
    onSendText: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onLockRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTyping = text.isNotBlank()
    var touchStartX by remember { mutableFloatStateOf(0f) }
    var touchStartY by remember { mutableFloatStateOf(0f) }
    var lockRaised by remember { mutableStateOf(false) }
    var cancelRaised by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.kidBoxColors.card)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (recordingState.isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x14FF6B00), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(recordingTimeLabel, color = Color(0xFFFF6B00))
                AdaptiveRecordingWaveformView(
                    samples = recordingWaveformBars,
                    color = Color(0xFFFF6B00),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        recordingState.isLocked -> "Bloccato"
                        recordingState.isCancelling -> "Annulla..."
                        else -> "Scorri su blocca / sx annulla"
                    },
                    color = MaterialTheme.kidBoxColors.subtitle,
                )
            }
            if (recordingState.isLocked) {
                Spacer(Modifier.size(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    IconButton(onClick = onCancelRecording) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = if (recordingState.isPaused) onResumeRecording else onPauseRecording) {
                        Icon(
                            if (recordingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color(0xFFFF6B00),
                        )
                    }
                    IconButton(onClick = onStopRecording) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFFFF6B00))
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFF6B00)),
            ) {
                IconButton(onClick = onOpenAttachments, enabled = !isSending && !recordingState.isRecording) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFF6B00))
                }
            }

            Spacer(Modifier.size(8.dp))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 140.dp),
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Messaggio...") },
                maxLines = 6,
                enabled = !recordingState.isRecording,
            )

            Spacer(Modifier.size(8.dp))

            Card(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isTyping) Color(0xFFFF6B00) else Color(0x1AFF6B00),
                ),
            ) {
                if (recordingState.isLocked) {
                    IconButton(onClick = onStopRecording, enabled = !isSending) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                    }
                } else if (isTyping) {
                    IconButton(onClick = onSendText, enabled = !isSending) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInteropFilter { event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        touchStartX = event.rawX
                                        touchStartY = event.rawY
                                        lockRaised = false
                                        cancelRaised = false
                                        onStartRecording()
                                        true
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        val dx = event.rawX - touchStartX
                                        val dy = event.rawY - touchStartY
                                        if (!lockRaised && dy < -120f) {
                                            lockRaised = true
                                            onLockRecording()
                                        }
                                        if (!cancelRaised && !lockRaised && dx < -120f) {
                                            cancelRaised = true
                                            onCancelRecording()
                                        }
                                        true
                                    }

                                    MotionEvent.ACTION_UP -> {
                                        if (!cancelRaised && !lockRaised) onStopRecording()
                                        true
                                    }

                                    MotionEvent.ACTION_CANCEL -> {
                                        onCancelRecording()
                                        true
                                    }

                                    else -> false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFFFF6B00),
                        )
                    }
                }
            }
        }
    }
}

