package it.vittorioscocca.kidbox.util.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Traduce il ciclo di vita del processo in eventi analytics: `session_start`
 * quando l'app passa in foreground, flush del buffer quando va in background.
 *
 * Equivale allo `scenePhase` di `KidBoxApp.swift` su iOS.
 *
 * Si conta il numero di Activity avviate invece di usare `ProcessLifecycleOwner`
 * perché `androidx.lifecycle:lifecycle-process` non è tra le dipendenze del
 * progetto: aggiungerla solo per questo non vale il costo. Il contatore
 * 0→1 / 1→0 dà lo stesso segnale ed è immune alle rotazioni di schermo, che
 * distruggono e ricreano l'Activity senza mai portare il conteggio a zero.
 */
class KBAnalyticsLifecycleObserver : Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) {
            // Foreground. `logSessionStart` applica da sé la finestra di 30 minuti:
            // rientri rapidi non contano come nuove sessioni.
            KBAnalytics.logSessionStart(KBAnalyticsEntryPoint.ICON)
            AppAnalytics.trackAppOpen(activity.applicationContext)
        }
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            // Le letture sono bufferizzate in memoria: qui è l'unico punto in cui
            // partono. Se si perde qualcosa è un costo accettabile.
            KBAnalytics.flush()

            OnboardingAnalyticsState.lastStepSeen?.let { step ->
                AppAnalytics.onboardingAbandoned(activity.applicationContext, step)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
