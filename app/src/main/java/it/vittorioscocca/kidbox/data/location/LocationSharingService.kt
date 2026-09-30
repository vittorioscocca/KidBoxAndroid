package it.vittorioscocca.kidbox.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.repository.FamilyLocationRepository
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service che mantiene attivo l'invio della posizione anche quando l'app
 * è in background o chiusa. È il writer autoritativo verso Firestore tramite
 * [FamilyLocationRepository.updateMyLocation], e decide da solo quando fermarsi:
 * «Interrompi» dalla notifica, scadenza della condivisione temporanea, condivisione
 * spenta sul server. La schermata Posizione lo avvia e lo ferma, ma non serve che
 * sia aperta perché queste regole valgano.
 *
 * Lo stato «questo dispositivo condivide» sta in [LocationSharingStateStore].
 */
@AndroidEntryPoint
class LocationSharingService : Service() {

    @Inject lateinit var repository: FamilyLocationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var familyId: String = ""
    private var displayName: String = "Utente"

    // Il `LocationRequest` sotto già garantisce un fix al massimo ogni 45s (gate
    // temporale). Qui si aggiunge il gate di distanza: se in 45s ci si è mossi
    // meno di 10m, non ha senso riscrivere la stessa posizione su Firestore.
    private var lastWrittenLocation: Location? = null
    private var lastWriteAtMillis: Long = 0L

    /**
     * Le coordinate si scrivono solo dopo che il server ha confermato la
     * condivisione accesa. Alla ripresa (watchdog, boot, push) il server
     * potrebbe averla già spenta: scrivere subito lascerebbe una posizione
     * orfana che nessuno cancella. Il fix arrivato prima della conferma
     * aspetta in [pendingFix] e parte appena la conferma arriva.
     */
    private var remoteConfirmed = false
    private var pendingFix: Location? = null

    private var statusRegistration: ListenerRegistration? = null
    private var statusRetryDelayMs = STATUS_RETRY_MIN_MS
    private var statusRetryJob: Job? = null
    private var expiryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // «Interrompi» dalla notifica vale quanto il tasto nella schermata:
            // anche su Firestore. Prima fermava solo il telefono, gli altri
            // continuavano a vederti «in condivisione» col pin fermo, e alla
            // prossima apertura della schermata la condivisione ripartiva.
            // Se l'intent è arrivato via startForegroundService, il sistema PRETENDE
            // una startForeground() entro pochi secondi: la chiamiamo e poi ci fermiamo,
            // così evitiamo ForegroundServiceDidNotStartInTimeException.
            startForegroundCompat()
            stopEverywhere("interrotta dalla notifica")
            return START_NOT_STICKY
        }

        val newFamilyId = intent?.getStringExtra(EXTRA_FAMILY_ID)?.takeIf { it.isNotBlank() }
            ?: LocationSharingStateStore.familyId(this).orEmpty()
        val newDisplayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        if (newFamilyId.isBlank() || !LocationSharingStateStore.shouldBeActive(this)) {
            // Niente da condividere (o temporanea già scaduta mentre il servizio era giù).
            stopSelfSafely()
            return START_NOT_STICKY
        }

        // Un altro account su questo telefono: la condivisione del vecchio non
        // riparte. Senza account (`null`, sessione non ancora ripristinata) si
        // va avanti: senza utente non si scrive comunque niente.
        val startedBy = LocationSharingStateStore.uid(this)
        val current = repository.currentUid()
        if (startedBy != null && current != null && startedBy != current) {
            KBLog.app.info("LocationSharingService: account diverso da quello che condivideva, stop", TAG)
            stopLocally()
            return START_NOT_STICKY
        }

        if (newFamilyId != familyId) {
            resetSession()
            familyId = newFamilyId
        }
        if (newDisplayName.isNotBlank()) displayName = newDisplayName

        // Prima il permesso, poi il foreground: su Android 14+ un servizio di
        // tipo «location» avviato senza il permesso posizione non si ferma da
        // solo, lancia SecurityException e uccide l'app.
        if (!hasLocationPermission()) {
            KBLog.app.warning("LocationSharingService: permesso posizione mancante, stop", TAG)
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!startForegroundCompat()) {
            // Avvio negato perché siamo in background (riavvio del sistema con
            // START_REDELIVER_INTENT, watchdog, boot). Lo stato resta «attivo»:
            // la condivisione riprende da MainActivity.onResume appena l'utente
            // riapre l'app, che è il momento in cui Android lo permette.
            stopSelfSafely()
            return START_NOT_STICKY
        }

        scheduleExpiry()
        listenOwnStatus()
        startLocationUpdates()
        // START_REDELIVER_INTENT: se il sistema uccide e ricrea il service,
        // riconsegna l'ultimo intent con gli extra (familyId/displayName).
        return START_REDELIVER_INTENT
    }

    // ── Posizione ────────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        if (locationCallback != null) return
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { handleFix(it) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 45_000L)
            .setMinUpdateIntervalMillis(45_000L)
            .build()
        locationCallback = callback
        runCatching {
            @Suppress("MissingPermission")
            fusedClient.requestLocationUpdates(request, callback, mainLooper)
        }.onFailure { err ->
            KBLog.app.error("LocationSharingService: requestLocationUpdates fallito: ${err.message}", TAG, err)
            locationCallback = null
            stopSelfSafely()
        }
    }

    private fun handleFix(fix: Location) {
        if (familyId.isBlank()) return
        if (isExpired()) {
            stopEverywhere("condivisione temporanea scaduta")
            return
        }
        if (!isReliable(fix)) return

        if (!remoteConfirmed) {
            pendingFix = fix
            return
        }

        val now = System.currentTimeMillis()
        val previous = lastWrittenLocation
        // Il battito: anche da fermi una scrittura ogni HEARTBEAT_MS, così chi
        // guarda (e il server, che decide se mandare la push di ripresa) può
        // distinguere «fermo» da «servizio morto». Il 30/09/2026 un telefono
        // aveva la condivisione ferma da 10 giorni e nessuno poteva saperlo.
        val heartbeatDue = now - lastWriteAtMillis >= HEARTBEAT_MS
        if (previous != null && fix.distanceTo(previous) < MIN_DISTANCE_METERS && !heartbeatDue) {
            return
        }
        write(fix, now)
    }

    private fun write(fix: Location, now: Long) {
        lastWrittenLocation = fix
        lastWriteAtMillis = now
        pendingFix = null
        val targetFamilyId = familyId
        scope.launch(Dispatchers.IO) {
            runCatching {
                repository.updateMyLocation(
                    familyId = targetFamilyId,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracy = fix.accuracy.toDouble(),
                )
            }.onFailure { err ->
                KBLog.data.error("LocationSharingService: updateMyLocation fallito: ${err.message}", TAG, err)
            }
        }
    }

    /**
     * Scarta i fix che farebbero vedere agli altri un posto sbagliato: oltre
     * 2 km di imprecisione (celle telefoniche o peggio: il 30/09 sul server ce
     * n'era uno da 50 km), vecchi di più di 2 minuti (cache del sistema), o
     * grossolani quando c'è una posizione precisa scritta da poco (il pin
     * salterebbe di centinaia di metri). Stessi criteri di iOS.
     */
    private fun isReliable(fix: Location): Boolean {
        if (fix.hasAccuracy() && fix.accuracy > MAX_ACCURACY_METERS) return false
        val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
        if (ageMs > MAX_FIX_AGE_MS) return false
        val previous = lastWrittenLocation
        if (fix.hasAccuracy() && fix.accuracy > COARSE_ACCURACY_METERS &&
            previous != null && previous.hasAccuracy() && previous.accuracy <= COARSE_ACCURACY_METERS &&
            fix.time - previous.time < PRECISE_FIX_VALIDITY_MS
        ) {
            return false
        }
        return true
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        locationCallback = null
    }

    // ── Stato sul server ─────────────────────────────────────────────────────

    /**
     * Ascolta il proprio documento di stato: se la condivisione viene spenta
     * altrove (scadenza lato server, stop da un altro dispositivo, famiglia o
     * account cancellati) questo telefono smette di inviare.
     */
    private fun listenOwnStatus() {
        if (statusRegistration != null) return
        val listenedFamilyId = familyId
        statusRegistration = repository.listenMySharingStatus(
            familyId = listenedFamilyId,
            onServerStatus = { sharing ->
                if (listenedFamilyId != familyId) return@listenMySharingStatus
                statusRetryDelayMs = STATUS_RETRY_MIN_MS
                if (sharing) {
                    remoteConfirmed = true
                    pendingFix?.let { write(it, System.currentTimeMillis()) }
                } else {
                    KBLog.app.info("LocationSharingService: condivisione spenta sul server, stop", TAG)
                    stopLocally()
                }
            },
            onError = { err ->
                KBLog.app.warning("LocationSharingService: stato non leggibile: ${err.message}", TAG)
                val denied = (err as? FirebaseFirestoreException)?.code ==
                    FirebaseFirestoreException.Code.PERMISSION_DENIED
                val sameAccount = repository.currentUid() == LocationSharingStateStore.uid(this)
                if (denied && sameAccount) {
                    // Non più membro di quella famiglia: le scritture verrebbero
                    // negate comunque, e il GPS resterebbe acceso per niente.
                    stopLocally()
                } else {
                    retryStatusListener()
                }
            },
        )
        if (statusRegistration == null) retryStatusListener() // sessione non ancora pronta
    }

    /**
     * Un listener andato in errore non si riprende da solo. L'attesa raddoppia
     * fino a 15 minuti: senza account ogni tentativo è una lettura negata.
     */
    private fun retryStatusListener() {
        statusRegistration?.remove()
        statusRegistration = null
        statusRetryJob?.cancel()
        val wait = statusRetryDelayMs
        statusRetryDelayMs = (statusRetryDelayMs * 2).coerceAtMost(STATUS_RETRY_MAX_MS)
        statusRetryJob = scope.launch {
            delay(wait)
            listenOwnStatus()
        }
    }

    // ── Scadenza e stop ──────────────────────────────────────────────────────

    private fun isExpired(): Boolean {
        val expiresAt = LocationSharingStateStore.expiresAt(this)
        return expiresAt != 0L && expiresAt <= System.currentTimeMillis()
    }

    /**
     * La scadenza della condivisione temporanea la fa rispettare il servizio,
     * non la schermata: prima la applicava solo il ViewModel, e a schermata
     * chiusa il telefono continuava a mandare la posizione oltre il tempo scelto.
     * Il controllo si ripete anche a ogni fix (vedi [handleFix]): il `delay` può
     * slittare mentre la CPU dorme.
     */
    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val expiresAt = LocationSharingStateStore.expiresAt(this)
        if (expiresAt == 0L) return
        expiryJob = scope.launch {
            delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
            stopEverywhere("condivisione temporanea scaduta")
        }
    }

    /** Ferma la condivisione qui e su Firestore (gli altri vedono lo stop). */
    private fun stopEverywhere(reason: String) {
        val stoppedFamilyId = familyId.ifBlank { LocationSharingStateStore.familyId(this).orEmpty() }
        KBLog.app.info("LocationSharingService: stop ($reason)", TAG)
        LocationSharingStateStore.markInactive(this)
        LocationSharingWatchdogWorker.cancel(this)
        if (stoppedFamilyId.isNotBlank()) {
            // Fuori dallo scope del servizio, che muore con lui: la scrittura
            // deve partire anche se il servizio si ferma subito dopo.
            detachedScope.launch {
                runCatching { repository.stopSharing(stoppedFamilyId) }
                    .onFailure { KBLog.data.error("LocationSharingService: stop su Firestore fallito: ${it.message}", TAG, it) }
            }
        }
        stopSelfSafely()
    }

    /** Ferma solo questo telefono: il server sa già che la condivisione è spenta. */
    private fun stopLocally() {
        LocationSharingStateStore.markInactive(this)
        LocationSharingWatchdogWorker.cancel(this)
        stopSelfSafely()
    }

    private fun resetSession() {
        statusRegistration?.remove()
        statusRegistration = null
        statusRetryJob?.cancel()
        statusRetryDelayMs = STATUS_RETRY_MIN_MS
        remoteConfirmed = false
        pendingFix = null
        lastWrittenLocation = null
        lastWriteAtMillis = 0L
    }

    /**
     * Porta il servizio in primo piano. `false` se Android lo ha negato.
     *
     * Da Android 12 un servizio in primo piano non può partire mentre l'app è
     * in background (ForegroundServiceStartNotAllowedException), e da Android
     * 14 un servizio di tipo «location» richiede che il permesso posizione sia
     * utilizzabile in quel momento (SecurityException, tipico con il permesso
     * «solo mentre usi l'app» e l'app chiusa). Entrambe arrivano qui dentro, in
     * onStartCommand, dove nessuno le catturava: settembre 2026, 19 crash su 30
     * in due settimane, su Samsung, Honor e Oppo con Android 15 e 16.
     */
    private fun startForegroundCompat(): Boolean {
        val stopIntent = Intent(this, LocationSharingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                // NEW_TASK necessario: parte da un Service (contesto non-Activity). Senza,
                // con l'app in background Android può aprire un secondo task invece di
                // riportare avanti quello esistente.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.location_sharing_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Interrompi", stopPending)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            KBLog.app.warning("LocationSharingService: foreground negato (permesso): ${e.message}", TAG)
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException estende IllegalStateException
            // ed esiste solo da API 31: si cattura la superclasse.
            KBLog.app.warning("LocationSharingService: foreground negato (app in background): ${e.message}", TAG)
            false
        }
    }

    private fun stopSelfSafely() {
        stopLocationUpdates()
        expiryJob?.cancel()
        expiryJob = null
        resetSession()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onDestroy() {
        stopLocationUpdates()
        resetSession()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationSharingSvc"
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 4711
        private const val MIN_DISTANCE_METERS = 10f
        private const val HEARTBEAT_MS = 15 * 60 * 1000L
        private const val MAX_ACCURACY_METERS = 2000f
        private const val COARSE_ACCURACY_METERS = 200f
        private const val PRECISE_FIX_VALIDITY_MS = 5 * 60 * 1000L
        private const val MAX_FIX_AGE_MS = 2 * 60 * 1000L
        private const val STATUS_RETRY_MIN_MS = 60_000L
        private const val STATUS_RETRY_MAX_MS = 15 * 60_000L
        const val ACTION_STOP = "it.vittorioscocca.kidbox.action.STOP_LOCATION_SHARING"
        const val EXTRA_FAMILY_ID = "extra_family_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        /** Per le scritture che devono sopravvivere al servizio (lo stop su Firestore). */
        private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start(context: Context, familyId: String, displayName: String) {
            if (familyId.isBlank()) return
            val intent = Intent(context, LocationSharingService::class.java).apply {
                putExtra(EXTRA_FAMILY_ID, familyId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Riavvia il servizio se questo telefono dovrebbe condividere. Per i
         * punti da cui Android permette l'avvio in primo piano anche con l'app
         * in background: la push ad alta priorità di ripresa e l'evento di una
         * zona. Da un worker o da un timer verrebbe negato (vedi
         * [startForegroundCompat]). `start` è idempotente.
         */
        fun resumeIfNeeded(context: Context, origin: String) {
            runCatching {
                if (!LocationSharingStateStore.shouldBeActive(context)) return
                val familyId = LocationSharingStateStore.familyId(context) ?: return
                KBLog.app.info("LocationSharingService: ripresa da $origin", TAG)
                start(context, familyId, LocationSharingStateStore.displayName(context))
            }.onFailure {
                KBLog.app.warning("LocationSharingService: ripresa da $origin fallita: ${it.message}", TAG)
            }
        }

        fun stop(context: Context) {
            // stopService NON impone il vincolo startForeground (a differenza di
            // startForegroundService): è il modo sicuro per fermare il service, anche
            // quando non è in esecuzione (no-op). Il cleanup avviene in onDestroy.
            val intent = Intent(context, LocationSharingService::class.java)
            runCatching { context.stopService(intent) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Condivisione posizione",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notifica persistente mentre condividi la tua posizione con la famiglia"
                    setShowBadge(false)
                },
            )
        }
    }
}
