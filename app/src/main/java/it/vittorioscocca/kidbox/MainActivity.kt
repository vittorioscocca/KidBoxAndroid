package it.vittorioscocca.kidbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.ui.CrashReportConsentDialog
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.navigation.AppNavGraph
import it.vittorioscocca.kidbox.util.CrashAnalyzer

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingPreferences = OnboardingPreferences(applicationContext)

        setContent {
            MaterialTheme {
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
}
