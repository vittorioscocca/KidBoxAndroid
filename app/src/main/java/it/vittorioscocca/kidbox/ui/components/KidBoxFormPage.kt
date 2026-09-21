package it.vittorioscocca.kidbox.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Pagina di inserimento/modifica a tutta larghezza: barra in alto con
 * Annulla a sinistra, titolo al centro e Salva a destra, contenuto che scorre.
 *
 * **Prende il posto della schermata**, non si apre sopra in una finestra: chi
 * la usa la mostra al posto del proprio contenuto e ritorna. Era un `Dialog`, e
 * quella e' stata una trappola cara: dentro la finestra di un `Dialog`
 * `WindowInsets.navigationBars` vale zero e `WindowInsets.ime` e' corto
 * esattamente di quell'altezza, quindi il fondo del form restava sotto la
 * tastiera anche scorrendo. Qui siamo nella finestra dell'Activity, dove gli
 * inset sono quelli veri e bastano i padding normali — la stessa struttura dei
 * form di Salute, dove lo scroll ha sempre funzionato.
 *
 * Il Salva sta in alto, nella stessa barra di `KidBoxIosFormTopBar` usata dagli
 * altri form. Usata da: nuovo animale, nuovo veicolo, nuovo/modifica to-do.
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
    BackHandler(onBack = onDismiss)
    Surface(modifier = Modifier.fillMaxSize(), color = kb.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // La tastiera accorcia la colonna invece di coprirla: il blocco
                // che scorre qui sotto riduce l'area visibile e porta da se' il
                // campo a fuoco sopra i tasti. Nella finestra dell'Activity
                // questo basta; dentro un Dialog non bastava (vedi il commento
                // in testa).
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
