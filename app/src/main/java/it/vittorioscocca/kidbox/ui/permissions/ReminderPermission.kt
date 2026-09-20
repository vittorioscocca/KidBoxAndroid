package it.vittorioscocca.kidbox.ui.permissions

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Cancello per accendere un promemoria locale (cure, visite, esami, vaccini,
 * wallet), speculare a `requestAuthorization()` su iOS.
 *
 * Senza il permesso notifiche l'alarm scatta lo stesso, ma `notify()` viene
 * scartato dal sistema in silenzio: l'interruttore direbbe «attivo» e il
 * telefono resterebbe muto. Qui l'accensione passa prima dal permesso —
 * [requestEnable] chiede se manca e chiama `onEnable` solo se le notifiche
 * possono davvero arrivare; altrimenti l'interruttore resta spento e
 * [showNotice] accende l'avviso con il rimando alle impostazioni.
 *
 * `areNotificationsEnabled` copre sia il permesso runtime (33+) sia la
 * disattivazione manuale (<33), e viene riletto a ogni ON_RESUME: l'utente va
 * nelle impostazioni di sistema, riattiva, torna, e l'avviso sparisce.
 */
@Stable
class ReminderPermission internal constructor(
    private val blockedState: MutableState<Boolean>,
    private val attemptedState: MutableState<Boolean>,
    private val request: State<() -> Unit>,
) {
    /** Le notifiche non possono arrivare su questo device. */
    val blocked: Boolean get() = blockedState.value

    /**
     * L'avviso si mostra solo quando serve: dopo un tentativo di accensione
     * fallito, o se un promemoria risulta acceso ma le notifiche sono bloccate
     * (permesso revocato dopo averlo armato).
     */
    fun showNotice(reminderEnabled: Boolean): Boolean =
        blocked && (attemptedState.value || reminderEnabled)

    /** Da chiamare all'accensione dell'interruttore, al posto di `onEnable` diretto. */
    fun requestEnable() {
        attemptedState.value = true
        request.value()
    }

    /** L'utente ha scelto esplicitamente «nessun promemoria»: l'avviso non serve più. */
    fun clearAttempt() {
        attemptedState.value = false
    }
}

@Composable
fun rememberReminderPermission(onEnable: () -> Unit): ReminderPermission {
    val context = LocalContext.current
    val blocked = remember {
        mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val attempted = remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blocked.value = !NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currentOnEnable by rememberUpdatedState(onEnable)
    val request = rememberUpdatedState(
        rememberNotificationPermissionRequester(
            onDenied = { blocked.value = true },
            onGranted = {
                // Su <33 il permesso runtime non esiste, ma le notifiche possono
                // essere spente a mano: in quel caso non si arma nulla.
                val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                blocked.value = !enabled
                if (enabled) currentOnEnable()
            },
        ),
    )
    return remember { ReminderPermission(blocked, attempted, request) }
}

/**
 * Avviso «Notifiche bloccate» da mettere sotto l'interruttore del promemoria.
 * Il tap apre le impostazioni notifiche dell'app, da cui si riattivano.
 */
@Composable
fun NotificationsBlockedCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = NOTICE_ORANGE.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = NOTICE_ORANGE,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    stringResource(R.string.settings_notif_blocked),
                    color = kb.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    stringResource(R.string.settings_notif_blocked_sub),
                    color = kb.subtitle,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private val NOTICE_ORANGE = Color(0xFFFF9800)
