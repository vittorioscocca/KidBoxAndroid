package it.vittorioscocca.kidbox

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "KidBoxAppCheck"

object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )
        Log.i(
            TAG,
            "DEBUG: cerca in logcat la riga Firebase con il token segreto (es. \"Enter this debug secret\" / App Check), " +
                "poi Firebase Console → App Check → app Android → Gestisci token di debug → incolla. " +
                "Senza quel token, con enforcement Storage attivo, tutti gli upload falliscono.",
        )
    }
}
