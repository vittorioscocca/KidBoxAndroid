package it.vittorioscocca.kidbox

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.local.ThemePreference
import it.vittorioscocca.kidbox.ui.CrashReportConsentDialog
import android.content.Intent
import it.vittorioscocca.kidbox.notifications.NotificationDeepLinkRouter
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.navigation.AppNavGraph
import it.vittorioscocca.kidbox.ui.theme.KidBoxTheme
import it.vittorioscocca.kidbox.util.CrashAnalyzer
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreference: ThemePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        title = ""
        WindowCompat.setDecorFitsSystemWindows(window, true)
        applySystemBarAppearance(resolveDarkTheme())

        val onboardingPreferences = OnboardingPreferences(applicationContext)
        NotificationDeepLinkRouter.handleLaunchIntent(this, intent)

        setContent {
            val appTheme by themePreference.getThemeFlow().collectAsStateWithLifecycle(
                initialValue = themePreference.getTheme(),
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> systemDark
            }

            KidBoxTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val showConsent by CrashAnalyzer.showConsentDialog.collectAsStateWithLifecycle()
                val consentPrompt by CrashAnalyzer.consentPrompt.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(
                        navController = navController,
                        startDestination = AppDestination.Login.route,
                        onboardingPreferences = onboardingPreferences,
                    )
                    CrashReportConsentDialog(
                        visible = showConsent,
                        issueCount = consentPrompt?.issueCount ?: 0,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationDeepLinkRouter.handleLaunchIntent(this, intent)
    }

    private fun resolveDarkTheme(): Boolean =
        when (themePreference.getTheme()) {
            AppTheme.DARK -> true
            AppTheme.LIGHT -> false
            AppTheme.SYSTEM -> {
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }

    private fun applySystemBarAppearance(darkTheme: Boolean) {
        val background = if (darkTheme) COLOR_BACKGROUND_DARK else COLOR_BACKGROUND_LIGHT
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    private companion object {
        private val COLOR_BACKGROUND_LIGHT = Color.parseColor("#FFF5F3EE")
        private val COLOR_BACKGROUND_DARK = Color.parseColor("#FF1C1C1E")
    }
}
