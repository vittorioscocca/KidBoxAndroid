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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.repository.FamilyLocationRepository
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service che mantiene attivo l'invio della posizione anche quando l'app
 * è in background o chiusa. È il writer autoritativo verso Firestore tramite
 * [FamilyLocationRepository.updateMyLocation].
 *
 * Avvio/arresto sono guidati da FamilyLocationViewModel (start/stopSharing).
 */
@AndroidEntryPoint
class LocationSharingService : Service() {

    @Inject lateinit var repository: FamilyLocationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var familyId: String = ""
    private var displayName: String = "Utente"

    // Il `LocationRequest` sotto già garantisce un fix al massimo ogni 45s (gate
    // temporale). Qui si aggiunge il gate di distanza: se in 45s ci si è mossi
    // meno di 10m, non ha senso riscrivere la stessa posizione su Firestore.
    private var lastWrittenLocation: android.location.Location? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // L'utente ha premuto "Interrompi": azzera lo stato persistito e ferma il
                // watchdog, così non riavvia il service.
                LocationSharingStateStore.markInactive(this)
                LocationSharingWatchdogWorker.cancel(this)
                // Se l'intent è arrivato via startForegroundService, il sistema PRETENDE
                // una startForeground() entro pochi secondi: la chiamiamo e poi ci fermiamo,
                // così evitiamo ForegroundServiceDidNotStartInTimeException.
                startForegroundCompat()
                stopSelfSafely()
                return START_NOT_STICKY
            }
        }

        val newFamilyId = intent?.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
        val newDisplayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        if (newFamilyId.isBlank()) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        familyId = newFamilyId
        if (newDisplayName.isNotBlank()) displayName = newDisplayName

        startForegroundCompat()

        if (!hasLocationPermission()) {
            KBLog.app.warning("LocationSharingService: permesso posizione mancante, stop", TAG)
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        // START_REDELIVER_INTENT: se il sistema uccide e ricrea il service,
        // riconsegna l'ultimo intent con gli extra (familyId/displayName).
        return START_REDELIVER_INTENT
    }

    private fun startLocationUpdates() {
        if (locationCallback != null) return
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation ?: return
                if (familyId.isBlank()) return

                val previous = lastWrittenLocation
                if (previous != null && last.distanceTo(previous) < MIN_DISTANCE_METERS) {
                    return
                }
                lastWrittenLocation = last

                scope.launch {
                    runCatching {
                        repository.updateMyLocation(
                            familyId = familyId,
                            latitude = last.latitude,
                            longitude = last.longitude,
                            accuracy = last.accuracy.toDouble(),
                        )
                    }.onFailure { err ->
                        KBLog.data.error("LocationSharingService: updateMyLocation fallito: ${err.message}", TAG, err)
                    }
                }
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

    private fun stopLocationUpdates() {
        locationCallback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        locationCallback = null
        lastWrittenLocation = null
    }

    private fun startForegroundCompat() {
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
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Condivisione posizione attiva")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Interrompi", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        stopLocationUpdates()
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
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationSharingSvc"
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 4711
        private const val MIN_DISTANCE_METERS = 10f
        const val ACTION_STOP = "it.vittorioscocca.kidbox.action.STOP_LOCATION_SHARING"
        const val EXTRA_FAMILY_ID = "extra_family_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        fun start(context: Context, familyId: String, displayName: String) {
            if (familyId.isBlank()) return
            val intent = Intent(context, LocationSharingService::class.java).apply {
                putExtra(EXTRA_FAMILY_ID, familyId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
            ContextCompat.startForegroundService(context, intent)
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
