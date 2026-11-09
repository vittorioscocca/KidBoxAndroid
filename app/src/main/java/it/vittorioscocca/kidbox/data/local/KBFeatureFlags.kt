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

    /**
     * Testi cifrati su Firestore con la chiave di famiglia: memoria AI di famiglia
     * (`contentEnc` dei `memoryFacts`) e testo letto dei documenti
     * (`extractedTextEnc`); su iOS e web anche le chat AI, che Android non
     * sincronizza. Spento finché le build che leggono il cifrato non sono
     * diffuse: si accende insieme alla promozione di `firestore.rules.next`.
     */
    private const val REMOTE_KEY_TEXT_ENCRYPTION = "text_encryption_enabled"
    private const val KEY_TEXT_ENCRYPTION = "kb_textEncryptionEnabled"
    private const val TEXT_ENCRYPTION_FALLBACK = false

    private val _textEncryptionEnabled = MutableStateFlow(TEXT_ENCRYPTION_FALLBACK)
    val textEncryptionEnabled: StateFlow<Boolean> = _textEncryptionEnabled.asStateFlow()

    /**
     * Sezioni cifrate con la chiave di famiglia (Garage, Animali, Spese, Salute:
     * campo `secure`, specifica in `internal/cifratura-sezioni.md`). La lettura
     * capisce sempre entrambi i formati; questo governa solo la scrittura e la
     * ricifratura. Spento finché le build che leggono `secure` non sono diffuse.
     */
    private const val REMOTE_KEY_SECTIONS_ENCRYPTION = "sections_encryption_enabled"
    private const val KEY_SECTIONS_ENCRYPTION = "kb_sectionsEncryptionEnabled"
    private const val SECTIONS_ENCRYPTION_FALLBACK = false

    private val _sectionsEncryptionEnabled = MutableStateFlow(SECTIONS_ENCRYPTION_FALLBACK)
    val sectionsEncryptionEnabled: StateFlow<Boolean> = _sectionsEncryptionEnabled.asStateFlow()

    /**
     * Prova su una sola famiglia a interruttore spento, solo nelle build debug:
     * il file di preferenze
     * `kb_debug_overrides` con la stringa `sections_encryption_family` = familyId
     * (scritto con `run-as`, `internal/cifratura-sezioni.md`, «Come provarla»).
     */
    private const val DEBUG_PREFS_FILE = "kb_debug_overrides"
    private const val KEY_DEBUG_SECTIONS_FAMILY = "sections_encryption_family"
    @Volatile private var debugSectionsFamily: String? = null

    /** Cifratura delle sezioni per questa famiglia: interruttore remoto o prova di debug. */
    fun sectionsEncryptionEnabled(familyId: String): Boolean =
        _sectionsEncryptionEnabled.value ||
            (BuildConfig.DEBUG && !debugSectionsFamily.isNullOrEmpty() && debugSectionsFamily == familyId)

    /** Da `KidBoxApplication.onCreate()`: allinea lo stato alla cache locale. */
    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            debugSectionsFamily = context.getSharedPreferences(DEBUG_PREFS_FILE, Context.MODE_PRIVATE)
                .getString(KEY_DEBUG_SECTIONS_FAMILY, null)
            if (!debugSectionsFamily.isNullOrEmpty()) {
                KBLog.data.info("FeatureFlags: cifratura sezioni di prova per $debugSectionsFamily", TAG)
            }
        }
        _facebookLoginEnabled.value =
            prefs(context).getBoolean(KEY_FACEBOOK_LOGIN, FACEBOOK_LOGIN_FALLBACK)
        _textEncryptionEnabled.value =
            prefs(context).getBoolean(KEY_TEXT_ENCRYPTION, TEXT_ENCRYPTION_FALLBACK)
        _sectionsEncryptionEnabled.value =
            prefs(context).getBoolean(KEY_SECTIONS_ENCRYPTION, SECTIONS_ENCRYPTION_FALLBACK)
    }

    /**
     * Allinea cache e stato ai valori remoti.
     *
     * Va lanciata senza attenderla: la schermata di login parte con la cache e
     * si aggiorna da sola tramite lo [StateFlow] quando la risposta arriva.
     */
    suspend fun refresh(context: Context) {
        val config = FirebaseRemoteConfig.getInstance()
        config.setDefaultsAsync(
            mapOf(
                REMOTE_KEY_FACEBOOK_LOGIN to FACEBOOK_LOGIN_FALLBACK,
                REMOTE_KEY_TEXT_ENCRYPTION to TEXT_ENCRYPTION_FALLBACK,
                REMOTE_KEY_SECTIONS_ENCRYPTION to SECTIONS_ENCRYPTION_FALLBACK,
            ),
        )
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
            Triple(
                config.getBoolean(REMOTE_KEY_FACEBOOK_LOGIN),
                config.getBoolean(REMOTE_KEY_TEXT_ENCRYPTION),
                config.getBoolean(REMOTE_KEY_SECTIONS_ENCRYPTION),
            )
        }.onSuccess { (enabled, encrypted, sections) ->
            prefs(context).edit()
                .putBoolean(KEY_FACEBOOK_LOGIN, enabled)
                .putBoolean(KEY_TEXT_ENCRYPTION, encrypted)
                .putBoolean(KEY_SECTIONS_ENCRYPTION, sections)
                .apply()
            _facebookLoginEnabled.value = enabled
            _textEncryptionEnabled.value = encrypted
            _sectionsEncryptionEnabled.value = sections
            KBLog.data.info(
                "FeatureFlags: $REMOTE_KEY_FACEBOOK_LOGIN=$enabled $REMOTE_KEY_TEXT_ENCRYPTION=$encrypted " +
                    "$REMOTE_KEY_SECTIONS_ENCRYPTION=$sections",
                TAG,
            )
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
