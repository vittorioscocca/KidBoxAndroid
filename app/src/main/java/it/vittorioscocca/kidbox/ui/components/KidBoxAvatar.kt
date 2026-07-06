package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.material3.MaterialTheme

/**
 * Circular profile avatar with a photo/initials/glyph fallback.
 * Mirrors the KidBox design system's `Avatar` component (design-system/components/core/Avatar.jsx)
 * and the iOS `KBAvatar`.
 */
@Composable
fun KidBoxAvatar(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val kb = MaterialTheme.kidBoxColors
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (imageUrl == null && initials.isNotEmpty()) Color(0xFFF28C33).copy(alpha = 0.16f) else kb.card),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
            )
            initials.isNotEmpty() -> Text(
                text = initials,
                color = Color(0xFFF28C33),
                fontSize = (size.value * 0.4f).coerceAtLeast(11f).sp,
                fontWeight = FontWeight.Bold,
            )
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = name,
                tint = kb.subtitle,
            )
        }
    }
}
