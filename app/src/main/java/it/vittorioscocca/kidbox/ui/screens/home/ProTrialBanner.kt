package it.vittorioscocca.kidbox.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.repository.KBTrialAskResult
import it.vittorioscocca.kidbox.data.repository.KBTrialOfferUi
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val TrialTint = Color(0xFF2563EB)

/**
 * Banner in Home durante la prova Pro: quanti giorni restano e un tocco per
 * vedere i piani. La prova la concede il server (functions/proTrial.js).
 * Gemello di ProTrialBanner.swift su iOS.
 */
@Composable
fun ProTrialBanner(daysLeft: Int, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TrialTint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = TrialTint, modifier = Modifier.size(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (daysLeft == 1) {
                    stringResource(R.string.trial_home_title_last)
                } else {
                    stringResource(R.string.trial_home_title_days, daysLeft)
                },
                color = kb.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.trial_home_body),
                color = kb.subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = kb.subtitle,
        )
    }
}

/** Da dove arriva l'utente, per il titolo della card della prova. */
private enum class TrialOrigin { AI_LIMIT, MEAL_PLAN, FITNESS_PLAN, TRAVEL, OTHER }

private fun trialOrigin(triggerFeature: String): TrialOrigin = when (triggerFeature) {
    // "wallet" è l'analisi AI dei documenti del Wallet, bloccata dai messaggi finiti.
    "ai_lock", "ai_chat_lock", "wallet" -> TrialOrigin.AI_LIMIT
    "meal_plan_lock" -> TrialOrigin.MEAL_PLAN
    "fitness_plan_lock" -> TrialOrigin.FITNESS_PLAN
    "travel_lock" -> TrialOrigin.TRAVEL
    else -> if (triggerFeature.startsWith("ai_upgrade_")) TrialOrigin.AI_LIMIT else TrialOrigin.OTHER
}

/**
 * Offerta della prova Pro, in Spazio e in Piani. Si mostra solo quando il
 * server dice che la prova spetta ([KBTrialOfferUi.isVisible]): la prova non
 * parte più da sola.
 * - al proprietario: pulsante «Prova Pro per N giorni»;
 * - agli altri membri, se il proprietario può ancora attivarla: «Chiedi di
 *   attivarla», che gli manda una push.
 * Il titolo cambia con l'origine: chi arriva dai messaggi AI finiti o da un
 * pianificatore bloccato legge la cosa che voleva fare. Gemella di
 * ProTrialOfferCard su iOS.
 */
@Composable
fun ProTrialOfferCard(
    offer: KBTrialOfferUi,
    triggerFeature: String,
    onStart: () -> Unit,
    onAskOwner: () -> Unit,
    onDismissFailure: () -> Unit,
    onDismissAskResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    val isOwner = offer.ownerDays != null
    val days = offer.ownerDays ?: offer.askOwnerDays ?: return

    if (offer.startFailed) {
        AlertDialog(
            onDismissRequest = onDismissFailure,
            title = { Text(stringResource(R.string.trial_offer_error_title)) },
            text = { Text(stringResource(R.string.trial_offer_error_body)) },
            confirmButton = {
                TextButton(onClick = onDismissFailure) { Text(stringResource(R.string.subscription_ok)) }
            },
        )
    }
    offer.askResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissAskResult,
            title = { Text(stringResource(R.string.trial_offer_error_title)) },
            text = {
                Text(
                    stringResource(
                        when (result) {
                            KBTrialAskResult.SENT -> R.string.trial_offer_ask_sent
                            KBTrialAskResult.NOT_DELIVERED -> R.string.trial_offer_ask_not_delivered
                            KBTrialAskResult.FAILED -> R.string.trial_offer_ask_error
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissAskResult) { Text(stringResource(R.string.subscription_ok)) }
            },
        )
    }

    val origin = trialOrigin(triggerFeature)
    val title = when (origin) {
        TrialOrigin.AI_LIMIT -> stringResource(R.string.trial_offer_title_ai)
        TrialOrigin.MEAL_PLAN -> stringResource(R.string.trial_offer_title_meal)
        TrialOrigin.FITNESS_PLAN -> stringResource(R.string.trial_offer_title_fitness)
        TrialOrigin.TRAVEL -> stringResource(R.string.trial_offer_title_travel)
        TrialOrigin.OTHER -> stringResource(R.string.trial_offer_title, days)
    }
    val body = when {
        !isOwner -> stringResource(R.string.trial_offer_body_member, days)
        origin == TrialOrigin.AI_LIMIT -> stringResource(R.string.trial_offer_body_ai, offer.aiLimit, days)
        origin == TrialOrigin.OTHER -> stringResource(R.string.trial_offer_body, days)
        else -> stringResource(R.string.trial_offer_body_planner, days)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TrialTint.copy(alpha = 0.10f))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = TrialTint, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = kb.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(body, color = kb.subtitle, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            isOwner -> TrialActionButton(
                label = stringResource(R.string.trial_offer_button, days),
                busy = offer.isStarting,
                onClick = onStart,
            )
            offer.ownerAsked -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TrialTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.trial_offer_asked), color = TrialTint, fontWeight = FontWeight.SemiBold)
            }
            else -> TrialActionButton(
                label = stringResource(R.string.trial_offer_ask_button),
                busy = offer.isAsking,
                onClick = onAskOwner,
            )
        }
    }
}

@Composable
private fun TrialActionButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TrialTint, contentColor = Color.White),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
