package it.vittorioscocca.kidbox.ui.screens.pharmacies

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.LocalUpgradeAction
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private val Green = Color(0xFF34A885)
private val Orange = Color(0xFFFF6B00)

/**
 * Farmacie di turno (Pro e Max). Tre cose che la schermata non fa mai: mostrare
 * un elenco che potrebbe essere di ieri (se la fonte è ferma resta il link),
 * presentare come ufficiale una fonte incerta (la riga della fonte dice sempre
 * di chi è il dato), togliere all'utente la possibilità di indicare a mano il
 * sito («Usa un altro sito» c'è in ogni stato). Gemella di `PharmacyView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyScreen(
    onBack: () -> Unit,
    onOpenPlaceSettings: () -> Unit,
    viewModel: PharmacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val upgradeAction = LocalUpgradeAction.current
    var showSite by remember { mutableStateOf(false) }
    var siteText by remember { mutableStateOf("") }
    val place = state.settings.effectivePlace

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize().background(kb.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_common_back), tint = kb.title)
            }
            Text(
                stringResource(R.string.pharmacies_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
        }

        when {
            !state.isPaid -> Locked {
                AppAnalytics.aiPaywallShown(context, "pharmacies")
                upgradeAction(context.getString(R.string.pharmacies_upgrade_subtitle))
            }
            !state.familyLoaded || (state.loading && state.feed == null) ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Green, modifier = Modifier.size(24.dp))
                }
            else -> PullToRefreshBox(isRefreshing = false, onRefresh = { viewModel.load(force = true) }, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PlaceRow(
                            text = if (place.hasCity) place.label else stringResource(R.string.pharmacies_set_place),
                            onClick = onOpenPlaceSettings,
                        )
                        state.error?.let { Banner(it, Orange) }
                        state.notice?.let { Banner(it, Color(0xFF2F80ED)) }
                        val feed = state.feed
                        if (feed != null) {
                            FeedBody(
                                feed = feed,
                                cityKnown = place.hasCity,
                                onFind = viewModel::find,
                                onOpen = ::open,
                                onUseSite = {
                                    siteText = feed.source?.takeIf { it.manual }?.url.orEmpty()
                                    showSite = true
                                },
                                onResetSite = { viewModel.setSource(null) },
                            )
                        }
                        Text(stringResource(R.string.pharmacies_emergency), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                        Spacer(Modifier.height(24.dp))
                    }
                    if (state.working) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.background(kb.card, RoundedCornerShape(14.dp)).padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp, color = Green, modifier = Modifier.size(24.dp))
                                Text(stringResource(R.string.pharmacies_working), style = MaterialTheme.typography.bodySmall, color = kb.title)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSite) {
        AlertDialog(
            onDismissRequest = { showSite = false },
            title = { Text(stringResource(R.string.pharmacies_use_other_site)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = siteText,
                        onValueChange = { siteText = it },
                        singleLine = true,
                        placeholder = { Text("https://…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.pharmacies_site_help, state.feed?.estimateManual ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = kb.subtitle,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = siteText.isNotBlank(),
                    onClick = {
                        showSite = false
                        viewModel.setSource(siteText.trim())
                    },
                ) { Text(stringResource(R.string.pharmacies_use)) }
            },
            dismissButton = { TextButton(onClick = { showSite = false }) { Text(stringResource(R.string.settings_common_cancel)) } },
        )
    }
}

@Composable
private fun FeedBody(
    feed: PharmacyFeed,
    cityKnown: Boolean,
    onFind: () -> Unit,
    onOpen: (String) -> Unit,
    onUseSite: () -> Unit,
    onResetSite: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    when (feed.status) {
        "ready" -> {
            Text(stringResource(R.string.pharmacies_today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = kb.title)
            if (feed.today.isEmpty()) {
                Text(stringResource(R.string.pharmacies_none_today), style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
            } else {
                feed.today.forEach { ShiftCard(it, showComune = !cityKnown) }
            }
            if (feed.upcoming.isNotEmpty()) {
                Text(stringResource(R.string.pharmacies_upcoming), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = kb.title)
                feed.upcoming.forEach { day ->
                    Text(dayLabel(day.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Green)
                    day.items.forEach { ShiftCard(it, showComune = false) }
                }
            }
            if (feed.others.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.pharmacies_nearby),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (expanded) "−" else "+", color = Green, style = MaterialTheme.typography.titleLarge)
                }
                if (expanded) feed.others.forEach { ShiftCard(it, showComune = true) }
            }
            SourceFooter(feed.source, onOpen, onUseSite, onResetSite)
        }
        "stale" -> InfoCard(
            title = stringResource(R.string.pharmacies_stale_title),
            text = stringResource(R.string.pharmacies_stale_text),
            feed = feed, onOpen = onOpen, onUseSite = onUseSite, onResetSite = onResetSite,
        )
        "unreadable" -> InfoCard(
            title = stringResource(R.string.pharmacies_unreadable_title),
            text = stringResource(R.string.pharmacies_unreadable_text),
            feed = feed, onOpen = onOpen, onUseSite = onUseSite, onResetSite = onResetSite,
        )
        "none" -> Card {
            Text(stringResource(R.string.pharmacies_none_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = kb.title)
            Text(stringResource(R.string.pharmacies_none_text), style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
            Button(
                onClick = onFind,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(context.getString(R.string.pharmacies_find, feed.estimateFind)) }
            SiteButtons(hasManual = false, onUseSite = onUseSite, onResetSite = onResetSite)
        }
        "no-place" -> InfoCard(
            title = stringResource(R.string.pharmacies_noplace_title),
            text = stringResource(R.string.pharmacies_noplace_text),
            feed = null, onOpen = onOpen, onUseSite = onUseSite, onResetSite = onResetSite,
        )
        "unsupported" -> InfoCard(
            title = stringResource(R.string.pharmacies_unsupported_title),
            text = stringResource(R.string.pharmacies_unsupported_text),
            feed = null, onOpen = onOpen, onUseSite = onUseSite, onResetSite = onResetSite,
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        Modifier.fillMaxWidth().background(kb.card, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun PlaceRow(text: String, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        Modifier.fillMaxWidth().background(kb.card, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Green)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = kb.title, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.pharmacies_change), style = MaterialTheme.typography.bodySmall, color = Green)
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.kidBoxColors.title,
        modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(12.dp),
    )
}

@Composable
private fun ShiftCard(s: PharmacyShift, showComune: Boolean) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().background(kb.card, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        serviceLabel(s.service)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Green)
        }
        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = kb.title)
        val line = listOf(s.address, if (showComune) s.comune else "").filter { it.isNotEmpty() }.joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.dialNumber.isNotEmpty()) {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.dialNumber}"))) }
                }) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp), tint = Green)
                    Text(" " + stringResource(R.string.pharmacies_call), color = Green)
                }
            }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(s.mapQuery)}")))
                }
            }) {
                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp), tint = Green)
                Text(" " + stringResource(R.string.pharmacies_directions), color = Green)
            }
        }
    }
}

@Composable
private fun SourceFooter(source: PharmacySource?, onOpen: (String) -> Unit, onUseSite: () -> Unit, onResetSite: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (source != null) {
            val uncertain = source.confidence == "uncertain"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (uncertain) Icons.Filled.Warning else Icons.Filled.Verified,
                    contentDescription = null,
                    tint = if (uncertain) Orange else Green,
                    modifier = Modifier.size(16.dp),
                )
                Text(confidenceText(source.confidence), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (uncertain) Orange else Green)
            }
            val updated = source.fetchedAtMs?.let { DateFormat.getTimeInstance(DateFormat.SHORT, KBLocale.current()).format(Date(it)) }
            Text(
                listOfNotNull(source.label, updated?.let { stringResource(R.string.pharmacies_updated_at, it) }).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
                modifier = Modifier.clickable(enabled = source.url.isNotEmpty()) { onOpen(source.url) },
            )
            if (uncertain) Text(stringResource(R.string.pharmacies_uncertain_note), style = MaterialTheme.typography.bodySmall, color = Orange)
        }
        Text(stringResource(R.string.pharmacies_disclaimer), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        SiteButtons(hasManual = source?.hasManual == true, onUseSite = onUseSite, onResetSite = onResetSite)
    }
}

@Composable
private fun SiteButtons(hasManual: Boolean, onUseSite: () -> Unit, onResetSite: () -> Unit) {
    Column {
        TextButton(onClick = onUseSite) { Text(stringResource(R.string.pharmacies_use_other_site), color = Green) }
        if (hasManual) TextButton(onClick = onResetSite) { Text(stringResource(R.string.pharmacies_back_to_auto), color = Green) }
    }
}

@Composable
private fun InfoCard(
    title: String,
    text: String,
    feed: PharmacyFeed?,
    onOpen: (String) -> Unit,
    onUseSite: () -> Unit,
    onResetSite: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Card {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = kb.title)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
        val source = feed?.source
        if (source != null) {
            if (source.url.isNotEmpty()) {
                OutlinedButton(onClick = { onOpen(source.url) }) { Text(stringResource(R.string.pharmacies_open, source.label), color = Green) }
            }
            Text(confidenceText(source.confidence), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            SiteButtons(hasManual = source.hasManual, onUseSite = onUseSite, onResetSite = onResetSite)
        }
    }
}

@Composable
private fun Locked(onUpgrade: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = Green, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.pharmacies_locked_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = kb.title, textAlign = TextAlign.Center)
        Text(stringResource(R.string.pharmacies_locked_text), style = MaterialTheme.typography.bodyMedium, color = kb.subtitle, textAlign = TextAlign.Center)
        Button(onClick = onUpgrade, colors = ButtonDefaults.buttonColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pharmacies_discover_pro))
        }
    }
}

@Composable
private fun serviceLabel(service: String): String? = when (service) {
    "night" -> stringResource(R.string.pharmacies_service_night)
    "afternoon" -> stringResource(R.string.pharmacies_service_afternoon)
    "holiday" -> stringResource(R.string.pharmacies_service_holiday)
    "afternoon_holiday" -> stringResource(R.string.pharmacies_service_afternoon_holiday)
    "h24" -> stringResource(R.string.pharmacies_service_h24)
    else -> null
}

@Composable
private fun confidenceText(c: String): String = when (c) {
    "verified" -> stringResource(R.string.pharmacies_conf_verified)
    "ai" -> stringResource(R.string.pharmacies_conf_ai)
    "manual" -> stringResource(R.string.pharmacies_conf_manual)
    else -> stringResource(R.string.pharmacies_conf_uncertain)
}

private fun dayLabel(key: String): String = runCatching {
    LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEEE d MMMM", KBLocale.current()))
}.getOrDefault(key)
