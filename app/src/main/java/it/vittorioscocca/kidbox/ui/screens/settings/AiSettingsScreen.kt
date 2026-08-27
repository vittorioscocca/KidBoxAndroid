@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.AIConsentBottomSheet
import it.vittorioscocca.kidbox.notifications.ExactAlarmScheduler
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.ui.components.KBBackButton

private fun CONSENT_DATE_FMT() = SimpleDateFormat("d MMMM yyyy, HH:mm", KBLocale.current())
private val DESTRUCTIVE = Color(0xFFD32F2F)
private val CONSENT_GREEN = Color(0xFF059669)

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    /** Apre la schermata Piani KidBox (stesso comportamento degli upgrade su iOS). */
    onOpenPlans: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val kb = MaterialTheme.kidBoxColors
    val uriHandler = LocalUriHandler.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRevokeConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    if (state.pendingShowConsent) {
        AIConsentBottomSheet(
            onAccept = { viewModel.recordConsent() },
            onDismiss = { viewModel.dismissPendingConsent() },
        )
    }

    if (showRevokeConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text(stringResource(R.string.settings_ai_revoke_q)) },
            text = {
                Text(stringResource(R.string.settings_ai_revoke_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeConsent()
                    showRevokeConfirm = false
                }) {
                    Text(stringResource(R.string.settings_ai_revoke), color = DESTRUCTIVE, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = false }) { Text(stringResource(R.string.settings_common_cancel)) }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = kb.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Titolo sotto il tasto indietro, come nelle altre Impostazioni.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                KBBackButton(onClick = onBack)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_ai_title),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = kb.title,
                )
            }
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
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CurrentPlanCard(
                plan = state.plan,
                usageToday = state.aiUsageToday,
                period = state.aiQuotaPeriod,
                aiAccessBlocked = state.aiAccessBlocked,
            )

            AIIntroCard()

            if (state.aiAccessBlocked) {
                AILockedBanner(
                    onDiscoverPlans = onOpenPlans,
                    onRedeemOfferCode = {
                        runCatching { uriHandler.openUri("https://play.google.com/redeem") }
                    },
                )
            } else {
                AIToggleCard(
                    isEnabled = state.isEnabled,
                    consentGiven = state.consentGiven,
                    consentDate = state.consentDate,
                    onToggle = { viewModel.toggleEnabled(it) },
                    onRevokeClick = { showRevokeConfirm = true },
                )

                AIUsageCard(
                    usageToday = state.aiUsageToday,
                    dailyLimit = state.plan.aiMessageLimit,
                    period = state.aiQuotaPeriod,
                )

                HealthContextSettingsCard(
                    selected = state.healthContextSendPreference,
                    onSelected = viewModel::setHealthContextSendPreference,
                )
            }

            AIPrivacyCard()

            WeeklySummaryCard(
                isEnabled = state.isWeeklySummaryEnabled,
                onToggle = { viewModel.toggleWeeklySummary(it) },
            )

            DailyBriefingCard(
                isEnabled = state.isDailyBriefingEnabled,
                onToggle = { viewModel.toggleDailyBriefing(it) },
            )

            HealthPatternCard(
                isEnabled = state.isHealthPatternEnabled,
                onToggle = viewModel::toggleHealthPattern,
            )

            ExactAlarmPermissionBanner(
                visible = state.isDailyBriefingEnabled || state.isWeeklySummaryEnabled,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CurrentPlanCard(
    plan: KBPlan,
    usageToday: Int,
    period: it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod,
    aiAccessBlocked: Boolean,
) {
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
                val planIcon = when (plan) {
                    KBPlan.FREE -> Icons.Filled.Star
                    KBPlan.PRO -> Icons.Filled.AutoAwesome
                    KBPlan.MAX -> Icons.Filled.WorkspacePremium
                }
                Icon(planIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    stringResource(R.string.settings_storage_current_plan),
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
                if (aiAccessBlocked) {
                    Text(
                        stringResource(R.string.settings_ai_requires_plan),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                } else {
                    val isLifetime = period == it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod.LIFETIME
                    val available = if (plan.aiMessageLimit == Int.MAX_VALUE) {
                        null
                    } else {
                        (plan.aiMessageLimit - usageToday).coerceAtLeast(0)
                    }
                    Text(
                        if (plan.aiMessageLimit == Int.MAX_VALUE) {
                            stringResource(R.string.settings_ai_unlimited)
                        } else if (isLifetime) {
                            stringResource(R.string.settings_ai_free_messages_used, usageToday, plan.aiMessageLimit)
                        } else {
                            stringResource(R.string.settings_plan_ai_messages_per_day, plan.aiMessageLimit)
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                    if (!isLifetime) {
                        Text(
                            if (available == null) stringResource(R.string.settings_ai_available_today_unlimited) else "Disponibili oggi: $available",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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
                    stringResource(R.string.settings_ai_medical_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = kb.title,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_ai_medical_full1),
                fontSize = 14.sp,
                color = kb.subtitle,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_ai_medical_full2),
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
                    Text(stringResource(R.string.settings_ai_enable), fontSize = 16.sp, color = kb.title, fontWeight = FontWeight.Medium)
                    if (!consentGiven) {
                        Text(stringResource(R.string.settings_ai_requires_consent), fontSize = 12.sp, color = kb.subtitle)
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
                        stringResource(R.string.settings_ai_consent_given_on, CONSENT_DATE_FMT().format(Date(consentDate))),
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
                    Text(stringResource(R.string.settings_ai_revoke_disable), color = DESTRUCTIVE, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AIUsageCard(
    usageToday: Int,
    dailyLimit: Int,
    period: it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod = it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod.DAILY,
) {
    val kb = MaterialTheme.kidBoxColors
    val isLifetime = period == it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod.LIFETIME
    val isUnlimited = dailyLimit == Int.MAX_VALUE
    val availableToday = if (isUnlimited) Int.MAX_VALUE else (dailyLimit - usageToday).coerceAtLeast(0)
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
                    if (isLifetime) stringResource(R.string.settings_ai_bonus_free_title) else stringResource(R.string.settings_ai_messages_today),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = kb.title,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (isUnlimited) {
                Text(
                    stringResource(R.string.settings_ai_available_unlimited),
                    fontSize = 14.sp,
                    color = kb.title,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_ai_messages_sent_today, usageToday),
                    fontSize = 14.sp,
                    color = kb.subtitle,
                )
            } else if (isLifetime) {
                Text(
                    stringResource(R.string.settings_ai_free_messages_used, usageToday, dailyLimit),
                    fontSize = 14.sp,
                    color = kb.title,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.15f),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_ai_available_count, availableToday), fontSize = 14.sp, color = kb.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.settings_ai_used_count, usageToday, dailyLimit),
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
                Text(stringResource(R.string.settings_ai_privacy_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.title)
            }
            Spacer(Modifier.height(10.dp))
            PrivacyLine(stringResource(R.string.settings_ai_privacy1))
            PrivacyLine(stringResource(R.string.settings_ai_privacy2))
            PrivacyLine(stringResource(R.string.settings_ai_privacy3))
            PrivacyLine(stringResource(R.string.settings_ai_privacy4))
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
                    Text(stringResource(R.string.settings_ai_weekly), fontSize = 15.sp, color = kb.title, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_ai_weekly_sub), fontSize = 12.sp, color = kb.subtitle)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun ExactAlarmPermissionBanner(visible: Boolean) {
    val context = LocalContext.current
    var needsPermission by remember {
        mutableStateOf(!ExactAlarmScheduler.canScheduleExactAlarms(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                needsPermission = !ExactAlarmScheduler.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!visible || !needsPermission) return

    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.ai_exact_alarm_banner_title),
                fontSize = 15.sp,
                color = kb.title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ai_exact_alarm_banner_body),
                fontSize = 12.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.ai_exact_alarm_banner_action),
                fontSize = 14.sp,
                color = Color(0xFF2563EB),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    ExactAlarmScheduler.requestExactAlarmSettingsIntent(context)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                },
            )
        }
    }
}

@Composable
private fun DailyBriefingCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.settings_ai_daily), fontSize = 15.sp, color = kb.title, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_ai_daily_sub), fontSize = 12.sp, color = kb.subtitle)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun HealthPatternCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.settings_ai_patterns), fontSize = 15.sp, color = kb.title, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_ai_patterns_sub), fontSize = 12.sp, color = kb.subtitle)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun AILockedBanner(
    onDiscoverPlans: () -> Unit,
    onRedeemOfferCode: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_ai_requires_plan),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = kb.title,
                    )
                    Text(
                        stringResource(R.string.settings_ai_upgrade_hint),
                        fontSize = 13.sp,
                        color = kb.subtitle,
                        lineHeight = 18.sp,
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDiscoverPlans),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1FF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_ai_see_plans), color = Color(0xFF1D4ED8), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onDiscoverPlans) { Text(stringResource(R.string.settings_ai_open), color = Color(0xFF1D4ED8)) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(kb.card)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_storage_redeem), color = kb.title, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRedeemOfferCode) { Text(stringResource(R.string.settings_ai_open)) }
            }
        }
    }
}

@Composable
private fun HealthContextSettingsCard(
    selected: HealthContextSendPreference,
    onSelected: (HealthContextSendPreference) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_ai_health_context),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = kb.title,
            )
            Text(
                stringResource(R.string.settings_ai_health_context_sub),
                fontSize = 13.sp,
                color = kb.subtitle,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            HealthContextSendPreference.entries.forEach { pref ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(pref) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == pref, onClick = { onSelected(pref) })
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(stringResource(pref.displayNameRes), color = kb.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(stringResource(pref.detailRes), color = kb.subtitle, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

/** Sezione riutilizzabile (es. bottom sheet dalla chat Salute). */
@Composable
fun HealthContextSendPreferenceSection(
    selected: HealthContextSendPreference,
    onSelected: (HealthContextSendPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_ai_health_chat),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = kb.title,
        )
        Text(
            stringResource(R.string.settings_ai_health_chat_sub),
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        HealthContextSendPreference.entries.forEach { pref ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(pref) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == pref, onClick = { onSelected(pref) })
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(pref.displayNameRes), color = kb.title, fontWeight = FontWeight.Medium)
                    Text(stringResource(pref.detailRes), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                }
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
