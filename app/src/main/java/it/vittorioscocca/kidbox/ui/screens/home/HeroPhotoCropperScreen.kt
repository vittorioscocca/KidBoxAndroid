package it.vittorioscocca.kidbox.ui.screens.home

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HeroCrop(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

@Composable
fun HeroPhotoCropperScreen(
    imageUri: Uri,
    initialCrop: HeroCrop = HeroCrop(),
    isSaving: Boolean = false,
    onCancel: () -> Unit,
    onSave: (HeroCrop) -> Unit,
) {
    val context = LocalContext.current

    // Decode bitmap from URI
    val bitmap = remember(imageUri) {
        context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }

    // Gesture state — identico a iOS HeroPhotoCropperView
    var scale by remember { mutableFloatStateOf(initialCrop.scale.coerceAtLeast(1f)) }
    var offsetX by remember { mutableFloatStateOf(initialCrop.offsetX) }
    var offsetY by remember { mutableFloatStateOf(initialCrop.offsetY) }

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
                Icon(Icons.Filled.Close, contentDescription = "Annulla", tint = Color.White)
            }
            Text(
                "Foto famiglia",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            IconButton(onClick = {
                if (!isSaving) {
                    scale = 1f; offsetX = 0f; offsetY = 0f
                }
            }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Cropper area ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF2A2A2A))
                .pointerInput(isSaving) {
                    if (!isSaving) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Scale: clamp 1.0 .. 4.0 come iOS (min(max(next, 1.0), 3.0))
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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

            // Hint overlay (come iOS)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(
                        Color.Black.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "Trascina per spostare • Pinch per zoom",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Saving overlay
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Buttons ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { if (!isSaving) onCancel() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text("Annulla")
            }
            Button(
                onClick = {
                    if (!isSaving) onSave(HeroCrop(scale, offsetX, offsetY))
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
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