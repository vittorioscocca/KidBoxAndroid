package it.vittorioscocca.kidbox.ui.subscription

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.UpgradeMessageStore
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics

@Composable
fun PlansScreen(
    onDismiss: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val activity = context as? Activity
    val uriHandler = LocalUriHandler.current
    val contextualMessage = remember { UpgradeMessageStore.consume() }

    LaunchedEffect(Unit) {
        viewModel.loadPlan()
        AppAnalytics.paywallShown(
            context,
            triggerFeature = UpgradeMessageStore.consumeTrigger(),
            planShown = "both",
        )
    }

    // Solo chi ha creato la famiglia può abbonarsi: il tocco su "Abbonati"
    // degli altri membri finisce qui invece che in un acquisto rifiutato.
    var mostraAvvisoCreatore by remember { mutableStateOf(false) }
    if (mostraAvvisoCreatore) {
        AlertDialog(
            onDismissRequest = { mostraAvvisoCreatore = false },
            title = { Text(stringResource(R.string.subscription_owner_managed_title)) },
            text = { Text(stringResource(R.string.subscription_owner_managed_body)) },
            confirmButton = {
                TextButton(onClick = { mostraAvvisoCreatore = false }) { Text(stringResource(R.string.subscription_ok)) }
            },
        )
    }

    state.purchaseError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.subscription_purchase_error_title)) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.subscription_ok)) }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.subscription_back_desc),
                tint = kb.title,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.subscription_plans_title), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
        Text(
            stringResource(R.string.subscription_plans_subtitle),
            color = kb.subtitle,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (contextualMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B00).copy(alpha = 0.10f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        contextualMessage,
                        color = kb.title,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        PlanCard(
            plan = KBPlan.FREE,
            isCurrent = state.currentPlan == KBPlan.FREE,
            badgeColor = Color(0xFF9CA3AF),
            buttonLabel = null,
            onButtonClick = null,
        )
        Spacer(modifier = Modifier.height(12.dp))

        PlanCard(
            plan = KBPlan.PRO,
            isCurrent = state.currentPlan == KBPlan.PRO,
            badgeColor = Color(0xFF2563EB),
            // Il pulsante c'è anche per chi non ha creato la famiglia: nasconderlo
            // lasciava senza risposta la domanda "perché non posso abbonarmi?".
            // Al tocco arriva il motivo, non un acquisto che fallirebbe.
            buttonLabel = if (state.currentPlan != KBPlan.PRO) stringResource(R.string.subscription_subscribe) else null,
            onButtonClick = {
                if (!state.isFamilyOwner) {
                    mostraAvvisoCreatore = true
                } else if (activity != null) {
                    viewModel.purchase(KBPlan.PRO, activity)
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        PlanCard(
            plan = KBPlan.MAX,
            isCurrent = state.currentPlan == KBPlan.MAX,
            badgeColor = Color(0xFF7C3AED),
            buttonLabel = if (state.currentPlan != KBPlan.MAX) stringResource(R.string.subscription_subscribe) else null,
            onButtonClick = {
                if (!state.isFamilyOwner) {
                    mostraAvvisoCreatore = true
                } else if (activity != null) {
                    viewModel.purchase(KBPlan.MAX, activity)
                }
            },
        )

        Spacer(modifier = Modifier.height(14.dp))
        run {
            Button(
                onClick = {
                    if (!state.isFamilyOwner) mostraAvvisoCreatore = true else viewModel.restorePurchases()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.subscription_restore_purchases))
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            stringResource(R.string.subscription_auto_renew_notice),
            fontSize = 12.sp,
            color = kb.subtitle,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            Text(
                stringResource(R.string.subscription_terms_of_service),
                color = Color(0xFFFF6B00),
                modifier = Modifier.clickable { uriHandler.openUri("https://vittorioscocca.github.io/KidBox/") },
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                stringResource(R.string.subscription_privacy_policy),
                color = Color(0xFFFF6B00),
                modifier = Modifier.clickable { uriHandler.openUri("https://vittorioscocca.github.io/KidBox/") },
            )
        }

        if (state.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PlanCard(
    plan: KBPlan,
    isCurrent: Boolean,
    badgeColor: Color,
    buttonLabel: String?,
    onButtonClick: (() -> Unit)?,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onButtonClick != null && !buttonLabel.isNullOrBlank()) {
                    base.clickable(onClick = onButtonClick)
                } else {
                    base
                }
            }
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = if (isCurrent) Color(0xFF22C55E) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            ),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badge = plan.badge
                if (badge.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (isCurrent) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF22C55E))
                }
            }
            Text(plan.displayName, color = kb.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(plan.monthlyPrice, color = kb.subtitle, fontSize = 16.sp)
            if (plan.tagline.isNotBlank()) {
                Text(plan.tagline, color = kb.subtitle, fontSize = 12.sp)
            }
            // Testi e quote arrivano dal catalogo `config/plans`, già localizzati
            // e con i segnaposto risolti (vedi KBPlanCatalog).
            plan.features.forEach { feature ->
                Text(
                    "${feature.icon} ${feature.text}",
                    color = kb.title,
                    fontSize = 14.sp,
                    fontWeight = if (feature.strong) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (!buttonLabel.isNullOrBlank() && onButtonClick != null) {
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(buttonLabel) }
            }
        }
    }
}
