package it.vittorioscocca.kidbox.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Da chiamare come prima cosa dentro un `Dialog(usePlatformDefaultWidth = false)`
 * che occupa lo schermo.
 *
 * Con `usePlatformDefaultWidth=false` Compose misura il contenuto del dialog
 * sull'intero display, ma la finestra del dialog resta dentro le barre di
 * sistema (su un 1080×2392 con barra a 3 pulsanti: `frame=[0,112][1080,2259]`).
 * Gli ultimi ~245 px del contenuto finiscono fuori finestra: tagliati e non
 * toccabili, con lo scroll già in fondo — visto da `dumpsys window windows`.
 * `decorFitsSystemWindows=false` e `navigationBarsPadding()` da soli non
 * cambiano nulla, perché il problema è il frame, non gli inset.
 *
 * Qui la finestra viene estesa a tutto lo schermo; gli inset arrivano al
 * contenuto e li applica chi di dovere (`Scaffold` da solo, altrimenti
 * `systemBarsPadding()` sul contenitore).
 */
@Composable
fun ExtendDialogWindowToScreen() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            )
        }
    }
}

/**
 * Da chiamare dentro ogni `Dialog` che contiene campi di testo, insieme a un
 * `imePadding()` sul contenuto.
 *
 * Le finestre dei `Dialog` di Compose nascono con
 * `softInputMode = SOFT_INPUT_ADJUST_PAN`: `windowSoftInputMode` del manifest
 * vale per l'Activity, non per loro. Misurato su device con la tastiera aperta
 * (`dumpsys window windows`): la finestra dell'Activity riporta
 * `sim={adjust=resize}`, quella del dialog `sim={adjust=pan}`.
 *
 * Con `ADJUST_PAN` Android non riporta l'inset dell'IME: `WindowInsets.ime`
 * misura **zero** e `imePadding()` non fa niente — silenziosamente.
 *
 * `ADJUST_RESIZE` **non** è la risposta, anche se sembra: accorcia il frame
 * della finestra e proprio per questo riporta comunque inset zero. Misurato sul
 * device, il risultato è il peggiore dei due — `frame=[32,112][1047,1542]` con
 * dentro un contenuto Compose ancora alto 2200: la metà bassa del form finisce
 * fuori finestra, ritagliata e non raggiungibile nemmeno scorrendo.
 *
 * Serve `ADJUST_NOTHING`: la finestra non viene né spostata né accorciata,
 * l'inset **viene riportato**, e ad accorciare il contenuto ci pensa
 * `imePadding()`. È la stessa scelta che Material3 fa sulle proprie
 * `ModalBottomSheet` (`ADJUST_NOTHING` su API ≥ 30, `ADJUST_RESIZE` sotto), ed
 * è il motivo per cui i fogli funzionavano già mentre i dialog no.
 */
@Composable
fun ReportKeyboardInsetsToDialog() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setSoftInputMode(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            },
        )
    }
}
