package it.vittorioscocca.kidbox.util.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.FirstContentInvitePrompt
import it.vittorioscocca.kidbox.util.ReviewPrompter

object OnboardingAnalyticsState {
    var lastStepSeen: String? = null

    /**
     * Step per cui `onboarding_abandoned` è già partito. Senza questo l'evento
     * scattava a OGNI background finché il wizard era aperto — telefonata,
     * mail di verifica, condivisione del link d'invito dall'ultima pagina — e
     * GA4 lo leggeva come «80% di abbandono». Al massimo uno per step.
     */
    var abandonReportedStep: String? = null
}

object AppAnalytics {

    fun signupStarted(context: Context, method: String) {
        log(context, "signup_started") {
            putString("method", method)
        }
    }

    fun signupCompleted(context: Context, method: String) {
        log(context, "signup_completed") {
            putString("method", method)
        }
    }

    /**
     * Tap su un provider OAuth (Google/Apple/Facebook): a differenza di
     * `signupStarted`, scatta sempre, anche per un login di un utente già
     * esistente — non si può sapere se è un nuovo account finché Firebase non
     * risponde con `isNewUser`.
     */
    fun loginAttempted(context: Context, method: String) {
        log(context, "login_attempted") {
            putString("method", method)
        }
    }

    fun signupMethodSelected(context: Context, method: String) {
        log(context, "signup_method_selected") {
            putString("method", method)
        }
    }

    fun preSignupScreenShown(context: Context, screenName: String) {
        log(context, "pre_signup_screen_shown") {
            putString("screen_name", screenName)
        }
    }

    fun preSignupScreenDismissed(context: Context, screenName: String) {
        log(context, "pre_signup_screen_dismissed") {
            putString("screen_name", screenName)
        }
    }

    fun onboardingStepShown(context: Context, stepName: String, stepNumber: Int) {
        log(context, "onboarding_step_shown") {
            putString("step_name", stepName)
            putInt("step_number", stepNumber)
        }
    }

    fun onboardingStepCompleted(context: Context, stepName: String) {
        log(context, "onboarding_step_completed") {
            putString("step_name", stepName)
        }
    }

    fun onboardingCompleted(context: Context, totalDurationSeconds: Int) {
        log(context, "onboarding_completed") {
            putInt("total_duration_seconds", totalDurationSeconds)
        }
    }

    fun onboardingAbandoned(context: Context, lastStepSeen: String) {
        log(context, "onboarding_abandoned") {
            putString("last_step_seen", lastStepSeen)
        }
    }

    fun familyCreated(context: Context) {
        log(context, "family_created")
    }

    fun onboardingInviteStepShown(context: Context) {
        log(context, "onboarding_invite_step_shown")
    }

    fun onboardingInviteStepSkipped(context: Context) {
        log(context, "onboarding_invite_step_skipped")
    }

    fun inviteGenerated(context: Context) {
        log(context, "invite_generated")
    }

    fun inviteShared(context: Context, channel: String) {
        log(context, "invite_shared") {
            putString("channel", channel)
        }
    }

    // Foglio d'invito contestuale (FirstContentInvitePrompt). `trigger` dice
    // quale occasione l'ha aperto (oggi solo `first_content`).
    fun invitePromptShown(context: Context, trigger: String, contentType: String) {
        log(context, "invite_prompt_shown") {
            putString("trigger", trigger)
            putString("content_type", contentType)
        }
    }

    fun invitePromptAccepted(context: Context, trigger: String, contentType: String) {
        log(context, "invite_prompt_accepted") {
            putString("trigger", trigger)
            putString("content_type", contentType)
        }
    }

    fun invitePromptDismissed(context: Context, trigger: String, contentType: String) {
        log(context, "invite_prompt_dismissed") {
            putString("trigger", trigger)
            putString("content_type", contentType)
        }
    }

    fun familyJoinAttempted(context: Context) {
        log(context, "family_join_attempted")
    }

    fun familyJoined(context: Context, vaultKeyAvailable: Boolean) {
        log(context, "family_joined") {
            putBoolean("vault_key_available", vaultKeyAvailable)
        }
    }

    fun familyJoinFailed(context: Context, reason: String) {
        log(context, "family_join_failed") {
            putString("reason", reason)
        }
    }

    fun screenView(context: Context, name: String) {
        log(context, "screen_view") {
            putString("screen_name", name)
        }
    }

    fun featureFirstUse(context: Context, feature: String) {
        log(context, "feature_first_use") {
            putString("feature", feature)
        }
    }

    fun contentCreated(context: Context, type: String) {
        log(context, "content_created") {
            putString("content_type", type)
        }
        // Stesso punto di passaggio di tutti i salvataggi: evita di ripetere
        // la chiamata in ogni schermata.
        ReviewPrompter.note(context, ReviewPrompter.Moment.CONTENT_CREATED)
        FirstContentInvitePrompt.noteContentCreated(context, type)
    }

    fun contentSharedRead(context: Context, type: String) {
        log(context, "content_shared_read") {
            putString("content_type", type)
        }
        ReviewPrompter.note(context, ReviewPrompter.Moment.SHARED_CONTENT_READ)
    }

    /** Richiesta del popup di recensione: Play può non mostrarlo, e non dice mai se l'ha fatto. */
    fun reviewPromptRequested(context: Context, trigger: String) {
        log(context, "review_prompt_requested") {
            putString("trigger", trigger)
        }
    }

    fun aiPaywallShown(context: Context, analyticsContext: String) {
        log(context, "ai_paywall_shown") {
            putString("context", analyticsContext)
        }
    }

    // ── Notizie (stessi eventi di iOS) ──────────────────────────────────────

    /** Edizione caricata: notizie, eventi, messaggi scalati adesso (0 se già pagata), parte in preparazione. */
    fun newsOpened(context: Context, items: Int, events: Int, units: Int, preparing: Boolean) {
        log(context, "news_opened") {
            putLong("items", items.toLong())
            putLong("events", events.toLong())
            putLong("units", units.toLong())
            putLong("preparing", if (preparing) 1L else 0L)
        }
    }

    /** Il «+» di un evento delle Notizie (il salvataggio vero lo conta `content_created`). */
    fun newsEventAddTapped(context: Context) {
        log(context, "news_event_add")
    }

    fun newsItemOpened(context: Context, kind: String, category: String, level: String) {
        log(context, "news_item_opened") {
            putString("kind", kind)
            putString("category", category)
            putString("level", level)
        }
    }

    fun newsOffersSearched(context: Context, offers: Int, units: Int) {
        log(context, "news_offers_searched") {
            putLong("offers", offers.toLong())
            putLong("units", units.toLong())
        }
    }

    fun newsActivated(context: Context) = log(context, "news_activated")

    fun aiMessageSent(context: Context, agentType: String, plan: String) {
        log(context, "ai_message_sent") {
            putString("agent_type", agentType)
            putString("plan", plan)
        }
    }

    fun aiBriefingReceived(context: Context) {
        log(context, "ai_briefing_received")
    }

    fun paywallShown(context: Context, triggerFeature: String, planShown: String) {
        log(context, "paywall_shown") {
            putString("trigger_feature", triggerFeature)
            putString("plan_shown", planShown)
        }
    }

    /** Prova Pro attivata dal pulsante (non dalla creazione della famiglia). */
    fun proTrialStarted(context: Context, triggerFeature: String) {
        log(context, "pro_trial_started") {
            putString("trigger_feature", triggerFeature)
        }
    }

    /** Un membro che non è il proprietario gli chiede di attivare la prova. */
    fun proTrialOwnerAsked(context: Context) {
        log(context, "pro_trial_owner_asked")
    }

    // Funnel d'acquisto: paywall_shown → purchase_started → subscription_started,
    // con le due uscite purchase_cancelled / purchase_failed. Tutti portano
    // `trigger_feature`, così ogni passo si lega alla schermata che ha aperto
    // il paywall. Stessi nomi e valori su iOS.

    fun subscriptionStarted(context: Context, plan: String, trial: Boolean, triggerFeature: String) {
        log(context, "subscription_started") {
            putString("plan", plan)
            putBoolean("trial", trial)
            putString("trigger_feature", triggerFeature)
        }
    }

    /** Tocco su «Abbonati» da chi può abbonarsi: parte il foglio di Play. */
    fun purchaseStarted(context: Context, plan: String, triggerFeature: String) {
        log(context, "purchase_started") {
            putString("plan", plan)
            putString("trigger_feature", triggerFeature)
        }
    }

    /** Foglio di Play chiuso senza pagare. */
    fun purchaseCancelled(context: Context, plan: String, triggerFeature: String) {
        log(context, "purchase_cancelled") {
            putString("plan", plan)
            putString("trigger_feature", triggerFeature)
        }
    }

    /**
     * Acquisto non concluso per un motivo diverso dalla rinuncia. `reason`:
     * not_owner, product_unavailable, no_offer, launch_error, billing_error,
     * pending, server_error. Per billing_error/launch_error `responseCode` è
     * il codice di Play.
     */
    fun purchaseFailed(
        context: Context,
        plan: String,
        triggerFeature: String,
        reason: String,
        responseCode: Int? = null,
    ) {
        log(context, "purchase_failed") {
            putString("plan", plan)
            putString("trigger_feature", triggerFeature)
            putString("reason", reason)
            responseCode?.let { putInt("response_code", it) }
        }
    }

    fun appOpen(context: Context, daysSinceInstall: Int, daysSinceLastOpen: Int) {
        log(context, "app_open") {
            putInt("days_since_install", daysSinceInstall)
            putInt("days_since_last_open", daysSinceLastOpen)
        }
    }

    private const val PREFS_NAME = "kb_app_analytics_prefs"
    private const val KEY_INSTALL_DATE = "install_date_millis"
    private const val KEY_LAST_OPEN_DATE = "last_open_date_millis"
    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    fun trackAppOpen(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val installDate = if (prefs.contains(KEY_INSTALL_DATE)) {
            prefs.getLong(KEY_INSTALL_DATE, now)
        } else {
            prefs.edit().putLong(KEY_INSTALL_DATE, now).apply()
            now
        }

        val lastOpenDate = prefs.getLong(KEY_LAST_OPEN_DATE, installDate)
        prefs.edit().putLong(KEY_LAST_OPEN_DATE, now).apply()

        val daysSinceInstall = ((now - installDate) / MILLIS_PER_DAY).toInt()
        val daysSinceLastOpen = ((now - lastOpenDate) / MILLIS_PER_DAY).toInt()
        appOpen(context, daysSinceInstall, daysSinceLastOpen)
    }

    private fun log(context: Context, name: String, build: (Bundle.() -> Unit)? = null) {
        val bundle = build?.let { Bundle().apply(it) }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }
}
