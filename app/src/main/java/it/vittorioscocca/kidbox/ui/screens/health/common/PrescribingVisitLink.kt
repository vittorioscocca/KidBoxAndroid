package it.vittorioscocca.kidbox.ui.screens.health.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrescribingVisitSummary(
    val visitId: String,
    val reason: String,
    val dateEpochMillis: Long,
)

private val visitDateFmt = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)

/** Card «Visita prescrittrice» (stile iOS). */
@Composable
fun PrescribingVisitLinkCard(
    summary: PrescribingVisitSummary,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Visita prescrittrice", fontSize = 12.sp, color = kb.subtitle)
                Text(
                    summary.reason.ifBlank { "Visita" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    visitDateFmt.format(Date(summary.dateEpochMillis)),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = kb.subtitle.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
