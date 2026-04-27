package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveRecordingWaveformView(
    samples: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val bars = if (samples.isEmpty()) {
        List(18) { 8 }
    } else {
        samples.takeLast(18).map { it.coerceIn(6, 24) }
    }
    Row(
        modifier = modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        bars.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(color = color, shape = RoundedCornerShape(999.dp)),
            )
        }
    }
}

