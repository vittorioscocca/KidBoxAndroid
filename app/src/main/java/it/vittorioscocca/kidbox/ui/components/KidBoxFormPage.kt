package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
 * Pagina di inserimento/modifica a tutta larghezza: barra in alto con
 * Annulla a sinistra, titolo al centro e Salva a destra, contenuto che scorre.
 *
 * Il Salva sta in alto e non in fondo perche' la finestra del Dialog viene
 * posizionata sotto la status bar restando alta quanto tutto lo schermo:
 * misurato sul device, la colonna partiva da y=112 ed era alta 2391 su uno
 * schermo di 2392, quindi un pulsante ancorato in basso finiva fuori. In alto
 * il problema non si pone, ed e' anche la stessa barra di
 * `KidBoxIosFormTopBar` usata dagli altri form.
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
    // Per lo stesso sfasamento descritto sopra, `fillMaxSize()` darebbe una
    // colonna piu' alta della parte visibile: l'altezza va ridotta dell'inset
    // alto, cosi' il fondo del contenuto non finisce oltre il bordo.
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val visibleHeight = with(density) {
        (LocalConfiguration.current.screenHeightDp.dp.toPx() - topInset).toDp()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(visibleHeight)
                .background(kb.background)
                .navigationBarsPadding()
                .imePadding(),
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
                TextButton(onClick = onSave, enabled = saveEnabled) {
                    Text(
                        saveLabel,
                        // Il colore va detto esplicitamente: da disabilitato i
                        // default Material sbiadiscono fino a farlo sparire.
                        color = if (saveEnabled) accent else kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
