package it.vittorioscocca.kidbox.data.local

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import it.vittorioscocca.kidbox.BuildConfig
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Interruttori che devono poter cambiare senza pubblicare una versione.
 *
 * Nasce per il pulsante «Continua con Facebook». L'app Meta di KidBox è in
 * modalità sviluppo, quindi chi non ha un ruolo su di essa riceve «questa app
 * non funziona e lo sviluppatore ne è a conoscenza» — una frase che l'utente
 * legge come «KidBox è rotta», non come «questo provider è spento». Su 256
 * registrazioni un solo login Facebook è andato a buon fine, ed era quello
 * dello sviluppatore.
 *
 * Il pulsante quindi va tolto, ma il giorno in cui Meta approva la
 * pubblicazione deve poter tornare **senza** una nuova release e senza
 * riattraversare la review di Play: da qui l'interruttore remoto.
 *
 * Perché Remote Config e non Firestore: la schermata di login sta **prima**
 * dell'autenticazione, e ogni documento sotto `config/` richiede `isSignedIn()`
 * (`firestore.rules:41-42`). Remote Config si legge senza account.
 *
 * Il valore vive in tre posti, dal più pronto al più autorevole: il default
 * compilato, le [android.content.SharedPreferences] con l'ultimo valore visto,
 * e Remote Config. Il default è **spento**: se la fetch non arriva mai — rete
 * assente, primo avvio, console non ancora configurata — resta lo stato che
 * vogliamo oggi.
 *
 * Sta in un `object` con uno [StateFlow] come [ChatAvailability]: lo legge una
 * schermata Compose fuori dal grafo Hilt, e deve ridisegnarsi se il valore
 * arriva mentre è già a video.
 *
 * Gemello di `KBFeatureFlags` su iOS: stessa chiave remota, stesso default.
 */
object KBFeatureFlags {

    private const val PREFS_FILE = "kidbox_prefs"

    /**
     * Chiave su Firebase Remote Config. Identica su iOS: un solo parametro in
     * console governa entrambe le piattaforme.
     */
    private const val REMOTE_KEY_FACEBOOK_LOGIN = "facebook_login_enabled"

    private const val KEY_FACEBOOK_LOGIN = "kb_facebookLoginEnabled"

    /** Spento finché Meta non pubblica l'app. */
    private const val FACEBOOK_LOGIN_FALLBACK = false

    private val _facebookLoginEnabled = MutableStateFlow(FACEBOOK_LOGIN_FALLBACK)
    val facebookLoginEnabled: StateFlow<Boolean> = _facebookLoginEnabled.asStateFlow()

    /** Da `KidBoxApplication.onCreate()`: allinea lo stato alla cache locale. */
    fun init(context: Context) {
        _facebookLoginEnabled.value =
            prefs(context).getBoolean(KEY_FACEBOOK_LOGIN, FACEBOOK_LOGIN_FALLBACK)
    }

    /**
     * Allinea cache e stato ai valori remoti.
     *
     * Va lanciata senza attenderla: la schermata di login parte con la cache e
     * si aggiorna da sola tramite lo [StateFlow] quando la risposta arriva.
     */
    suspend fun refresh(context: Context) {
        val config = FirebaseRemoteConfig.getInstance()
        config.setDefaultsAsync(mapOf(REMOTE_KEY_FACEBOOK_LOGIN to FACEBOOK_LOGIN_FALLBACK))
        config.setConfigSettingsAsync(
            remoteConfigSettings {
                // In debug si rilegge a ogni avvio, così una modifica in console
                // si verifica subito. In release un'ora: il flag cambia una volta
                // all'anno, non vale una chiamata di rete a ogni apertura.
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            }
        )

        runCatching {
            config.fetchAndActivate().await()
            config.getBoolean(REMOTE_KEY_FACEBOOK_LOGIN)
        }.onSuccess { enabled ->
            prefs(context).edit().putBoolean(KEY_FACEBOOK_LOGIN, enabled).apply()
            _facebookLoginEnabled.value = enabled
            KBLog.data.info("FeatureFlags: $REMOTE_KEY_FACEBOOK_LOGIN=$enabled", TAG)
        }.onFailure {
            // Nessun fallback qui: senza risposta resta l'ultimo valore noto,
            // che è già la scelta giusta dell'ultima volta.
            KBLog.data.info("FeatureFlags: fetch fallita, resta la cache locale", TAG)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private const val TAG = "KBFeatureFlags"
}
