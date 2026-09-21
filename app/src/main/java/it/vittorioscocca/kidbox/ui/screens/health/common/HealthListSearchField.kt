package it.vittorioscocca.kidbox.ui.screens.health.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Campo di ricerca delle liste di Salute (visite, cure, esami, vaccini), sotto
 * il titolo grande: lo stesso in tutte e quattro, con il colore del modulo.
 * Equivale al `.searchable` delle view iOS.
 */
@Composable
fun HealthListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        placeholder = { Text(placeholder, color = kb.subtitle) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = kb.subtitle)
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tint,
            unfocusedBorderColor = kb.subtitle.copy(alpha = 0.25f),
            focusedTextColor = kb.title,
            unfocusedTextColor = kb.title,
            cursorColor = tint,
        ),
    )
}
