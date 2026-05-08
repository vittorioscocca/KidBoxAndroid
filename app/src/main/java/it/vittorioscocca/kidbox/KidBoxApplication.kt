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
import it.vittorioscocca.kidbox.ui.screens.ai.planning.WeeklySummaryService
import javax.inject.Inject

@HiltAndroidApp
class KidBoxApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var healthOcrRecoveryMigration: HealthOcrRecoveryMigration

    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install()
        runCatching { WorkManager.initialize(this, workManagerConfiguration) }
        runCatching { healthOcrRecoveryMigration.runIfNeeded(this) }
        val familyId = getSharedPreferences("kidbox_prefs", MODE_PRIVATE)
            .getString("active_family_id", null)
        if (!familyId.isNullOrBlank()) {
            WeeklySummaryService.scheduleWeeklyIfNeeded(this, familyId)
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
