package it.vittorioscocca.kidbox.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.family.PendingFamilyInvite
import it.vittorioscocca.kidbox.ui.screens.settings.JoinFamilyViewModel

/**
 * Applica un invito famiglia lasciato in sospeso da un App Link.
 *
 * Vive sulla Home e non sul Login perché l'ingresso in famiglia richiede sia la
 * sessione autenticata sia il database locale pronto: arrivare qui significa che
 * entrambe le condizioni sono soddisfatte. Il link può essere stato toccato
 * molto prima — anche prima che l'utente avesse un account.
 *
 * Gemello di `consumePendingInviteIfNeeded()` in `RootHostView` su iOS.
 */
@Composable
fun PendingInviteHandler(
    onJoined: () -> Unit,
    viewModel: JoinFamilyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var consumed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (consumed) return@LaunchedEffect
        val invite = PendingFamilyInvite.load(context) ?: return@LaunchedEffect
        if (FirebaseAuth.getInstance().currentUser == null) return@LaunchedEffect

        consumed = true
        // Sempre, anche in caso di errore: l'invito è monouso e a scadenza,
        // ritentarlo a ogni apertura produrrebbe solo lo stesso errore.
        PendingFamilyInvite.clear(context)

        viewModel.joinFromInvite(invite) {
            message = context.getString(R.string.settings_join_success)
        }
    }

    // L'errore arriva dallo stato del ViewModel, che è la stessa strada usata
    // dal join via QR: un unico punto in cui i messaggi vengono formulati.
    LaunchedEffect(state.error) {
        state.error?.let { message = it }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = {
                message = null
                if (state.didJoin) onJoined()
            },
            title = { Text(stringResource(R.string.settings_join_title)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    message = null
                    if (state.didJoin) onJoined()
                }) {
                    Text(stringResource(R.string.settings_common_close))
                }
            },
        )
    }
}
