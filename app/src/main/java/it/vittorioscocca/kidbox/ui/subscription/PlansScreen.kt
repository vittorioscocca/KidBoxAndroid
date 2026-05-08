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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

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

    LaunchedEffect(Unit) {
        viewModel.loadPlan()
    }

    state.purchaseError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Errore acquisto") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                tint = kb.title,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Piani KidBox", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
        Text(
            "Un solo abbonamento copre tutti i membri.",
            color = kb.subtitle,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))

        PlanCard(
            title = "Free",
            price = "€0 / mese",
            badge = "Gratuito",
            features = listOf("200 MB storage famiglia", "AI non inclusa"),
            isCurrent = state.currentPlan == KBPlan.FREE,
            badgeColor = Color(0xFF9CA3AF),
            buttonLabel = null,
            onButtonClick = null,
        )
        Spacer(modifier = Modifier.height(12.dp))

        PlanCard(
            title = "Pro",
            price = "€4,99/mese",
            badge = "Più popolare",
            features = listOf("5 GB storage famiglia", "30 messaggi AI/giorno", "Sintesi settimanale AI"),
            isCurrent = state.currentPlan == KBPlan.PRO,
            badgeColor = Color(0xFF2563EB),
            buttonLabel = if (state.currentPlan != KBPlan.PRO && state.isFamilyOwner) "Abbonati" else null,
            onButtonClick = {
                if (activity != null) viewModel.purchase(KBPlan.PRO, activity)
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        PlanCard(
            title = "Max",
            price = "€9,99/mese",
            badge = "Migliore",
            features = listOf("20 GB storage famiglia", "100 messaggi AI/giorno", "Sintesi settimanale AI", "Supporto prioritario"),
            isCurrent = state.currentPlan == KBPlan.MAX,
            badgeColor = Color(0xFF7C3AED),
            buttonLabel = if (state.currentPlan != KBPlan.MAX && state.isFamilyOwner) "Abbonati" else null,
            onButtonClick = {
                if (activity != null) viewModel.purchase(KBPlan.MAX, activity)
            },
        )

        Spacer(modifier = Modifier.height(14.dp))
        if (state.isFamilyOwner) {
            Button(
                onClick = viewModel::restorePurchases,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Ripristina acquisti precedenti")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            "Gli abbonamenti si rinnovano automaticamente. Puoi gestirli in Impostazioni > Google Play > Abbonamenti.",
            fontSize = 12.sp,
            color = kb.subtitle,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            Text(
                "Termini di servizio",
                color = Color(0xFFFF6B00),
                modifier = Modifier.clickable { uriHandler.openUri("https://vittorioscocca.github.io/KidBox/") },
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                "Privacy Policy",
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
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    badge: String,
    features: List<String>,
    isCurrent: Boolean,
    badgeColor: Color,
    buttonLabel: String?,
    onButtonClick: (() -> Unit)?,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (isCurrent) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF22C55E))
                }
            }
            Text(title, color = kb.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(price, color = kb.subtitle, fontSize = 16.sp)
            features.forEach { feature ->
                Text("• $feature", color = kb.title, fontSize = 14.sp)
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
