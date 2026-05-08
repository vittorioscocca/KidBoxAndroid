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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun StorageUsageScreen(
    onBack: () -> Unit,
    onOpenPlans: () -> Unit = {},
    viewModel: StorageUsageViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStubDialog by remember { mutableStateOf(false) }
    var stubDialogTitle by remember { mutableStateOf("") }
    var stubDialogBody by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }

    if (showStubDialog) {
        AlertDialog(
            onDismissRequest = { showStubDialog = false },
            title = { Text(stubDialogTitle) },
            text = { Text(stubDialogBody) },
            confirmButton = { TextButton(onClick = { showStubDialog = false }) { Text("OK") } },
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
                contentDescription = "Indietro",
                onClick = onBack,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Utilizzo spazio",
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
                    Text("Upgrade", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
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
        SectionCard(title = "Utilizzo per sezione") {
            if (state.sections.isEmpty()) {
                Text(
                    if (state.isLoading) "Caricamento..." else "Nessun dato disponibile",
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
        SectionCard(title = "Piani disponibili") {
            KBPlan.entries.forEachIndexed { idx, plan ->
                PlanRow(plan = plan, current = state.plan)
                if (idx != KBPlan.entries.lastIndex) {
                    HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }

        if (state.plan != KBPlan.FREE) {
            Spacer(Modifier.height(14.dp))
            SectionCard(title = "Abbonamento attivo") {
                ActionRow(
                    icon = Icons.Filled.Description,
                    title = "Gestisci abbonamento",
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
                title = "Lo spazio è condiviso tra tutti i membri della famiglia.",
                subtitle = null,
                trailing = false,
                onClick = {},
            )
            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
            ActionRow(
                icon = Icons.Filled.Refresh,
                title = "Ripristina acquisti",
                subtitle = null,
                onClick = {
                    stubDialogTitle = "Ripristina acquisti"
                    stubDialogBody = "Funzione in arrivo su Android."
                    showStubDialog = true
                },
            )
            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 16.dp))
            ActionRow(
                icon = Icons.Filled.Redeem,
                title = "Riscatta codice offerta",
                subtitle = "Codice promozionale o offerta App Store",
                onClick = {
                    stubDialogTitle = "Riscatta codice offerta"
                    stubDialogBody = "Funzione in arrivo su Android."
                    showStubDialog = true
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Gli acquisti vengono verificati tramite lo store. Il piano si applica all'intera famiglia.",
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
                    "  SPAZIO FAMIGLIA",
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
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        " / ${quota.toStorageString()}",
                        color = kb.subtitle,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 5.dp),
                        maxLines = 1,
                    )
                }
                Text(
                    text = "$percent%",
                    color = percentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
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
private fun PlanRow(plan: KBPlan, current: KBPlan) {
    val kb = MaterialTheme.kidBoxColors
    val isCurrent = plan == current
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                        Text("Piano attuale", color = Color(0xFF4F8FDB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("${plan.storageQuotaBytes().toStorageString()} storage", color = kb.subtitle, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (plan == KBPlan.FREE) "Senza AI" else "${plan.aiDailyMessagesForUi()} msg AI/giorno",
                color = kb.subtitle,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            if (isCurrent) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(22.dp))
            } else {
                Text(
                    when (plan) {
                        KBPlan.FREE -> "Gratis"
                        KBPlan.PRO -> "€4,99/mese"
                        KBPlan.MAX -> "€ 9.99/mese"
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
            TextButton(onClick = onClick) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = kb.subtitle)
            }
        }
    }
}

private fun iconForSection(iconKey: String): ImageVector = when (iconKey) {
    "photo" -> Icons.Filled.Image
    "document" -> Icons.Filled.Description
    "wallet" -> Icons.Filled.Wallet
    "chat" -> Icons.Filled.ChatBubble
    "expense" -> Icons.Filled.Euro
    "note" -> Icons.Filled.Note
    "calendar" -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.Inventory2
}
