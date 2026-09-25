package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.data.remote.chat.ChatUploadProgress

/** Progresso di invio del messaggio: null se non è in invio, < 0 in preparazione, 0…1 in caricamento. */
@Composable
internal fun rememberUploadProgress(messageId: String): Float? {
    val flow = remember(messageId) { ChatUploadProgress.progressOf(messageId) }
    val value by flow.collectAsState(initial = ChatUploadProgress.state.value[messageId])
    return value
}

/**
 * Anello di avanzamento stile WhatsApp, come su iOS: gira finché non arriva il
 * primo progresso, poi si riempie; al centro la freccia d'invio.
 */
/** Come WhatsApp sopra foto e video: disco chiaro traslucido, anello e stop scuri. */
internal val ChatRingMediaTint = Color(0xFF474747)
internal val ChatRingMediaBackdrop = Color.White.copy(alpha = 0.62f)

@Composable
internal fun ChatProgressRing(
    value: Float,
    diameter: Dp = 46.dp,
    tint: Color = ChatRingMediaTint,
    backdrop: Color = ChatRingMediaBackdrop,
    /** Tocco sull'anello = stop dell'invio. null: solo indicatore. */
    onCancel: (() -> Unit)? = null,
) {
    val indeterminate = value < 0f
    val animated by animateFloatAsState(targetValue = value.coerceIn(0f, 1f), animationSpec = tween(200), label = "upload")
    val spin = if (indeterminate) {
        rememberInfiniteTransition(label = "uploadSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "uploadSpinAngle",
        ).value
    } else 0f
    val stroke = (diameter * 0.075f).coerceAtLeast(2.5.dp)
    val cancelLabel = stringResource(R.string.chat_cancel_upload)
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(backdrop)
            .then(
                if (onCancel != null) {
                    Modifier
                        .clickable(onClick = onCancel)
                        .semantics { contentDescription = cancelLabel }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(stroke * 1.5f)) {
            val w = stroke.toPx()
            drawCircle(color = tint.copy(alpha = 0.25f), style = Stroke(width = w))
            if (indeterminate) {
                rotate(spin) {
                    drawArc(tint, startAngle = -90f, sweepAngle = 90f, useCenter = false,
                        style = Stroke(width = w, cap = StrokeCap.Round))
                }
            } else {
                drawArc(tint, startAngle = -90f, sweepAngle = 360f * animated.coerceAtLeast(0.03f),
                    useCenter = false, style = Stroke(width = w, cap = StrokeCap.Round))
            }
        }
        // Stop: tocca per annullare l'invio.
        Box(
            modifier = Modifier
                .size(diameter * 0.2f)
                .clip(RoundedCornerShape(diameter * 0.04f))
                .background(tint),
        )
    }
}

/** L'anello se il messaggio è in invio, altrimenti [fallback]. */
@Composable
internal fun ChatUploadIndicator(
    messageId: String,
    diameter: Dp = 46.dp,
    tint: Color = ChatRingMediaTint,
    backdrop: Color = ChatRingMediaBackdrop,
    fallback: @Composable () -> Unit,
) {
    val progress = rememberUploadProgress(messageId)
    if (progress != null) {
        ChatProgressRing(progress, diameter, tint, backdrop, onCancel = { ChatUploadProgress.cancel(messageId) })
    } else {
        fallback()
    }
}

/**
 * Formato della bolla foto/video come WhatsApp (e come iOS): gli orizzontali
 * prendono tutta la larghezza, i verticali crescono in altezza fino a un tetto e
 * poi si restringono. Senza dimensioni note: 4:3 orizzontale.
 */
internal fun chatMediaBoxSize(width: Int?, height: Int?, maxWidth: Dp): DpSize {
    val maxH = maxWidth * 1.2f
    if (width == null || height == null || width <= 0 || height <= 0) {
        return DpSize(maxWidth, maxWidth * 0.75f)
    }
    val aspect = height.toFloat() / width.toFloat()
    var w = maxWidth
    var h = maxWidth * aspect
    if (h > maxH) {
        h = maxH
        w = maxOf(maxH / aspect, maxWidth * 0.55f)
    }
    if (h < maxWidth * 0.45f) h = maxWidth * 0.45f
    return DpSize(w, h)
}

/**
 * Dimensioni misurate al caricamento per i messaggi che non le portano (precedenti
 * al campo o inviati da una versione vecchia): la bolla prende il formato giusto
 * dopo la prima miniatura. Solo in memoria.
 */
internal object ChatMeasuredMediaSizes {
    val sizes = mutableStateMapOf<String, Pair<Int, Int>>()

    fun record(messageId: String, width: Int, height: Int) {
        if (width > 0 && height > 0 && messageId !in sizes) sizes[messageId] = width to height
    }
}

internal fun UiChatMessage.mediaDimensions(): Pair<Int?, Int?> {
    if (mediaWidth != null && mediaHeight != null) return mediaWidth to mediaHeight
    val measured = ChatMeasuredMediaSizes.sizes[id]
    return measured?.first to measured?.second
}
