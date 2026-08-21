package it.vittorioscocca.kidbox.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Porta lo schermo alla massima luminosità finché il composable è nella
 * composizione, ripristinando il valore precedente all'uscita. Serve a far
 * leggere un codice a barre dal lettore alla cassa: con luminosità bassa (o in
 * pieno sole) molti scanner non agganciano il codice.
 *
 * A differenza di iOS — dove `UIScreen.brightness` cambia la luminosità di
 * SISTEMA e va ripristinata a mano per non lasciare il telefono al massimo —
 * qui `screenBrightness` è un override di finestra: vale solo mentre l'app è in
 * primo piano e non tocca l'impostazione di sistema. Riportarlo a
 * [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE] restituisce il controllo
 * al sistema, quindi il caso "app in background" si risolve da sé senza
 * osservare il ciclo di vita.
 *
 * @param isActive passare `false` per sospendere il boost mentre un `Dialog`
 *   copre il codice (modifica carta, foto a schermo intero). Quei dialog hanno
 *   una finestra propria e non smontano questa composizione, quindi senza
 *   l'interruttore l'override resterebbe attivo; quale finestra "vinca"
 *   l'override dipende dalla versione di Android, e affidarsi a quel dettaglio
 *   darebbe un comportamento diverso da iOS e non deterministico tra device.
 */
@Composable
fun MaxScreenBrightnessWhileVisible(isActive: Boolean = true) {
    val context = LocalContext.current
    DisposableEffect(isActive) {
        if (!isActive) return@DisposableEffect onDispose { }

        val window = context.findActivity()?.window
        val previous = window?.attributes?.screenBrightness
        window?.attributes = window.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window?.attributes = window.attributes?.apply {
                screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
