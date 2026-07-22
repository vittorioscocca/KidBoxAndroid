package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme

/** Barra superiore stile iOS: Annulla | titolo | Salva (arancione). */
@Composable
fun KidBoxIosFormTopBar(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    kb: KidBoxColorScheme,
    orange: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.components_ios_form_cancel), color = kb.title) }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
        )
        TextButton(onClick = onSave, enabled = saveEnabled) {
            Text(
                stringResource(R.string.components_ios_form_save),
                color = if (saveEnabled) orange else kb.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun IosGroupedCard(
    kb: KidBoxColorScheme,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
fun IosFormDivider(kb: KidBoxColorScheme) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = kb.divider,
    )
}

@Composable
fun IosPlainTextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    kb: KidBoxColorScheme,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        placeholder = { Text(placeholder, color = kb.subtitle) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = kb.title,
            unfocusedTextColor = kb.title,
            cursorColor = kb.title,
            focusedPlaceholderColor = kb.subtitle,
            unfocusedPlaceholderColor = kb.subtitle,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}
