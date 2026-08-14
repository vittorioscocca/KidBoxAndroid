package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBPlan
import android.app.Activity
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@Composable
fun StorageUsageScreen(
    onBack: () -> Unit,
    onOpenPlans: () -> Unit = {},
    viewModel: StorageUsageViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.warmBilling()
    }

    state.restoreDialogError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearRestoreDialogError,
            title = { Text(stringResource(R.string.settings_storage_restore_purchases_title)) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRestoreDialogError) { Text("OK") }
            },
        )
    }

    state.billingPurchaseError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearBillingPurchaseError,
            title = { Text(stringResource(R.string.settings_storage_purchase_error)) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = viewModel::clearBillingPurchaseError) { Text("OK") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_common_back),
                onClick = onBack,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.settings_storage_title),
            color = kb.title,
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(14.dp))

        SpaceSummaryCard(
            plan = state.plan,
            usedBytes = state.usedBytes,
            sectionRows = state.sections,
        )

        if (state.isFamilyOwner && (state.plan == KBPlan.FREE || state.plan == KBPlan.PRO)) {
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPlans),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1FF)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_storage_upgrade), color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Passa a ${if (state.plan == KBPlan.FREE) "Pro" else "Max"} per più spazio e AI.",
                        color = kb.title,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = kb.subtitle)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(title = stringResource(R.string.settings_storage_by_section)) {
            if (state.sections.isEmpty()) {
                Text(
                    if (state.isLoading) stringResource(R.string.settings_storage_loading) else stringResource(R.string.settings_storage_no_data),
                    color = kb.subtitle,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                state.sections.forEachIndexed { idx, section ->
                    StorageSectionRow(section = section, totalBytes = state.plan.storageQuotaBytes())
                    if (idx != state.sections.lastIndex) {
                        HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(title = stringResource(R.string.settings_storage_plans_available)) {
            KBPlan.entries.forEachIndexed { idx, plan ->
                PlanRow(
                    plan = plan,
                    current = state.plan,
                    isFamilyOwner = state.isFamilyOwner,
                    onPurchasePlan = {
                        if (activity != null) {
                            viewModel.purchase(plan, activity)
                        }
                    },
                )
                if (idx != KBPlan.entries.lastIndex) {
                    HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
                }
            }
            if (!state.isFamilyOwner) {
                HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EEFF)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_storage_owner_managed),
                            color = Color(0xFF6D28D9),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            stringResource(R.string.settings_storage_owner_full),
                            color = kb.subtitle,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }

        if (state.plan != KBPlan.FREE) {
            Spacer(Modifier.height(14.dp))
            SectionCard(title = stringResource(R.string.settings_storage_active_subscription)) {
                ActionRow(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.settings_storage_manage_subscription),
                    subtitle = null,
                    onClick = {
                        onOpenPlans()
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(title = null) {
            ActionRow(
                icon = Icons.Filled.People,
                title = stringResource(R.string.settings_storage_shared_note),
                subtitle = null,
                trailing = false,
                onClick = {},
            )
            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
            ActionRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_storage_restore),
                subtitle = null,
                enabled = !state.isRestoreInProgress,
                isLoading = state.isRestoreInProgress,
                onClick = { viewModel.restorePurchases() },
            )
            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
            ActionRow(
                icon = Icons.Filled.Redeem,
                title = stringResource(R.string.settings_storage_redeem),
                subtitle = stringResource(R.string.settings_storage_redeem_sub),
                onClick = {
                    runCatching { uriHandler.openUri("https://play.google.com/redeem") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_storage_verified_note),
            color = kb.subtitle,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SpaceSummaryCard(
    plan: KBPlan,
    usedBytes: Long,
    sectionRows: List<StorageUsageSectionUi>,
) {
    val kb = MaterialTheme.kidBoxColors
    val quota = plan.storageQuotaBytes()
    val fraction = (usedBytes.toFloat() / quota.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    val percent = (fraction * 100f).toInt()
    val percentColor = when {
        fraction >= 0.9f -> Color(0xFFE53935)
        fraction >= 0.7f -> Color(0xFFFF9800)
        else -> Color(0xFF2BB673)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, null, tint = kb.subtitle, modifier = Modifier.size(15.dp))
                Text(
                    stringResource(R.string.settings_storage_family_space),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.subtitle,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            when (plan) {
                                KBPlan.MAX -> Color(0xFF7C3AED)
                                KBPlan.PRO -> Color(0xFF3B82F6)
                                KBPlan.FREE -> Color(0xFF9CA3AF)
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Piano ${plan.displayName} · ${quota.toStorageString()}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        usedBytes.toStorageString(),
                        color = Color(0xFF5B8FDE),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        " / ${quota.toStorageString()}",
                        color = kb.subtitle,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                        maxLines = 1,
                    )
                }
                Text(
                    text = "$percent%",
                    color = percentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    modifier = Modifier.padding(start = 16.dp),
                    maxLines = 1,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Color(0xFF5B8FDE),
                trackColor = kb.divider,
            )
            Text(
                "• ${(quota - usedBytes).coerceAtLeast(0L).toStorageString()} disponibili per la famiglia",
                color = kb.subtitle,
                fontSize = 13.sp,
            )

            if (sectionRows.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun StorageSectionRow(section: StorageUsageSectionUi, totalBytes: Long) {
    val kb = MaterialTheme.kidBoxColors
    val sectionColor = Color(section.colorHex)
    val fraction = (section.bytes.toFloat() / totalBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(sectionColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconForSection(section.iconKey), null, tint = sectionColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.name, color = kb.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(section.bytes.toStorageString(), color = sectionColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = sectionColor,
                trackColor = kb.divider,
            )
            Spacer(Modifier.height(5.dp))
            val countLabel = if (section.recordCount == 1) "elemento" else "elementi"
            Text("${section.recordCount} $countLabel", color = kb.subtitle, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlanRow(
    plan: KBPlan,
    current: KBPlan,
    isFamilyOwner: Boolean,
    onPurchasePlan: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val isCurrent = plan == current
    val canPurchaseWholeRow =
        isFamilyOwner && plan != KBPlan.FREE && !isCurrent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canPurchaseWholeRow) {
                    Modifier.clickable(onClick = onPurchasePlan)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.displayName, color = kb.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (isCurrent) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE8F1FF))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(stringResource(R.string.settings_storage_current_plan), color = Color(0xFF4F8FDB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("${plan.storageQuotaBytes().toStorageString()} storage", color = kb.subtitle, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                // Confronto piani puramente informativo: ogni piano ora include l'AI
                // nominalmente, Free non è più marcato "senza AI".
                plan.aiQuotaLabel,
                color = kb.subtitle,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            if (isCurrent) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(22.dp))
            } else {
                Text(
                    when (plan) {
                        KBPlan.FREE -> stringResource(R.string.settings_storage_free)
                        KBPlan.PRO -> stringResource(R.string.settings_plan_pro_price)
                        KBPlan.MAX -> stringResource(R.string.settings_plan_max_price)
                    },
                    color = if (plan == KBPlan.PRO) Color(0xFF4F8FDB) else kb.subtitle,
                    fontWeight = if (plan == KBPlan.PRO) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    if (title != null) {
        Text(title, color = kb.subtitle, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: Boolean = true,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFF4F8FDB), modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = kb.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = kb.subtitle, fontSize = 12.sp)
            }
        }
        if (trailing) {
            TextButton(onClick = onClick, enabled = enabled && !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = kb.subtitle)
                }
            }
        }
    }
}

private fun iconForSection(iconKey: String): ImageVector = when (iconKey) {
    "photo" -> Icons.Filled.Image
    "document" -> Icons.Filled.Description
    "wallet" -> Icons.Filled.Wallet
    "chat" -> Icons.Filled.ChatBubble
    "salute" -> Icons.Filled.MedicalServices
    "expense" -> Icons.Filled.Euro
    "note" -> Icons.Filled.Note
    "calendar" -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.Inventory2
}
