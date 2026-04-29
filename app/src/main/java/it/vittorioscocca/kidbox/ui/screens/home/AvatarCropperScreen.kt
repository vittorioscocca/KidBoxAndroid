package it.vittorioscocca.kidbox.ui.screens.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.util.fixBitmapOrientationFromBytes
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen interactive avatar cropper.
 *
 * • Circular viewport — the image is clipped to a circle so the user sees
 *   exactly what will be saved.
 * • Pan + pinch-to-zoom with hard boundary clamping: the image can never be
 *   dragged so that empty space shows inside the crop circle.
 * • Tapping "Salva" exports a 512 × 512 JPEG of the visible crop region.
 */
@Composable
fun AvatarCropperScreen(
    imageUri: Uri,
    onCancel: () -> Unit,
    onSave: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Diameter of the circular crop frame
    val cropSizeDp = 280.dp
    val cropSizePx = with(density) { cropSizeDp.toPx() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    // Decode + EXIF-orient the bitmap on an IO thread
    LaunchedEffect(imageUri) {
        val bm = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(imageUri)
                ?.use { it.readBytes() } ?: return@withContext null
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            fixBitmapOrientationFromBytes(raw, bytes)
        }
        bitmap = bm
    }

    /**
     * After every gesture update, clamp offsetX/Y so the image never exposes
     * empty space inside the crop circle.
     *
     *   maxOffset = (renderedSide - cropSizePx) / 2
     *
     * where renderedSide = bitmapSide × fitScale × gestureScale.
     * If the image is already larger than the crop box (which it always is
     * when scale ≥ 1 and ContentScale.Fit fills the box), maxOffset ≥ 0.
     */
    fun clampOffsets(bm: Bitmap) {
        val fitScale = min(cropSizePx / bm.width, cropSizePx / bm.height)
        val maxOffX = ((bm.width  * fitScale * scale - cropSizePx) / 2f).coerceAtLeast(0f)
        val maxOffY = ((bm.height * fitScale * scale - cropSizePx) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxOffX, maxOffX)
        offsetY = offsetY.coerceIn(-maxOffY, maxOffY)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .statusBarsPadding(),
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (!isSaving) onCancel() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Annulla",
                    tint = Color.White,
                )
            }
            Text(
                text = "Foto profilo",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            IconButton(onClick = {
                if (!isSaving) { scale = 1f; offsetX = 0f; offsetY = 0f }
            }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset", tint = Color.White)
            }
        }

        Spacer(Modifier.height(40.dp))

        // ── Crop zone ─────────────────────────────────────────────────────────
        // The gesture detector spans the full width for comfortable use on any
        // finger position; the clipped circle defines what the user sees.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(bitmap) {
                    if (bitmap != null) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                            bitmap?.let { clampOffsets(it) }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Circular clipped frame — the image is cropped to this circle
            Box(
                modifier = Modifier
                    .size(cropSizeDp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A)),
            ) {
                bitmap?.let { bm ->
                    Image(
                        bitmap = bm.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                }

                // Loading spinner while bitmap decodes
                if (bitmap == null && !isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                }
            }

            // White border ring drawn on top of the clip boundary
            Canvas(modifier = Modifier.size(cropSizeDp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = size.minDimension / 2f - 1.5f,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            // Semi-transparent saving overlay
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .size(cropSizeDp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Trascina e pizzica per sistemare la foto",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.weight(1f))

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { if (!isSaving) onCancel() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Annulla") }

            Button(
                onClick = {
                    val bm = bitmap ?: return@Button
                    isSaving = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
                            exportCrop(bm, scale, offsetX, offsetY, cropSizePx)
                        }
                        onSave(bytes)
                        // Composable will be removed from composition by the caller
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = bitmap != null && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    Text(" Salva", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Crop export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders the visible crop area as a 512 × 512 JPEG.
 *
 * The math mirrors the on-screen layout exactly:
 *   • [ContentScale.Fit] maps the bitmap into the [cropSizePx] square at
 *     `fitScale = min(cropSizePx / bitmapW, cropSizePx / bitmapH)`.
 *   • The user's [scale] and [offsetX]/[offsetY] are applied on top.
 *   • Everything is then projected uniformly onto a 512 px output frame.
 */
private fun exportCrop(
    bm: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cropSizePx: Float,
): ByteArray {
    val outputSize = 512
    val outputRatio = outputSize / cropSizePx

    // Total scale from source bitmap pixels → output pixels
    val fitScale = min(cropSizePx / bm.width, cropSizePx / bm.height)
    val totalScale = fitScale * scale * outputRatio

    val matrix = Matrix()
    matrix.postScale(totalScale, totalScale)
    // Translate so the (scaled) bitmap center aligns with the output center,
    // then apply the user's pan (also scaled to output space).
    matrix.postTranslate(
        outputSize / 2f - bm.width  * totalScale / 2f + offsetX * outputRatio,
        outputSize / 2f - bm.height * totalScale / 2f + offsetY * outputRatio,
    )

    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(output).drawBitmap(
        bm,
        matrix,
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG),
    )

    return ByteArrayOutputStream().also { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    }.toByteArray()
}
