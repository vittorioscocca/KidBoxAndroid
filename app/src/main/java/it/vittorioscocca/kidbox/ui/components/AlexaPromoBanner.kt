package it.vittorioscocca.kidbox.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.AlexaAvailability
import kotlinx.coroutines.tasks.await

/**
 * L'invito a collegare Alexa, dentro le due schermate dove la skill serve
 * davvero: la lista della spesa e i to-do.
 *
 * Perché qui e non solo in Impostazioni: la skill esiste da settembre e nessuno
 * la trova, perché per scoprirla bisogna già sapere che c'è e andarla a cercare
 * in fondo alle impostazioni. Chi sta guardando la lista della spesa è invece
 * esattamente la persona a cui interessa dettarla a voce.
 *
 * Tre condizioni perché compaia, e servono tutte:
 *  1. la skill esiste nella lingua dell'app ([AlexaAvailability]): proporre a un
 *     francese di collegare una skill che risponde solo in italiano è peggio che
 *     tacere;
 *  2. la famiglia non è già collegata — e vale il collegamento di CHIUNQUE, non
 *     solo il proprio: se in casa un membro ha già agganciato l'account Amazon,
 *     gli Echo funzionano per tutti e l'invito è rumore;
 *  3. l'utente non ha già detto di no.
 */
enum class AlexaPromoContext(val messageRes: Int) {
    GROCERY(R.string.alexa_promo_grocery),
    TODO(R.string.alexa_promo_todo),
}

private const val TAG = "AlexaPromo"
private const val PREFS = "kidbox_prefs"
private const val KEY_DISMISSED = "kb_alexa_promo_dismissed"
private const val KEY_KNOWN_LINKED = "kb_alexa_promo_known_linked"

/**
 * Le due preferenze sono per FAMIGLIA, non per dispositivo.
 *
 * Erano globali, ed era un bug che si vedeva solo cambiando account: chi
 * usciva da una famiglia con Alexa già collegata e rientrava con un account
 * nuovo si portava dietro il «già collegata» del vecchio, e l'invito non
 * compariva più su quel telefono. Le `SharedPreferences` sopravvivono al
 * logout, quindi tutto ciò che descrive una famiglia va scritto sotto il suo
 * id — e vale anche per il rifiuto: «non mostrare più» detto in una famiglia
 * non è una risposta data per un'altra.
 */
private fun key(base: String, familyId: String) = "${base}_$familyId"

/**
 * Esito della verifica, per avvio dell'app, insieme alla famiglia a cui si
 * riferisce.
 *
 * Deve stare QUI e non nello stato del composable: lo stato è per schermata, e
 * con un semplice «ho già controllato» la seconda schermata trovava il
 * controllo fatto ma il proprio `visible` ancora a false — aprendo prima la
 * spesa, l'invito nei to-do non sarebbe comparso mai. Qui si conserva il
 * RISULTATO, così entrambe le schermate leggono lo stesso.
 *
 * La famiglia fa parte della memoria e non è un dettaglio: il processo Android
 * sopravvive al logout, quindi senza confrontarla un cambio account
 * riuserebbe la decisione presa per quello precedente.
 *
 * `null` = non ancora chiesto. La domanda si fa al massimo una volta per
 * avvio e per famiglia: è una callable, e queste sono schermate che si aprono
 * di continuo.
 */
private var promoDecision: Pair<String, Boolean>? = null

private fun flag(context: Context, base: String, familyId: String): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(key(base, familyId), false)

private fun setFlag(context: Context, base: String, familyId: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit { putBoolean(key(base, familyId), true) }
}

@Composable
fun AlexaPromoBanner(
    familyId: String,
    context: AlexaPromoContext,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(familyId) {
        // Il rifiuto vale per ENTRAMBE le schermate: chi lo scarta nella spesa
        // ha capito di cosa si tratta, riproporglielo nei to-do sarebbe la
        // stessa richiesta due volte.
        if (!AlexaAvailability.isAvailable() ||
            familyId.isBlank() ||
            flag(ctx, KEY_DISMISSED, familyId) ||
            flag(ctx, KEY_KNOWN_LINKED, familyId)
        ) {
            visible = false
            return@LaunchedEffect
        }
        promoDecision?.let { (decidedFor, decision) ->
            if (decidedFor == familyId) {
                visible = decision
                return@LaunchedEffect
            }
        }

        runCatching {
            val result = FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("getAlexaLinkStatus")
                .call(hashMapOf("familyId" to familyId))
                .await()

            @Suppress("UNCHECKED_CAST")
            result.getData() as? Map<String, Any?>
        }.onSuccess { payload ->
            val data = payload.orEmpty()
            val mine = data["linked"] == true

            @Suppress("UNCHECKED_CAST")
            val others = (data["familyLinks"] as? List<Map<String, Any?>>).orEmpty().isNotEmpty()
            if (mine || others) {
                // Una volta che la famiglia risulta collegata non si torna
                // indietro: il collegamento si può sciogliere, ma da
                // Impostazioni, dove l'invito non serve più a nessuno.
                setFlag(ctx, KEY_KNOWN_LINKED, familyId)
                promoDecision = familyId to false
                visible = false
            } else {
                promoDecision = familyId to true
                visible = true
            }
        }.onFailure { err ->
            // Silenzio VERSO L'UTENTE: un invito facoltativo non è motivo per
            // mostrare un errore in cima alla lista della spesa. Ma un log
            // serve, o «il banner non compare» resta indistinguibile fra
            // «famiglia già collegata» e «la callable non risponde» — che è
            // esattamente il dubbio in cui ci si perde più tempo.
            Log.w(TAG, "stato Alexa non leggibile, invito nascosto: ${err.message}")
            // La decisione NON si memorizza: un errore di rete adesso non deve
            // nascondere l'invito per tutta la sessione.
            visible = false
        }
    }

    if (!visible) return

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().then(modifier),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(context.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConnect) {
                    Text(stringResource(R.string.alexa_promo_connect))
                }
                // «Non mostrare più» e non «Annulla»: un invito che ricompare a
                // ogni apertura della lista diventa un fastidio, e la
                // scorciatoia resta comunque in Impostazioni → Alexa per chi
                // cambia idea.
                OutlinedButton(onClick = {
                    setFlag(ctx, KEY_DISMISSED, familyId)
                    promoDecision = familyId to false
                    visible = false
                }) {
                    Text(stringResource(R.string.alexa_promo_never))
                }
            }
        }
    }
}
