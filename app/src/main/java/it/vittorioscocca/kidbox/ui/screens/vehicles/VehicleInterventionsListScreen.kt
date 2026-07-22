package it.vittorioscocca.kidbox.ui.screens.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.ui.screens.life.formatItDateTime
import it.vittorioscocca.kidbox.ui.screens.life.vehicleEventTypeEmoji
import it.vittorioscocca.kidbox.ui.screens.life.vehicleEventTypeLabelIt
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.NumberFormat
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleInterventionsListScreen(
    onNavigateBack: () -> Unit,
    viewModel: VehicleInterventionsListViewModel = hiltViewModel(),
) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val filtered = remember(events, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            events
        } else {
            events.filter { ev ->
                ev.title.lowercase(Locale.ROOT).contains(q) ||
                    ev.eventType.lowercase(Locale.ROOT).contains(q) ||
                    vehicleEventTypeLabelIt(ev.eventType).lowercase(Locale.ROOT).contains(q) ||
                    (ev.notes?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                    (ev.garageName?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                    (ev.km?.toString()?.contains(q) == true) ||
                    (ev.cost?.let { formatInterventionCostEuro(it).lowercase(Locale.ROOT).contains(q) } == true)
            }
        }
    }

    val kb = MaterialTheme.kidBoxColors

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.vehicles_services), color = kb.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        vehicle?.name?.let { n ->
                            Text(n, color = kb.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.background,
                    titleContentColor = kb.title,
                    navigationIconContentColor = kb.title,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.vehicles_search_hint), color = kb.subtitle) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = kb.subtitle) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = kb.title,
                    unfocusedTextColor = kb.title,
                    focusedBorderColor = kb.subtitle,
                    unfocusedBorderColor = kb.divider,
                    cursorColor = kb.title,
                ),
            )
            if (filtered.isEmpty()) {
                Text(
                    if (events.isEmpty()) {
                        stringResource(R.string.vehicles_no_services_dot)
                    } else {
                        stringResource(R.string.vehicles_no_results)
                    },
                    color = kb.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = kb.card),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column {
                                filtered.forEachIndexed { index, ev ->
                                    InterventionListRow(ev = ev, kb = kb)
                                    if (index < filtered.lastIndex) {
                                        HorizontalDivider(color = kb.divider, thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatInterventionCostEuro(value: Double): String {
    val nf = NumberFormat.getCurrencyInstance(KBLocale.current())
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = 2
    return nf.format(value)
}

@Composable
private fun InterventionListRow(
    ev: VehicleEventEntity,
    kb: KidBoxColorScheme,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Text(vehicleEventTypeEmoji(ev.eventType), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ev.title, fontWeight = FontWeight.SemiBold, color = kb.title, style = MaterialTheme.typography.bodyLarge)
            Text(formatItDateTime(ev.date), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ev.km?.let {
                    Text("$it km", style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
                }
                ev.cost?.let {
                    Text(formatInterventionCostEuro(it), style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
                }
            }
            ev.garageName?.trim()?.takeIf { it.isNotEmpty() }?.let { g ->
                Text(g, style = MaterialTheme.typography.labelSmall, color = kb.subtitle.copy(alpha = 0.85f), maxLines = 2)
            }
        }
    }
}
