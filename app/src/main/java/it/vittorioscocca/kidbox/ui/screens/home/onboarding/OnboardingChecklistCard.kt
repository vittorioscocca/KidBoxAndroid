package it.vittorioscocca.kidbox.ui.screens.home.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * La card "Per iniziare" in cima alla Home: cinque passi, una barra di
 * avanzamento, e righe che si spuntano da sole quando il passo è fatto.
 *
 * Gemello di `OnboardingChecklistCard.swift`. Le scelte visive non sono
 * decorative:
 *
 * - **La barra e il "N su 5"** esistono perché una lista di cose da fare senza
 *   un traguardo visibile è solo una lista di cose da fare. Vedere il secondo
 *   segmento riempirsi è ciò che porta al terzo.
 * - **Le righe fatte restano**, barrate e in grigio, invece di sparire. Sono la
 *   metà del valore della card: la parte già percorsa.
 * - **La chiusura è sempre disponibile.** Un suggerimento che non si può
 *   togliere smette di essere un suggerimento.
 */
@Composable
fun OnboardingChecklistCard(
    state: OnboardingChecklistUiState,
    onSelect: (OnboardingStep) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    val target = if (state.totalCount > 0) {
        state.completedCount.toFloat() / state.totalCount.toFloat()
    } else {
        0f
    }
    val progress by animateFloatAsState(targetValue = target, label = "onboardingProgress")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = kb.card,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (state.isCelebrating) {
                            R.string.onboarding_checklist_title_done
                        } else {
                            R.string.onboarding_checklist_title
                        }
                    ),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = kb.title,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.onboarding_checklist_progress,
                        state.completedCount,
                        state.totalCount,
                    ),
                    fontSize = 15.sp,
                    color = kb.subtitle,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.onboarding_checklist_hide),
                        tint = kb.subtitle,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // La barra è decorativa per lo screen reader: il "N su M" accanto al
            // titolo dice già la stessa cosa, e leggerla due volte è rumore.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(kb.divider)
                    .clearAndSetSemantics { },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(kb.title),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            state.rows.forEach { row ->
                ChecklistRow(row = row, onClick = { onSelect(row.step) })
            }

            if (state.isCelebrating) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_checklist_celebration),
                    fontSize = 13.sp,
                    color = kb.subtitle,
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(row: OnboardingChecklistRow, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val title = stringResource(row.step.titleRes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Un passo chiuso non è più un pulsante: toccarlo non deve rimandare
            // l'utente dove è già stato.
            .clickable(enabled = !row.isComplete, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (row.isComplete) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = kb.title,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, kb.subtitle.copy(alpha = 0.45f), CircleShape),
            )
        }

        Text(
            text = title,
            fontSize = 15.sp,
            color = if (row.isComplete) kb.subtitle else kb.title,
            textDecoration = if (row.isComplete) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .semanticsLabel(title),
        )

        if (!row.isComplete) {
            Icon(
                imageVector = row.step.icon,
                contentDescription = null,
                tint = row.step.tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(Modifier.clearAndSetSemantics { contentDescription = label })

/**
 * L'icona vive qui e non nell'enum perché è una scelta di presentazione: il
 * catalogo dei passi resta leggibile senza dipendere da Compose.
 */
private val OnboardingStep.icon: ImageVector
    get() = when (this) {
        OnboardingStep.NOTIFICATIONS -> Icons.Filled.NotificationsActive
        OnboardingStep.INVITE -> Icons.Filled.PersonAdd
        OnboardingStep.DOCUMENT -> Icons.Filled.Description
        OnboardingStep.PHOTO -> Icons.Filled.Photo
        OnboardingStep.CALENDAR_EVENT -> Icons.Filled.Event
        OnboardingStep.EXPENSE -> Icons.Filled.Payments
        OnboardingStep.NOTE -> Icons.Filled.EditNote
        OnboardingStep.GROCERY -> Icons.Filled.ShoppingCart
    }
