package it.vittorioscocca.kidbox.util.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object OnboardingAnalyticsState {
    var lastStepSeen: String? = null
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
    }

    fun contentSharedRead(context: Context, type: String) {
        log(context, "content_shared_read") {
            putString("content_type", type)
        }
    }

    fun aiPaywallShown(context: Context, analyticsContext: String) {
        log(context, "ai_paywall_shown") {
            putString("context", analyticsContext)
        }
    }

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

    fun subscriptionStarted(context: Context, plan: String, trial: Boolean) {
        log(context, "subscription_started") {
            putString("plan", plan)
            putBoolean("trial", trial)
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
