package it.vittorioscocca.kidbox

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import it.vittorioscocca.kidbox.notifications.KidBoxFirebaseMessagingService
import it.vittorioscocca.kidbox.util.CrashAnalyzer
import it.vittorioscocca.kidbox.util.KBCrashHandler
import it.vittorioscocca.kidbox.util.KBFileLogger
import it.vittorioscocca.kidbox.util.KidBoxApplicationHolder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KidBoxApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        KidBoxApplicationHolder.applicationContext = applicationContext
        KBFileLogger.init(this)
        KBCrashHandler.install()
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        KidBoxFirebaseMessagingService.createNotificationChannels(this)
        appInitScope.launch {
            CrashAnalyzer.analyzeIfNeeded(this@KidBoxApplication)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
