package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.data.local.TravelAgeGroup
import it.vittorioscocca.kidbox.data.local.TravelPace
import it.vittorioscocca.kidbox.data.local.TravelProfile
import it.vittorioscocca.kidbox.data.local.TravelStyle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val TravelAccent = Color(0xFFF2611A)

@Composable
fun TravelOnboardingScreen(
    onComplete: (TravelProfile) -> Unit,
    onExit: () -> Unit,
    /**
     * Contenuto opzionale in cima alle domande. Serve all'avviso "serve un piano
     * a pagamento": la configurazione iniziale è la prima cosa che vede un utente
     * Free, e senza questo l'avviso resterebbe sepolto dietro tre domande.
     */
    header: (@Composable () -> Unit)? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    var step by remember { mutableIntStateOf(0) }
    var selectedStyles by remember { mutableStateOf(setOf<TravelStyle>()) }
    var selectedPace by remember { mutableStateOf<TravelPace?>(null) }
    var selectedAge by remember { mutableStateOf<TravelAgeGroup?>(null) }

    val canContinue = when (step) {
        0 -> selectedStyles.isNotEmpty()
        1 -> selectedPace != null
        else -> selectedAge != null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Al primo step «Indietro» esce dalla sezione Viaggi: senza, la
            // configurazione iniziale non ha alcuna via d'uscita.
            TextButton(onClick = { if (step > 0) step -= 1 else onExit() }) {
                Text(stringResource(R.string.travel_back), color = kb.title)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("${step + 1} di 3", color = kb.subtitle, fontSize = 14.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(kb.subtitle.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((step + 1) / 3f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TravelAccent),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp),
        ) {
            if (header != null) {
                header()
                Spacer(modifier = Modifier.height(20.dp))
            }

            when (step) {
                0 -> {
                    Text(stringResource(R.string.travel_style_q), fontWeight = FontWeight.Bold, fontSize = 28.sp, color = kb.title)
                    Text(
                        stringResource(R.string.travel_select_all_interest),
                        color = kb.subtitle,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    TravelStyle.entries.forEach { style ->
                        StyleRow(
                            style = style,
                            selected = style in selectedStyles,
                            onToggle = {
                                selectedStyles = if (style in selectedStyles) {
                                    selectedStyles - style
                                } else {
                                    selectedStyles + style
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                1 -> {
                    Text(stringResource(R.string.travel_how_travel_q), fontWeight = FontWeight.Bold, fontSize = 28.sp, color = kb.title)
                    Text(
                        stringResource(R.string.travel_pace),
                        color = kb.subtitle,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    TravelPace.entries.forEach { pace ->
                        PaceRow(
                            pace = pace,
                            selected = selectedPace == pace,
                            onClick = { selectedPace = pace },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                else -> {
                    Text(stringResource(R.string.travel_age_q), fontWeight = FontWeight.Bold, fontSize = 28.sp, color = kb.title)
                    Text(
                        stringResource(R.string.travel_age_hint),
                        color = kb.subtitle,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    val groups = TravelAgeGroup.entries
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AgeCard(groups[0], selectedAge == groups[0], { selectedAge = groups[0] }, Modifier.weight(1f))
                        AgeCard(groups[1], selectedAge == groups[1], { selectedAge = groups[1] }, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AgeCard(groups[2], selectedAge == groups[2], { selectedAge = groups[2] }, Modifier.weight(1f))
                        AgeCard(groups[3], selectedAge == groups[3], { selectedAge = groups[3] }, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.travel_almost_done), fontWeight = FontWeight.Bold, color = kb.title)
                            Text(
                                stringResource(R.string.travel_prefs_saved),
                                color = kb.subtitle,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (step < 2) {
                    step += 1
                } else {
                    val pace = selectedPace ?: return@Button
                    val age = selectedAge ?: return@Button
                    onComplete(TravelProfile(selectedStyles.toList(), pace, age))
                }
            },
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TravelAccent,
                disabledContainerColor = kb.subtitle.copy(alpha = 0.25f),
            ),
        ) {
            Text(stringResource(R.string.travel_continue), modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun StyleRow(style: TravelStyle, selected: Boolean, onToggle: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) TravelAccent else kb.subtitle.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(style.emoji, fontSize = 28.sp, modifier = Modifier.width(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(style.titleRes), fontWeight = FontWeight.SemiBold, color = kb.title)
                Text(stringResource(style.subtitleRes), fontSize = 12.sp, color = kb.subtitle)
            }
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) TravelAccent else kb.subtitle.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun PaceRow(pace: TravelPace, selected: Boolean, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val icon = when (pace) {
        TravelPace.CHILL -> Icons.Filled.Eco
        TravelPace.BALANCED -> Icons.Filled.Balance
        TravelPace.PACKED -> Icons.Filled.Bolt
    }
    val tint = when (pace) {
        TravelPace.CHILL -> Color(0xFF4DA673)
        TravelPace.BALANCED -> Color(0xFF737880)
        TravelPace.PACKED -> TravelAccent
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) TravelAccent else kb.subtitle.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(kb.subtitle.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(stringResource(pace.titleRes), fontWeight = FontWeight.SemiBold, color = kb.title)
                Text(stringResource(pace.line1Res), fontSize = 12.sp, color = kb.subtitle)
                Text(stringResource(pace.line2Res), fontSize = 12.sp, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun AgeCard(
    group: TravelAgeGroup,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) TravelAccent else kb.subtitle.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(group.emoji, fontSize = 32.sp)
            Text(group.raw, fontWeight = FontWeight.Bold, color = kb.title, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(group.subtitleRes),
                fontSize = 12.sp,
                color = kb.subtitle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
