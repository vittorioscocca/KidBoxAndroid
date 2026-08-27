package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Pagina di inserimento/modifica a tutta larghezza, nello stile di
 * `MedicalVisitFormScreen`: barra in alto con Annulla e titolo centrato,
 * contenuto che scorre, pulsante di salvataggio grande in fondo.
 *
 * Sostituisce gli `AlertDialog` stretti usati finora dai form brevi (todo,
 * animali, veicoli, interventi). Resta un `Dialog` invece di una destinazione
 * di navigazione — con `usePlatformDefaultWidth = false` occupa comunque tutto
 * lo schermo — così i punti di chiamata e la logica di salvataggio esistenti
 * non vanno riscritti.
 */
@Composable
fun KidBoxFormPage(
    title: String,
    onDismiss: () -> Unit,
    saveLabel: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(kb.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.life_cancel), color = kb.title)
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = kb.title,
                )
                Spacer(Modifier.width(72.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )

            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Text(saveLabel, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** Intestazione di sezione: titolo in grassetto e, sotto, una riga di aiuto. */
@Composable
fun FormSectionTitle(text: String, hint: String? = null) {
    val kb = MaterialTheme.kidBoxColors
    Spacer(Modifier.height(10.dp))
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    if (hint != null) Text(hint, fontSize = 12.sp, color = kb.subtitle)
    Spacer(Modifier.height(2.dp))
}

/** Intestazione con icona colorata, per i blocchi dentro la pagina. */
@Composable
fun FormSectionHeader(text: String, icon: ImageVector, tint: Color) {
    val kb = MaterialTheme.kidBoxColors
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, color = kb.title)
    }
    Spacer(Modifier.height(2.dp))
}
