package it.vittorioscocca.kidbox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.HiltAndroidApp
import it.vittorioscocca.kidbox.data.local.ThemePreference
import it.vittorioscocca.kidbox.data.local.toNightMode
import it.vittorioscocca.kidbox.data.location.GeofenceMonitorRestorer
import it.vittorioscocca.kidbox.data.remote.AppCheckTokenCache
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager
import it.vittorioscocca.kidbox.notifications.KidBoxFirebaseMessagingService
import it.vittorioscocca.kidbox.notifications.nudge.NudgeEngine
import it.vittorioscocca.kidbox.util.CrashAnalyzer
import it.vittorioscocca.kidbox.util.KBCrashHandler
import it.vittorioscocca.kidbox.util.KBFileLogger
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.KidBoxApplicationHolder
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsLifecycleObserver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KidBoxApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var geofenceMonitorRestorer: GeofenceMonitorRestorer

    @Inject
    lateinit var pushNotificationManager: PushNotificationManager

    @Inject
    lateinit var nudgeEngine: NudgeEngine

    @Inject
    lateinit var themePreference: ThemePreference

    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // super.onCreate() prima di tutto: Hilt inietta i campi @Inject (incluso
        // themePreference) dentro Hilt_KidBoxApplication.onCreate(), quindi usarli
        // prima genera UninitializedPropertyAccessException e crash immediato.
        super.onCreate()
        KidBoxApplicationHolder.applicationContext = applicationContext
        KBFileLogger.init(this)
        // Debug diagnostico temporaneo: dà visibilità sul traffico gRPC di Firestore
        // (query inviate, stream di listen, risposte) sotto il tag logcat "Firestore".
        // Solo debug build, va tolto una volta chiuso il problema di sync sulla
        // famiglia senza bambino.
        if (BuildConfig.DEBUG) {
            FirebaseFirestore.setLoggingEnabled(true)
        }
        KBCrashHandler.install()
        // Installa il provider App Check (Play Integrity in release, Debug in debug)
        // il prima possibile, prima di qualunque chiamata Firestore/Storage/Functions:
        // da qui i token vengono generati e allegati alle richieste, ma nessuna Cloud
        // Function li richiede ancora (enforceAppCheck non è attivo lato server) —
        // quindi questo passo non può bloccare nulla, solo iniziare a raccogliere
        // dati sull'adozione prima di un enforcement futuro.
        AppCheckInstaller.install()
        appInitScope.launch {
            runCatching { AppCheckTokenCache.warmUp() }
        }
        // Applicato subito dopo l'injection, prima di qualunque Activity: allinea da
        // subito i componenti nativi (DatePickerDialog, notifiche, ecc.) alla preferenza
        // scelta in Impostazioni. Senza, quei componenti seguono solo il tema di sistema,
        // e mostrano un popup chiaro dentro un'app messa in scuro dall'utente (o viceversa).
        AppCompatDelegate.setDefaultNightMode(themePreference.getTheme().toNightMode())
        WorkManager.initialize(this, workManagerConfiguration)
        KidBoxFirebaseMessagingService.createNotificationChannels(this)
        // Analytics utenti attivi — internal/analytics-active-users.md
        registerActivityLifecycleCallbacks(KBAnalyticsLifecycleObserver())
        appInitScope.launch {
            CrashAnalyzer.analyzeIfNeeded(this@KidBoxApplication)
        }
        appInitScope.launch {
            runCatching { geofenceMonitorRestorer.restore() }
        }
        startFcmTokenOwnershipObserver()
        registerActivityLifecycleCallbacks(NudgeForegroundObserver())
    }

    /**
     * Ricalcola la coda dei nudge a ogni passaggio in foreground.
     *
     * Stessa tecnica di [KBAnalyticsLifecycleObserver] — conteggio delle
     * Activity avviate — per lo stesso motivo: `lifecycle-process` non è tra le
     * dipendenze, e il contatore 0→1 è immune alle rotazioni di schermo.
     *
     * Throttle di due minuti: la valutazione è tutta locale, ma rifarla a ogni
     * rientro rapido è lavoro sprecato.
     */
    private inner class NudgeForegroundObserver : Application.ActivityLifecycleCallbacks {
        private var startedActivities = 0
        private var lastRunAt = 0L

        override fun onActivityStarted(activity: Activity) {
            if (startedActivities == 0) {
                val now = System.currentTimeMillis()
                if (now - lastRunAt >= THROTTLE_MS) {
                    lastRunAt = now
                    appInitScope.launch { runCatching { nudgeEngine.refresh() } }
                }
            }
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val THROTTLE_MS = 2 * 60 * 1000L
    }

    /**
     * Ripersiste il token FCM a ogni sessione autenticata.
     *
     * Senza questo il token si salva solo in `onNewToken` e al toggle di una
     * preferenza in Impostazioni. `onNewToken` scatta però *prima* del login al
     * primo avvio: lì `uid` è null, il token viene scartato e non viene mai più
     * ritentato, quindi il dispositivo resta invisibile al server per sempre.
     *
     * Il listener copre sia l'avvio con sessione già attiva sia il login.
     * `persistFcmToken` fa `set(merge)` sul documento con id = token, quindi
     * riscrivere lo stesso token è un no-op idempotente.
     *
     * Gemello di `startAuthStateObserver` su iOS — che in più cancella il token
     * dell'utente precedente al cambio account. Qui quella pulizia manca
     * ancora: token vecchi restano finché non falliscono l'invio e vengono
     * potati lato server.
     */
    private fun startFcmTokenOwnershipObserver() {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser == null) return@addAuthStateListener
            appInitScope.launch {
                runCatching { pushNotificationManager.registerCurrentFcmToken() }
                    .onFailure {
                        KBLog.app.warning(
                            "FCM token non persistito: ${it.message}",
                            "PushToken",
                        )
                    }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * ImageLoader condiviso dell'app.
     *
     * Senza questo Coil girava sui default, tarati su un'app generica: cache disco al 2%
     * dello spazio libero e revalidazione HTTP su ogni immagine. KidBox è invece pesante di
     * media (chat, foto di famiglia, documenti, wallet) e la stragrande maggioranza degli
     * URL viene da Firebase Storage, dove il path cambia se cambia il contenuto.
     *
     * `respectCacheHeaders(false)` sfrutta proprio questo: gli URL sono di fatto immutabili,
     * quindi si evitano round-trip di revalidazione. Il rovescio della medaglia riguarda le
     * sole anteprime `og:image` dei link di terze parti, che possono restare in cache anche
     * se il sito le aggiorna — accettabile per una thumbnail di anteprima.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .respectCacheHeaders(false)
        // Usa RGB_565 (metà della memoria per pixel) solo per immagini senza canale alpha
        // e solo quando il dispositivo è sotto pressione di memoria.
        .allowRgb565(true)
        .crossfade(true)
        .build()
}
