package it.vittorioscocca.kidbox.data.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.vittorioscocca.kidbox.util.KBLog
import java.util.concurrent.TimeUnit

/**
 * Watchdog periodico della SOLA condivisione posizione live: se dovrebbe essere attiva
 * ([LocationSharingStateStore]) ma il foreground service è stato ucciso dal sistema o da
 * un battery-killer OEM, lo riavvia. [LocationSharingService.start] è idempotente.
 *
 * NB: di proposito NON ri-registra le geofence. Ri-registrarle ogni 15 min faceva
 * scattare notifiche di "arrivo" ripetute per chi era fermo dentro una zona. Le geofence
 * vengono ripristinate dove serve davvero: avvio app, boot, apertura schermata Posizione.
 *
 * Nota: su Android 12+ l'avvio di un foreground service da background può essere negato
 * (ForegroundServiceStartNotAllowedException). È quindi best-effort: cattura l'eccezione
 * e riprova al ciclo successivo / al prossimo risveglio del dispositivo.
 */
class LocationSharingWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        if (!LocationSharingStateStore.shouldBeActive(ctx)) {
            // Non c'è più condivisione da sorvegliare: smetti di rischedularti.
            cancel(ctx)
            return Result.success()
        }

        val familyId = ctx.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
            .getString("active_family_id", null)?.trim().orEmpty()
        if (familyId.isNotBlank()) {
            runCatching {
                LocationSharingService.start(ctx, familyId, LocationSharingStateStore.displayName(ctx))
            }.onFailure { err ->
                KBLog.app.warning("Watchdog: restart service negato/fallito: ${err.message}", TAG)
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "LocationWatchdog"
        const val WORK_NAME = "location_sharing_watchdog"

        fun enqueue(context: Context) {
            val work = PeriodicWorkRequestBuilder<LocationSharingWatchdogWorker>(
                15, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
