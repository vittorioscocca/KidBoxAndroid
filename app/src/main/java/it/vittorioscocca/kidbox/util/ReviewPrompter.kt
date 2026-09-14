package it.vittorioscocca.kidbox.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.play.core.review.ReviewManagerFactory
import it.vittorioscocca.kidbox.BuildConfig
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decide quando chiedere una recensione con il popup nativo di Play.
 *
 * Solo In-App Review API: non esiste un'API per inviare a Play un voto
 * raccolto dall'app, e le norme vietano di chiedere prima "ti piace?" e
 * mostrare il popup solo a chi dice sì.
 *
 * La quota la decide Play (non è pubblicata) e l'API non dice se il popup è
 * comparso né se l'utente ha votato: per questo l'evento analytics si chiama
 * "requested". Compare solo con l'app installata dal Play Store (test interno
 * o internal app sharing), mai da una build installata da Android Studio.
 *
 * Stato solo sul dispositivo: nessuna lettura Firestore. Stesse regole di
 * `ReviewPrompter.swift` su iOS.
 */
object ReviewPrompter : Application.ActivityLifecycleCallbacks {

    enum class Moment(val analyticsName: String) {
        /** Piano Fitness o Alimentare generato con successo. */
        AI_PLAN_GENERATED("ai_plan"),

        /** Un contenuto creato; conta solo dalla soglia [MIN_CONTENT_CREATED]. */
        CONTENT_CREATED("content_created"),

        /** Un contenuto di un altro membro aperto: la famiglia usa l'app insieme. */
        SHARED_CONTENT_READ("shared_read"),
    }

    private const val MIN_DAYS_SINCE_FIRST_OPEN = 7
    private const val MIN_OPEN_DAYS = 5
    private const val MIN_DAYS_BETWEEN_REQUESTS = 120
    private const val MIN_CONTENT_CREATED = 10
    private const val MIN_SHARED_READS = 5
    private const val PRESENTATION_DELAY_MS = 1_500L

    private const val PREFS_NAME = "kb_review_prompt_prefs"
    private const val KEY_FIRST_OPEN = "first_open_millis"
    private const val KEY_OPEN_DAYS = "open_days"
    private const val KEY_LAST_OPEN_DAY = "last_open_epoch_day"
    private const val KEY_CONTENT_CREATED = "content_created"
    private const val KEY_SHARED_READS = "shared_reads"
    private const val KEY_LAST_REQUEST = "last_request_millis"
    private const val KEY_LAST_REQUEST_VERSION = "last_request_version"

    /** Scritta da `AppAnalytics.trackAppOpen`: chi aggiorna l'app non riparte da zero giorni. */
    private const val ANALYTICS_PREFS_NAME = "kb_app_analytics_prefs"
    private const val ANALYTICS_KEY_INSTALL_DATE = "install_date_millis"

    /** Link fisso della voce "Valuta KidBox" nelle Impostazioni. */
    private const val PLAY_PACKAGE = "it.vittorioscocca.kidbox"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean(false)
    private var startedActivities = 0
    private var resumedActivity: WeakReference<Activity>? = null

    /** Da chiamare subito dopo un'azione andata a buon fine. Sicura da qualunque thread. */
    fun note(context: Context, moment: Moment) {
        val prefs = prefs(context)
        when (moment) {
            Moment.AI_PLAN_GENERATED -> Unit
            Moment.CONTENT_CREATED -> {
                val count = prefs.getInt(KEY_CONTENT_CREATED, 0) + 1
                prefs.edit().putInt(KEY_CONTENT_CREATED, count).apply()
                if (count < MIN_CONTENT_CREATED) return
            }
            Moment.SHARED_CONTENT_READ -> {
                val count = prefs.getInt(KEY_SHARED_READS, 0) + 1
                prefs.edit().putInt(KEY_SHARED_READS, count).apply()
                if (count < MIN_SHARED_READS) return
            }
        }
        if (!isEligible(context) || !pending.compareAndSet(false, true)) return

        // A schermata ferma: lascia chiudere il form o il bottom sheet del salvataggio.
        mainHandler.postDelayed({ launch(context.applicationContext, moment) }, PRESENTATION_DELAY_MS)
    }

    /** Intent per la voce "Valuta KidBox": app del Play Store, o il sito se manca. */
    fun storeIntent(context: Context): Intent {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (market.resolveActivity(context.packageManager) != null) return market
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_PACKAGE"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun launch(appContext: Context, moment: Moment) {
        val activity = resumedActivity?.get()
        // Senza focus c'è una finestra sopra l'Activity (dialog, bottom sheet,
        // permessi di sistema): lì il popup interromperebbe.
        if (activity == null || activity.isFinishing || !activity.hasWindowFocus() || !isEligible(appContext)) {
            pending.set(false)
            return
        }
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful && !activity.isFinishing) {
                prefs(appContext).edit()
                    .putLong(KEY_LAST_REQUEST, System.currentTimeMillis())
                    .putString(KEY_LAST_REQUEST_VERSION, BuildConfig.VERSION_NAME)
                    .apply()
                manager.launchReviewFlow(activity, task.result)
                AppAnalytics.reviewPromptRequested(appContext, moment.analyticsName)
            }
            pending.set(false)
        }
    }

    private fun isEligible(context: Context): Boolean {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val firstOpen = prefs.getLong(KEY_FIRST_OPEN, 0L)
        if (firstOpen == 0L || daysBetween(firstOpen, now) < MIN_DAYS_SINCE_FIRST_OPEN) return false
        if (prefs.getInt(KEY_OPEN_DAYS, 0) < MIN_OPEN_DAYS) return false

        val lastRequest = prefs.getLong(KEY_LAST_REQUEST, 0L)
        if (lastRequest != 0L && daysBetween(lastRequest, now) < MIN_DAYS_BETWEEN_REQUESTS) return false
        return prefs.getString(KEY_LAST_REQUEST_VERSION, null) != BuildConfig.VERSION_NAME
    }

    /** Conta i giorni distinti di utilizzo a ogni passaggio in foreground. */
    private fun registerAppOpen(context: Context) {
        val prefs = prefs(context)
        val editor = prefs.edit()
        if (!prefs.contains(KEY_FIRST_OPEN)) {
            val seed = context.getSharedPreferences(ANALYTICS_PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(ANALYTICS_KEY_INSTALL_DATE, System.currentTimeMillis())
            editor.putLong(KEY_FIRST_OPEN, seed)
        }
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong(KEY_LAST_OPEN_DAY, Long.MIN_VALUE) != today) {
            editor.putLong(KEY_LAST_OPEN_DAY, today)
            editor.putInt(KEY_OPEN_DAYS, prefs.getInt(KEY_OPEN_DAYS, 0) + 1)
        }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun daysBetween(fromMillis: Long, toMillis: Long): Long =
        TimeUnit.MILLISECONDS.toDays(toMillis - fromMillis)

    // ── Ciclo di vita: stesso conteggio 0→1 di `KBAnalyticsLifecycleObserver` ──

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) registerAppOpen(activity.applicationContext)
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
