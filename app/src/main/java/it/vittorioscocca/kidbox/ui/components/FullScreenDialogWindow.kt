package it.vittorioscocca.kidbox.ui.components

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
