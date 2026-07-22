package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val TravelAccent = Color(0xFFF2611A)
private val RingSize = 156.dp
private val RingStroke = 7.dp
private val InnerDiscSize = 108.dp

@Composable
fun TravelPlanningLoadingScreen(
    destinationName: String,
    subtitle: String,
    plannedDayCount: Int = 3,
) {
    val kb = MaterialTheme.kidBoxColors
    val totalSeconds = remember(plannedDayCount) {
        TravelPlanningCountdown.totalSeconds(plannedDayCount)
    }
    var secondsLeft by remember(plannedDayCount) { mutableIntStateOf(totalSeconds) }
    var tip by remember { mutableStateOf(TravelDiscoverTips.random()) }

    val targetProgress = (secondsLeft.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    val ringProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 900),
        label = "countdown_ring",
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (secondsLeft > 0) secondsLeft -= 1
            if (secondsLeft % 12 == 0) tip = TravelDiscoverTips.random()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.35f))

        Box(
            modifier = Modifier.size(RingSize),
            contentAlignment = Alignment.Center,
        ) {
            CountdownRing(
                progress = ringProgress,
                trackColor = kb.subtitle.copy(alpha = 0.14f),
                progressColor = TravelAccent,
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                modifier = Modifier
                    .size(InnerDiscSize)
                    .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(0.08f)),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.travel_about),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    Text(
                        stringResource(R.string.travel_remaining),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Text(
            "Pianifico $destinationName…",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = kb.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            subtitle,
            color = kb.subtitle,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            TravelPlanningCountdown.estimateLabel(plannedDayCount),
            fontSize = 12.sp,
            color = kb.subtitle.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(tip.title.uppercase(), color = TravelAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(tip.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.travel_tip_while_ai),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.weight(0.65f))
    }
}

@Composable
private fun CountdownRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokePx = RingStroke.toPx()
        val diameter = size.minDimension - strokePx
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )

        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}
