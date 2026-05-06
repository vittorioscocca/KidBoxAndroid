@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ai.AIConsentBottomSheet
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CONSENT_DATE_FMT = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.ITALIAN)
private val DESTRUCTIVE = Color(0xFFD32F2F)
private val CONSENT_GREEN = Color(0xFF059669)

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRevokeConfirm by remember { mutableStateOf(false) }

    if (state.pendingShowConsent) {
        AIConsentBottomSheet(
            onAccept = { viewModel.recordConsent() },
            onDismiss = { viewModel.dismissPendingConsent() },
        )
    }

    if (showRevokeConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text("Revocare il consenso?") },
            text = {
                Text("L'assistente AI verrà disattivato e dovrai accettare di nuovo le condizioni per usarlo.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeConsent()
                    showRevokeConfirm = false
                }) {
                    Text("Revoca", color = DESTRUCTIVE, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = false }) { Text("Annulla") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text("Assistente AI", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = kb.title)
                },
                navigationIcon = {
                    KidBoxHeaderCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        onClick = onBack,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CurrentPlanCard(plan = state.plan)

            AIIntroCard()

            if (!state.plan.includesAI) {
                AILockedBanner()
            } else {
                AIToggleCard(
                    isEnabled = state.isEnabled,
                    consentGiven = state.consentGiven,
                    consentDate = state.consentDate,
                    onToggle = { viewModel.toggleEnabled(it) },
                    onRevokeClick = { showRevokeConfirm = true },
                )

                if (state.isEnabled) {
                    AIUsageCard(
                        usageToday = state.aiUsageToday,
                        dailyLimit = state.plan.aiDailyLimit,
                    )
                }
            }

            AIPrivacyCard()

            WeeklySummaryCard(
                isEnabled = state.isWeeklySummaryEnabled,
                onToggle = { viewModel.toggleWeeklySummary(it) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CurrentPlanCard(plan: KBPlan) {
    val gradientColors = when (plan) {
        KBPlan.MAX -> listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
        KBPlan.PRO -> listOf(Color(0xFF2563EB), Color(0xFF0EA5E9))
        KBPlan.FREE -> listOf(Color(0xFF6B7280), Color(0xFF9CA3AF))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(plan.planIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    "Piano attuale",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    plan.displayName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (plan.includesAI) {
                    Text(
                        if (plan.aiDailyLimit == Int.MAX_VALUE) "AI illimitata" else "AI: ${plan.aiDailyLimit} messaggi/giorno",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        "AI non inclusa",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AIIntroCard() {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "  Assistente AI Medico",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = kb.title,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "L'assistente analizza i dati sanitari che scegli di condividere " +
                    "e risponde alle tue domande sulla salute del tuo bambino.",
                fontSize = 14.sp,
                color = kb.subtitle,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Le risposte sono generate da Claude di Anthropic e non sostituiscono " +
                    "il parere del tuo pediatra.",
                fontSize = 13.sp,
                color = kb.subtitle,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun AIToggleCard(
    isEnabled: Boolean,
    consentGiven: Boolean,
    consentDate: Long?,
    onToggle: (Boolean) -> Unit,
    onRevokeClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Abilita Assistente AI", fontSize = 16.sp, color = kb.title, fontWeight = FontWeight.Medium)
                    if (!consentGiven) {
                        Text("Richiede accettazione consenso", fontSize = 12.sp, color = kb.subtitle)
                    }
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }

            if (consentGiven && consentDate != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = kb.divider)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = CONSENT_GREEN,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "  Consenso fornito il ${CONSENT_DATE_FMT.format(Date(consentDate))}",
                        color = CONSENT_GREEN,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = kb.divider)
                TextButton(
                    onClick = onRevokeClick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("Revoca consenso e disabilita AI", color = DESTRUCTIVE, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AIUsageCard(usageToday: Int, dailyLimit: Int) {
    val kb = MaterialTheme.kidBoxColors
    val isUnlimited = dailyLimit == Int.MAX_VALUE
    val progress = if (isUnlimited) 0f else usageToday.toFloat() / dailyLimit.coerceAtLeast(1)
    val progressColor = when {
        isUnlimited -> Color(0xFF3B82F6)
        progress >= 0.9f -> Color(0xFFEF4444)
        progress >= 0.7f -> Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6)
    }

    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = progressColor, modifier = Modifier.size(18.dp))
                Text(
                    "  Utilizzo oggi",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = kb.title,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (isUnlimited) {
                Text(
                    "$usageToday messaggi inviati · limite illimitato",
                    fontSize = 14.sp,
                    color = kb.subtitle,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("$usageToday / $dailyLimit messaggi", fontSize = 14.sp, color = kb.title)
                    Text(
                        "${dailyLimit - usageToday} rimanenti",
                        fontSize = 13.sp,
                        color = kb.subtitle,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.15f),
                )
            }
        }
    }
}

@Composable
private fun AIPrivacyCard() {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                Text("  Privacy e sicurezza", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.title)
            }
            Spacer(Modifier.height(10.dp))
            PrivacyLine("I dati sanitari sono cifrati prima dell'invio.")
            PrivacyLine("Le risposte AI non vengono memorizzate sui server KidBox.")
            PrivacyLine("Puoi revocare il consenso in qualsiasi momento.")
            PrivacyLine("I dati sono trattati da Anthropic secondo la loro Privacy Policy.")
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·  ", fontSize = 13.sp, color = kb.subtitle)
        Text(text, fontSize = 13.sp, color = kb.subtitle, lineHeight = 18.sp)
    }
}

@Composable
private fun WeeklySummaryCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Riepilogo settimanale AI", fontSize = 15.sp, color = kb.title, fontWeight = FontWeight.Medium)
                    Text("Ricevi un riassunto AI ogni settimana", fontSize = 12.sp, color = kb.subtitle)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun AILockedBanner() {
    val kb = MaterialTheme.kidBoxColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    "Assistente AI non incluso",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = kb.title,
                )
                Text(
                    "Aggiorna al piano Pro o Max per accedere all'assistente AI medico.",
                    fontSize = 13.sp,
                    color = kb.subtitle,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { content() }
}
