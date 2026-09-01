package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Arancione dell'AI: lo stesso tasto invio di tutte le chat askAI. */
private val AI_ORANGE = Color(0xFFFF6B00)

@Composable
fun FitnessCopilotScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    viewModel: FitnessCopilotViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.actionSummary) {
        state.actionSummary?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeActionSummary()
        }
    }
    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.health_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.fitness_copilot_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.weight(1f))
            if (state.dailyLimit > 0) {
                Text(
                    "${state.usageToday}/${state.dailyLimit}",
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
            } else {
                Spacer(Modifier.width(40.dp))
            }
        }

        // Badge provider, come nelle altre chat askAI.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FITNESS_TINT.copy(alpha = 0.06f))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = FITNESS_TINT,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.fitness_copilot_badge),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.fitness_copilot_disclaimer),
                fontSize = 11.sp,
                color = kb.subtitle,
            )
        }
        HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { IntroBubble(state) }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
            if (state.isLoading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = FITNESS_TINT,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.fitness_copilot_thinking),
                            fontSize = 13.sp,
                            color = kb.subtitle,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (state.messages.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions(state.hasTodaySession).forEach { suggestion ->
                    Text(
                        suggestion,
                        fontSize = 13.sp,
                        color = FITNESS_TINT,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(FITNESS_TINT.copy(alpha = 0.12f))
                            .clickable { viewModel.send(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::setInput,
                placeholder = {
                    Text(stringResource(R.string.ai_write_message), color = kb.subtitle)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FITNESS_TINT,
                    unfocusedBorderColor = kb.subtitle.copy(alpha = 0.3f),
                    focusedContainerColor = kb.card,
                    unfocusedContainerColor = kb.card,
                ),
                maxLines = 5,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (state.canSend) viewModel.send() }),
            )
            Spacer(Modifier.size(8.dp))
            Surface(
                shape = CircleShape,
                color = if (state.canSend) AI_ORANGE else kb.subtitle.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        IconButton(onClick = { viewModel.send() }, enabled = state.canSend) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = if (state.canSend) Color.White else kb.subtitle,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun suggestions(hasTodaySession: Boolean): List<String> = listOf(
    stringResource(
        if (hasTodaySession) {
            R.string.fitness_copilot_suggestion_exercise
        } else {
            R.string.fitness_copilot_suggestion_rest
        },
    ),
    stringResource(R.string.fitness_copilot_suggestion_rain),
    stringResource(R.string.fitness_copilot_suggestion_pain),
)

@Composable
private fun IntroBubble(state: FitnessCopilotUiState) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FITNESS_TINT.copy(alpha = 0.05f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FITNESS_TINT.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = FITNESS_TINT,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.fitness_copilot_hi, state.subjectName),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = kb.title,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.fitness_copilot_access), fontSize = 13.sp, color = kb.subtitle)
        Spacer(Modifier.height(4.dp))
        AccessRow(stringResource(R.string.fitness_copilot_access_sessions, state.sessionCount))
        if (state.safetyNoteCount > 0) {
            AccessRow(stringResource(R.string.fitness_copilot_access_safety, state.safetyNoteCount))
        }
        AccessRow(stringResource(R.string.fitness_copilot_access_health))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.fitness_copilot_intro_more),
            fontSize = 13.sp,
            color = kb.subtitle,
        )
    }
}

@Composable
private fun AccessRow(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = FITNESS_TINT,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = kb.title)
    }
}

@Composable
private fun MessageBubble(message: FitnessCopilotMessage) {
    val kb = MaterialTheme.kidBoxColors
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            message.text,
            fontSize = 15.sp,
            color = if (message.isUser) Color.White else kb.title,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.isUser) FITNESS_TINT else kb.card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(
            timeFormat.format(Date(message.createdAtEpochMillis)),
            fontSize = 10.sp,
            color = kb.subtitle,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
