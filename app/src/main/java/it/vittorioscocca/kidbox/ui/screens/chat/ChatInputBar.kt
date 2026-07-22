@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package it.vittorioscocca.kidbox.ui.screens.chat

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

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
    /**
     * Candidati per il picker `@menzione`. Se vuoto (es. la chat ha al massimo
     * due partecipanti) il picker non viene mai mostrato.
     */
    mentionCandidates: List<ChatMentionCandidate> = emptyList(),
    /**
     * Invocata quando l'utente seleziona un candidato dal picker. Riceve il
     * candidato selezionato — il chiamante è responsabile di registrarlo sul
     * ViewModel così da serializzarlo nel campo `mentions` al successivo invio.
     */
    onMentionPicked: (ChatMentionCandidate) -> Unit = {},
) {
    val isTyping = text.isNotBlank()
    var touchStartX by remember { mutableFloatStateOf(0f) }
    var touchStartY by remember { mutableFloatStateOf(0f) }
    var lockRaised by remember { mutableStateOf(false) }
    var cancelRaised by remember { mutableStateOf(false) }

    // TextFieldValue locale per controllare la posizione del cursore.
    // Sincronizziamo il testo esterno → locale solo quando cambia da fuori
    // (es. clear dopo invio), preservando il cursore durante la digitazione normale.
    var tfv by remember { mutableStateOf(TextFieldValue(text, selection = TextRange(text.length))) }
    LaunchedEffect(text) {
        if (tfv.text != text) {
            tfv = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    // Picker @menzioni — calcoliamo la query corrente in base all'ultimo token
    // che inizia con `@` (preceduto da inizio testo o whitespace).
    val mentionSuggestions = remember(text, mentionCandidates) {
        if (mentionCandidates.isEmpty()) emptyList()
        else computeMentionSuggestions(text, mentionCandidates)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.kidBoxColors.card)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        if (mentionSuggestions.isNotEmpty() && !recordingState.isRecording) {
            MentionSuggesterList(
                suggestions = mentionSuggestions,
                onPick = { candidate ->
                    val newText = applyMentionToText(text, candidate)
                    val cursorPos = newText.length
                    tfv = TextFieldValue(newText, selection = TextRange(cursorPos))
                    onTextChange(newText)
                    onMentionPicked(candidate)
                },
            )
            Spacer(Modifier.size(4.dp))
        }
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
                        recordingState.isLocked -> stringResource(R.string.chat_locked)
                        recordingState.isCancelling -> stringResource(R.string.chat_cancel_dots)
                        else -> stringResource(R.string.chat_swipe_hint)
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
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFF6B00)),
            ) {
                IconButton(onClick = onOpenAttachments, enabled = !isSending && !recordingState.isRecording) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFF6B00))
                }
            }

            Spacer(Modifier.size(6.dp))

            OutlinedTextField(
                value = tfv,
                onValueChange = { newTfv ->
                    tfv = newTfv
                    onTextChange(newTfv.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                maxLines = 5,
                enabled = !recordingState.isRecording,
            )

            Spacer(Modifier.size(6.dp))

            Card(
                modifier = Modifier.size(36.dp),
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

/**
 * Mostra l'elenco dei candidati alle `@menzioni` filtrati dalla query corrente.
 * Limita l'elenco a 6 voci per non occupare troppo spazio sopra l'input bar.
 */
@Composable
private fun MentionSuggesterList(
    suggestions: List<ChatMentionCandidate>,
    onPick: (ChatMentionCandidate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.kidBoxColors.surfaceOverlay, RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
    ) {
        suggestions.take(6).forEachIndexed { index, candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0x1AFF6B00), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initialsForMention(candidate.displayName),
                        color = Color(0xFFFF6B00),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = candidate.displayName,
                    color = MaterialTheme.kidBoxColors.title,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = null,
                    tint = MaterialTheme.kidBoxColors.subtitle,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (index < suggestions.lastIndex && index < 5) {
                HorizontalDivider(
                    color = MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.08f),
                    modifier = Modifier.padding(start = 50.dp),
                )
            }
        }
    }
}

private fun initialsForMention(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).take(2)
    return parts.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("")
}

/**
 * Trova l'ultimo token `@partial` nel testo corrente e restituisce la lista
 * filtrata di candidati. Restituisce lista vuota quando:
 *  - non c'è alcun `@` valido (deve essere a inizio testo o preceduto da whitespace);
 *  - il token corrente contiene whitespace (la query è "chiusa");
 *  - la query corrisponde esattamente a un singolo candidato (menzione completata).
 */
private fun computeMentionSuggestions(
    text: String,
    candidates: List<ChatMentionCandidate>,
): List<ChatMentionCandidate> {
    val active = findActiveMentionQuery(text) ?: return emptyList()
    val q = active.query.lowercase()
    val filtered = if (q.isEmpty()) {
        candidates
    } else {
        candidates.filter { it.displayName.lowercase().contains(q) }
    }
    if (filtered.size == 1 && filtered.first().displayName.lowercase() == q) return emptyList()
    return filtered
}

/**
 * Sostituisce il token `@partial` corrente con `@<DisplayName> ` (con spazio
 * finale per consentire di continuare a digitare).
 */
private fun applyMentionToText(text: String, candidate: ChatMentionCandidate): String {
    val active = findActiveMentionQuery(text) ?: return text
    val before = text.substring(0, active.atIndex)
    val after = text.substring(active.endIndex)
    return before + "@" + candidate.displayName + " " + after
}

private data class MentionQuery(val atIndex: Int, val endIndex: Int, val query: String)

private fun findActiveMentionQuery(text: String): MentionQuery? {
    val atIndex = text.lastIndexOf('@')
    if (atIndex < 0) return null
    if (atIndex > 0) {
        val prev = text[atIndex - 1]
        if (!prev.isWhitespace()) return null
    }
    val tail = text.substring(atIndex + 1)
    if (tail.any { it.isWhitespace() }) return null
    return MentionQuery(atIndex = atIndex, endIndex = text.length, query = tail)
}

