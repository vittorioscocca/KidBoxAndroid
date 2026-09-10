package it.vittorioscocca.kidbox.ui.screens.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun AlexaSettingsScreen(
    onBack: () -> Unit,
    viewModel: AlexaSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    var showUnlinkDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_alexa_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
        )
        Spacer(Modifier.height(16.dp))

        StatusCard(state = state)

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_alexa_intro),
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
        )

        // Chi ha già collegato in famiglia va detto sempre, anche a chi ha il
        // proprio collegamento: è l'unico posto da cui si capisce quanti account
        // Amazon sono agganciati alla lista.
        if (state.otherLinks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_alexa_family_section))
            state.otherLinks.forEach { link ->
                Spacer(Modifier.height(8.dp))
                FamilyLinkCard(link)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_alexa_family_note),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Chi ha l'account collegato può già dettare: le frasi vengono prima,
        // perché sono quello che funziona adesso. I passi restano sotto per chi
        // deve ancora far riconoscere la propria voce — il caso del secondo
        // membro di casa, che l'account ce l'ha già per riflesso di quello
        // Amazon condiviso e vedeva solo «La tua voce», senza sapere che prima
        // gli serve la skill attiva sul proprio account.
        if (state.linked) {
            SectionTitle(stringResource(R.string.settings_alexa_phrases_section))
            PhrasesCard()
            Spacer(Modifier.height(8.dp))
            // Il promemoria e' l'unico comando che non si esaurisce in una
            // frase, e senza dirlo qui l'utente non si aspetta le due domande
            // e riattacca. Non e' una scelta di stile: AMAZON.SearchQuery, lo
            // slot che regge il testo libero del titolo, non puo' convivere con
            // nessun altro slot nella stessa frase, quindi assegnatario e
            // orario devono per forza arrivare nei turni dopo.
            Text(
                text = stringResource(R.string.settings_alexa_phrases_note),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Il codice serve finché non è legata anche la voce, e serve partendo
        // dalla skill: senza quella attiva sul proprio account Amazon si detta a
        // vuoto e non c'e' modo di capire perche'. Una `PairingCard` sola sulla
        // schermata: due pulsanti genererebbero lo stesso codice a sei cifre e
        // non si capirebbe quale premere.
        if (!state.linked || !state.voiceLinked) {
            SectionTitle(stringResource(R.string.settings_alexa_skill_section))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_alexa_skill_step),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_alexa_skill_note),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(16.dp))

            // Col proprio account già collegato, «2. Collega l'account»
            // contraddice il «Account collegato» in cima alla schermata: resta
            // il passo 2, ma di un'altra cosa.
            SectionTitle(
                stringResource(
                    if (state.linked) {
                        R.string.settings_alexa_voice_step_section
                    } else {
                        R.string.settings_alexa_pairing_section
                    },
                ),
            )
            PairingCard(
                state = state,
                onGenerate = viewModel::generateCode,
                // Con l'account già collegato quel codice non serve più a
                // collegarlo: serve a legare la voce. Il pulsante lo dice, o
                // sembrerebbe di rifare un passo già fatto.
                buttonLabel = if (state.linked) {
                    stringResource(R.string.settings_alexa_voice_cta)
                } else {
                    null
                },
            )
            Spacer(Modifier.height(8.dp))
            if (state.linked) {
                Text(
                    text = stringResource(R.string.settings_alexa_voice_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stringResource(R.string.settings_alexa_pairing_note),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
        }

        if (state.linked) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showUnlinkDialog = true },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_alexa_unlink))
            }
        }

        state.errorMessage?.let { key ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(errorLabel(key)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text(stringResource(R.string.settings_alexa_unlink_confirm_title)) },
            text = { Text(stringResource(R.string.settings_alexa_unlink_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnlinkDialog = false
                    viewModel.unlink()
                }) {
                    Text(stringResource(R.string.settings_alexa_unlink))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) {
                    Text(stringResource(R.string.settings_alexa_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.kidBoxColors.title,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StatusCard(state: AlexaSettingsUiState) {
    val kb = MaterialTheme.kidBoxColors
    // "Non collegata" sarebbe falso se un altro membro l'ha già agganciata: la
    // lista è già raggiungibile dagli Echo di casa.
    val hasOthers = state.otherLinks.isNotEmpty()
    val titleRes = when {
        state.linked -> R.string.settings_alexa_status_linked
        hasOthers -> R.string.settings_alexa_status_family_linked
        else -> R.string.settings_alexa_status_not_linked
    }
    val subtitle = when {
        state.linked && state.voiceLinked -> stringResource(R.string.settings_alexa_status_voice)
        state.linked -> stringResource(R.string.settings_alexa_status_no_voice)
        hasOthers -> stringResource(R.string.settings_alexa_status_not_this_account)
        else -> stringResource(R.string.settings_alexa_status_needs_code)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        state.linked -> Icons.Filled.CheckCircle
                        hasOthers -> Icons.Filled.Group
                        else -> Icons.Filled.Link
                    },
                    contentDescription = null,
                    tint = if (state.linked) Color(0xFF27AE60) else Color(0xFFFF6B00),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = kb.title,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = kb.subtitle,
                    )
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun FamilyLinkCard(link: AlexaFamilyLinkUi) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF27AE60),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = link.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.settings_alexa_family_member_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    color = kb.title,
                )
                Text(
                    text = stringResource(
                        if (link.kind == AlexaLinkKind.VOICE) {
                            R.string.settings_alexa_kind_voice
                        } else {
                            R.string.settings_alexa_kind_account
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = kb.subtitle,
                )
            }
        }
    }
}

@Composable
private fun PairingCard(
    state: AlexaSettingsUiState,
    onGenerate: () -> Unit,
    buttonLabel: String? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val code = state.pairingCode
            if (code == null) {
                Button(onClick = onGenerate, enabled = !state.isGenerating) {
                    Icon(imageVector = Icons.Filled.Pin, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = buttonLabel ?: stringResource(R.string.settings_alexa_generate_code),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (state.isGenerating) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = code,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_alexa_say_to_alexa),
                    style = MaterialTheme.typography.bodySmall,
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_alexa_link_phrase, code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kb.title,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.settings_alexa_expires_in,
                        state.secondsLeft / 60,
                        state.secondsLeft % 60,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = kb.subtitle,
                )
            }
        }
    }
}

@Composable
private fun PhrasesCard() {
    val kb = MaterialTheme.kidBoxColors
    val phrases = listOf(
        R.string.settings_alexa_phrase_add,
        R.string.settings_alexa_phrase_read,
        R.string.settings_alexa_phrase_remove,
        R.string.settings_alexa_phrase_remind,
        R.string.settings_alexa_phrase_open,
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            phrases.forEachIndexed { index, res ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00),
                    )
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kb.title,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

private fun errorLabel(key: String): Int = when (key) {
    AlexaSettingsViewModel.NO_FAMILY -> R.string.settings_alexa_error_no_family
    AlexaSettingsViewModel.CODE_ERROR -> R.string.settings_alexa_error_code
    AlexaSettingsViewModel.UNLINK_ERROR -> R.string.settings_alexa_error_unlink
    AlexaSettingsViewModel.UNEXPECTED -> R.string.settings_alexa_error_unexpected
    else -> R.string.settings_alexa_error_status
}
