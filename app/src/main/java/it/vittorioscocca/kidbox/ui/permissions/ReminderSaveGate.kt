package it.vittorioscocca.kidbox.ui.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLog

private const val TAG = "ReminderSaveGate"

/**
 * Il cancello che sta fra «salva» e un promemoria che non suonerà mai.
 *
 * Due permessi, chiesti solo a chi accende davvero un avviso:
 *
 * 1. **Notifiche** (`POST_NOTIFICATIONS`, API 33+). Senza, l'alarm scatta ma
 *    `notify()` viene scartato in silenzio: l'interruttore direbbe «attivo» e
 *    il telefono resterebbe muto. Se l'utente nega, l'elemento si salva lo
 *    stesso ma **senza** promemoria, così non resta acceso un avviso finto.
 * 2. **Sveglia a tutto schermo** (`USE_FULL_SCREEN_INTENT`, API 34+), solo per
 *    gli urgenti. Qui non c'è un permesso da chiedere a runtime: si manda alle
 *    impostazioni di sistema. Google chiede di verificarne la presenza invece
 *    di darla per scontata — senza, la sveglia resta una finestra mobile
 *    visibile 60 secondi invece di coprire lo schermo bloccato. Il salvataggio
 *    non si ferma: l'avviso arriva comunque, solo più piccolo.
 *
 * Stessa sostanza del cancello già presente nel salvataggio to-do, estratto
 * qui perché ora serve anche a eventi e promemoria del calendario.
 * [ReminderPermission] resta il cancello dei promemoria di salute e wallet,
 * dove l'interruttore vive dentro il form e l'avviso si mostra lì.
 */
@Stable
class ReminderSaveGate<T> internal constructor(
    private val context: Context,
    private val pending: MutableState<Pending<T>?>,
    private val requestNotifications: State<() -> Unit>,
    private val noticeState: MutableState<Boolean>,
    private val onSave: State<(item: T, reminderAllowed: Boolean) -> Unit>,
) {
    internal data class Pending<T>(val item: T, val isUrgent: Boolean)

    /** La sveglia è stata salvata ma non potrà coprire lo schermo bloccato. */
    val showFullScreenNotice: Boolean get() = noticeState.value

    fun dismissFullScreenNotice() {
        noticeState.value = false
    }

    /**
     * Da chiamare al posto del salvataggio diretto: se manca un permesso lo
     * chiede e salva dopo la risposta.
     *
     * @param wantsReminder l'elemento accende un avviso (promemoria acceso, o
     *   `reminderMinutes` valorizzato su un evento).
     * @param isUrgent l'avviso è una sveglia, non una notifica.
     */
    fun save(item: T, wantsReminder: Boolean, isUrgent: Boolean = false) {
        if (wantsReminder && !RuntimePermissions.hasNotificationPermission(context)) {
            pending.value = Pending(item, isUrgent)
            requestNotifications.value()
            return
        }
        if (wantsReminder && isUrgent) checkFullScreenIntent()
        onSave.value(item, wantsReminder)
    }

    internal fun checkFullScreenIntent() {
        if (!RuntimePermissions.canUseFullScreenIntent(context)) noticeState.value = true
    }
}

@Composable
fun <T> rememberReminderSaveGate(
    onSave: (item: T, reminderAllowed: Boolean) -> Unit,
): ReminderSaveGate<T> {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<ReminderSaveGate.Pending<T>?>(null) }
    val notice = remember { mutableStateOf(false) }
    // `onSave` è una lambda del chiamante e cambia a ogni ricomposizione: il
    // gate deve chiamare sempre l'ultima, non quella catturata alla nascita.
    val currentOnSave = rememberUpdatedState(onSave)

    // Il salvataggio rimandato: l'utente ha risposto al permesso, e l'elemento
    // aspettava da `save()`. Negato non vuol dire perso — si salva senza avviso.
    fun resume(granted: Boolean) {
        val p = pending.value ?: return
        pending.value = null
        // Concesso e urgente: manca ancora il secondo permesso, quello che fa
        // coprire lo schermo bloccato. Qui `save()` non ci ripassa.
        if (granted && p.isUrgent && !RuntimePermissions.canUseFullScreenIntent(context)) {
            notice.value = true
        }
        currentOnSave.value(p.item, granted)
    }

    val request = rememberUpdatedState(
        rememberNotificationPermissionRequester(
            onDenied = { resume(false) },
            onGranted = { resume(true) },
        ),
    )
    return remember { ReminderSaveGate(context, pending, request, notice, currentOnSave) }
}

/**
 * Avviso dopo aver salvato una sveglia che il sistema non lascerà comparire a
 * tutto schermo. Non blocca niente: spiega e porta alle impostazioni.
 */
@Composable
fun FullScreenAlarmNoticeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.urgent_reminder_fullscreen_permission_title)) },
        text = { Text(stringResource(R.string.urgent_reminder_fullscreen_permission_body)) },
        confirmButton = {
            TextButton(onClick = {
                RuntimePermissions.fullScreenIntentSettings(context)?.let { intent ->
                    runCatching { context.startActivity(intent) }.onFailure { e ->
                        if (e is ActivityNotFoundException) {
                            KBLog.app.error("impostazioni sveglia a tutto schermo assenti", TAG, e)
                        }
                    }
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.urgent_reminder_fullscreen_permission_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.urgent_reminder_fullscreen_permission_later))
            }
        },
    )
}
