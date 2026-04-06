package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    fun hasSeenOnboarding(): Boolean =
        prefs.getBoolean(KEY_SEEN, false)

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    private companion object {
        private const val KEY_SEEN = "has_seen_onboarding"
    }
}
