package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.data.local.TravelProfile
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLocale

private val TravelAccent = Color(0xFFF2611A)

@Composable
fun TravelHubSection(
    profile: TravelProfile?,
    aiAvailable: Boolean,
    onPlanTrip: () -> Unit,
    onDiscover: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> stringResource(R.string.travel_good_morning)
        in 12..17 -> stringResource(R.string.travel_good_afternoon)
        else -> stringResource(R.string.travel_good_evening)
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text(greeting, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = kb.title)
            Text(stringResource(R.string.travel_where_to), fontSize = 20.sp, color = kb.subtitle, modifier = Modifier.padding(top = 4.dp))
        }

        PlanTripCard(enabled = aiAvailable, onClick = onPlanTrip)
        DiscoverCard(enabled = aiAvailable, onClick = onDiscover)

        profile?.let {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    stringResource(R.string.travel_hub_discover_hint, stringResource(it.pace.titleRes).lowercase(KBLocale.current())),
                    modifier = Modifier.padding(16.dp),
                    color = kb.subtitle,
                )
            }
        }
    }
}

@Composable
private fun PlanTripCard(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1F1A1A), Color(0xFF472414)),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.travel_plan_a_trip), color = TravelAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("~2 MIN", color = Color.White.copy(0.55f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.travel_i_know), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.travel_where_to_go), color = TravelAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.travel_build_around),
                color = Color.White.copy(0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.travel_start_planning),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .background(TravelAccent, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun DiscoverCard(enabled: Boolean, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.travel_discover_up), color = TravelAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .background(TravelAccent.copy(alpha = 0.12f), CircleShape)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TravelAccent)
                }
            }
            Text(stringResource(R.string.travel_suggest_place), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = kb.title, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.travel_suggest_hint), color = kb.subtitle, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.travel_show_places),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(Color.Black, RoundedCornerShape(24.dp))
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
