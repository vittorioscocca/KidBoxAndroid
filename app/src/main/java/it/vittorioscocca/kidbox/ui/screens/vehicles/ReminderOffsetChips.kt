package it.vittorioscocca.kidbox.ui.screens.vehicles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.R

/** Chip multi-select per gli offset di preavviso (giorno stesso / 2 giorni prima / 1 settimana prima) di una scadenza. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderOffsetChips(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        0 to stringResource(R.string.vehicles_reminder_offset_same_day),
        2 to stringResource(R.string.vehicles_reminder_offset_2d),
        7 to stringResource(R.string.vehicles_reminder_offset_1w),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.vehicles_reminder_alerts_label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (days, label) ->
                FilterChip(
                    selected = days in selected,
                    onClick = { onToggle(days) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}
