package it.vittorioscocca.kidbox.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun QRCodeView(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { QRCodeGenerator.generateQRCodeBitmap(payload) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Codice QR",
            contentScale = ContentScale.FillBounds,
            modifier = modifier
                .size(220.dp)
                .background(Color.White, RoundedCornerShape(16.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(220.dp)
                .background(Color(0x33000000), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "QR non disponibile",
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
            )
        }
    }
}
