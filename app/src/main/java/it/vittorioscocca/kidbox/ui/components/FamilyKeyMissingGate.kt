package it.vittorioscocca.kidbox.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyEscrow
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avvisa, una volta per schermata, che manca la chiave della famiglia.
 *
 * Perché all'ingresso e non sul singolo errore: le decifrature sparse in
 * Documenti, Wallet, Chat e Foto falliscono tutte per la stessa ragione, e
 * parecchie scartano l'esito in silenzio (`runCatching { … }.getOrNull()`,
 * `try?`), lasciando l'utente davanti a contenuti vuoti senza spiegazione.
 * Controllare la condizione a monte le copre tutte insieme, senza dover
 * modificare ogni punto di cattura.
 *
 * `ensureFamilyKeyAvailable` prova anche il recupero dall'escrow, quindi
 * l'avviso compare solo quando la chiave è davvero irrecuperabile.
 *
 * Un dialog e non un Toast: il Toast tronca a due righe e il messaggio, che
 * deve spiegare anche come rimediare, risultava illeggibile.
 */
@Composable
fun FamilyKeyMissingGate(familyId: String) {
    val context = LocalContext.current
    // `rememberSaveable`: sopravvive alla rotazione, così non ricompare
    // dopo che l'utente l'ha chiuso.
    var alreadyShown by rememberSaveable(familyId) { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(familyId) {
        if (familyId.isBlank() || alreadyShown) return@LaunchedEffect
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return@LaunchedEffect
        val available = withContext(Dispatchers.IO) {
            runCatching { FamilyKeyEscrow.ensureFamilyKeyAvailable(context, familyId, uid) }
                .getOrDefault(false)
        }
        if (!available) {
            alreadyShown = true
            visible = true
        }
    }

    if (visible) {
        FamilyKeyMissingDialog(familyId = familyId, onDismiss = { visible = false })
    }
}

/**
 * Dialog con il messaggio esteso della chiave mancante.
 *
 * Separato da [FamilyKeyMissingGate] perché serve anche quando il caso emerge
 * da un'operazione fallita in corso d'opera, non solo all'apertura della
 * schermata.
 */
@Composable
fun FamilyKeyMissingDialog(familyId: String, onDismiss: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid.orEmpty()
    // Cassaforte v2 accesa per questo utente: allora si offre anche il QR per
    // farsi mandare la chiave (piano v2, sezione 3.2).
    var canReceive by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) canReceive = FamilyKeyVault.flags(uid).active
    }

    if (receiving) {
        KeyVaultPrompt(
            uid = uid,
            email = user?.email,
            familyIds = listOf(familyId).filter { it.isNotBlank() },
            allowLater = true,
            onDone = onDismiss,
            onLater = onDismiss,
            receive = true,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.family_key_missing_title)) },
        text = {
            Text(
                stringResource(
                    if (canReceive) R.string.family_key_missing_message_qr else R.string.family_key_missing_message,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (canReceive) {
                TextButton(onClick = { receiving = true }) {
                    Text(stringResource(R.string.handoff_receive))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_common_close))
                }
            }
        },
        dismissButton = if (canReceive) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_common_close))
                }
            }
        } else null,
    )
}
