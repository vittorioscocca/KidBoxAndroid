package it.vittorioscocca.kidbox

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import it.vittorioscocca.kidbox.data.health.HealthOcrRecoveryMigration
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.remote.AppCheckTokenCache
import it.vittorioscocca.kidbox.notifications.HousePaymentReminderScheduler
import it.vittorioscocca.kidbox.notifications.VehicleDeadlineReminderScheduler
import it.vittorioscocca.kidbox.ui.screens.ai.planning.WeeklySummaryService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class KidBoxApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var healthOcrRecoveryMigration: HealthOcrRecoveryMigration

    @Inject
    lateinit var vehicleDeadlineReminderScheduler: VehicleDeadlineReminderScheduler

    @Inject
    lateinit var vehicleDao: VehicleDao

    @Inject
    lateinit var housePaymentDao: HousePaymentDao

    @Inject
    lateinit var housePaymentReminderScheduler: HousePaymentReminderScheduler

    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install()
        appInitScope.launch {
            delay(750)
            AppCheckTokenCache.warmUp()
        }
        runCatching { WorkManager.initialize(this, workManagerConfiguration) }
        runCatching { healthOcrRecoveryMigration.runIfNeeded(this) }
        val familyId = getSharedPreferences("kidbox_prefs", MODE_PRIVATE)
            .getString("active_family_id", null)
        if (!familyId.isNullOrBlank()) {
            WeeklySummaryService.scheduleWeeklyIfNeeded(this, familyId)
            appInitScope.launch(Dispatchers.IO) {
                runCatching {
                    vehicleDao.listActiveByFamily(familyId).forEach { entity ->
                        vehicleDeadlineReminderScheduler.syncVehicle(entity)
                    }
                    housePaymentDao.listActiveByFamily(familyId).forEach { hp ->
                        housePaymentReminderScheduler.syncPayment(hp)
                    }
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("chat_image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
