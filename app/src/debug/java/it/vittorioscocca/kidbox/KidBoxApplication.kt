package it.vittorioscocca.kidbox

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KidBoxApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )
    }

    /**
     * Shared [ImageLoader] for every [coil.compose.AsyncImage] in the app.
     *
     * [respectCacheHeaders] is disabled because Firebase Storage returns
     * `Cache-Control: no-cache` / `max-age=0` headers that would otherwise
     * prevent Coil from writing anything to the 150 MB on-disk cache.
     */
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
