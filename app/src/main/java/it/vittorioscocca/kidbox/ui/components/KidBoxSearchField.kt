package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/** Campo di ricerca delle liste: stesso stile di quello della cronologia di Salute. */
@Composable
fun KidBoxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = kb.subtitle) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = kb.subtitle,
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = kb.card,
            unfocusedContainerColor = kb.card,
            focusedBorderColor = Color(0xFFD98C59),
            unfocusedBorderColor = kb.subtitle.copy(alpha = 0.25f),
        ),
    )
}

/** Confronto da ricerca: senza maiuscole né accenti. */
fun String?.matchesSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val normalizedQuery = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val text = java.text.Normalizer.normalize(this.orEmpty(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return text.contains(normalizedQuery, ignoreCase = true)
}
