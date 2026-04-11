package it.vittorioscocca.kidbox.ui.splash

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GradientStart = Color(0xFFFFBF40)
private val GradientEnd = Color(0xFFF26010)

@Composable
fun KidBoxSplashScreen(onFinished: () -> Unit) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    activity?.window?.attributes?.layoutInDisplayCutoutMode =
        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    val window = activity?.window
    val iconBitmap = remember(context) {
        val drawable = ResourcesCompat.getDrawable(
            context.resources,
            R.mipmap.ic_launcher,
            context.theme,
        )
        drawable?.toBitmap()?.asImageBitmap()
    }

    val ringScale = remember { Animatable(0.6f) }
    val ringOpacity = remember { Animatable(0f) }
    val glowScale = remember { Animatable(0.6f) }
    val glowOpacity = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0.55f) }
    val iconOpacity = remember { Animatable(0f) }
    val wordmarkOffsetY = remember {
        Animatable(18f * density.density)
    }
    val wordmarkOpacity = remember { Animatable(0f) }

    var particlesActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        launch {
            delay(100)
            coroutineScope {
                launch { ringScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                launch { ringOpacity.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                launch { glowScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                launch { glowOpacity.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            }
        }
        launch {
            delay(250)
            coroutineScope {
                launch {
                    iconScale.animateTo(
                        1f,
                        spring(
                            dampingRatio = 0.62f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
                launch {
                    iconOpacity.animateTo(
                        1f,
                        spring(
                            dampingRatio = 0.62f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        }
        launch {
            delay(550)
            coroutineScope {
                launch {
                    wordmarkOffsetY.animateTo(
                        0f,
                        spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
                launch {
                    wordmarkOpacity.animateTo(
                        1f,
                        spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        }
        launch {
            delay(400)
            particlesActive = true
        }
        launch {
            delay(850)
            iconScale.animateTo(1.04f, tween(200))
            delay(200)
            iconScale.animateTo(1f, tween(200))
        }
        delay(2600)
        controller?.show(WindowInsetsCompat.Type.systemBars())
        onFinished()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val gradientBrush = remember(wPx, hPx) {
            Brush.linearGradient(
                colors = listOf(GradientStart, GradientEnd),
                start = Offset(0f, 0f),
                end = Offset(wPx, hPx),
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(gradientBrush))

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(440.dp)
                    .graphicsLayer {
                        scaleX = glowScale.value
                        scaleY = glowScale.value
                        alpha = glowOpacity.value
                        transformOrigin = TransformOrigin.Center
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer {
                        scaleX = ringScale.value
                        scaleY = ringScale.value
                        alpha = ringOpacity.value
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                val r = size.minDimension / 2f
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = r,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            Canvas(
                modifier = Modifier
                    .size(340.dp)
                    .graphicsLayer {
                        scaleX = ringScale.value
                        scaleY = ringScale.value
                        alpha = ringOpacity.value
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                val r = size.minDimension / 2f
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = r,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        if (particlesActive) {
            val infinite = rememberInfiniteTransition(label = "splash_particles")
            val particlePhase by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "phase",
            )
            val specs = listOf(
                Triple(-90f, -130f, 7f),
                Triple(80f, -110f, 5f),
                Triple(110f, -60f, 6f),
                Triple(-70f, 100f, 4f),
                Triple(50f, 120f, 8f),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                specs.forEachIndexed { index, (ox, oy, sizeDp) ->
                    val phase = particlePhase * 2 * kotlin.math.PI.toFloat() + index * 0.7f
                    val dyPx = sin(phase) * with(density) { 10.dp.toPx() }
                    val op = (0.2f + 0.35f * ((sin(phase) + 1f) / 2f)).coerceIn(0.2f, 0.55f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = ox.dp,
                                y = oy.dp + with(density) { dyPx.toDp() },
                            )
                            .size(sizeDp.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = op)),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    alpha = iconOpacity.value
                    transformOrigin = TransformOrigin.Center
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(30.dp),
                            spotColor = Color.Black.copy(alpha = 0.18f),
                            ambientColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .size(128.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = "KidBox",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    translationY = wordmarkOffsetY.value
                    alpha = wordmarkOpacity.value
                },
            ) {
                Text(
                    text = "KidBox",
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.15f),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f,
                        ),
                    ),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "La tua famiglia, in un'unica app.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.80f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
