package it.vittorioscocca.kidbox

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import it.vittorioscocca.kidbox.data.update.AppUpdateChecker
import it.vittorioscocca.kidbox.ui.CrashReportConsentDialog
import it.vittorioscocca.kidbox.ui.EdgeToEdgeController
import it.vittorioscocca.kidbox.ui.UpdateAvailableDialog
import android.content.Intent
import it.vittorioscocca.kidbox.notifications.NotificationDeepLinkRouter
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.navigation.AppNavGraph
import it.vittorioscocca.kidbox.ui.theme.KidBoxTheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.CrashAnalyzer
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var themePreference: ThemePreference

    @Inject
    lateinit var appUpdateChecker: AppUpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        title = ""
        // Edge-to-edge esplicito su TUTTE le versioni, non solo dove è imposto.
        //
        // Con `targetSdk 36` Android ignora `windowOptOutEdgeToEdgeEnforcement`:
        // il vecchio modello (`setDecorFitsSystemWindows(window, true)`, sistema
        // che rimpicciolisce la finestra per te) non è più disponibile. Metterlo
        // a `false` qui, invece di lasciare che sia il framework a farlo solo su
        // Android 16, evita di avere DUE comportamenti diversi a seconda del
        // dispositivo: il padding lo mette la radice della UI, sempre allo
        // stesso modo, e si testa una configurazione sola.
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

                var updateVersionCode by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    updateVersionCode = appUpdateChecker.checkForUpdate()
                }

                // Riproduce in un punto solo quello che prima faceva il sistema.
                //
                // `systemBars ∪ displayCutout` e NON `safeDrawing`: safeDrawing
                // include anche la tastiera, e spostando tutta l'app verso
                // l'alto renderebbe inerti gli `imePadding` già presenti nelle
                // schermate con input (chat, note, calendario), che gestiscono
                // la tastiera meglio di una traslazione globale.
                //
                // Il cutout serve perché il tema dichiara
                // `windowLayoutInDisplayCutoutMode=shortEdges`: senza, in
                // orizzontale il contenuto finirebbe sotto la tacca.
                // Una schermata alla volta può chiedere il pieno schermo (la
                // mappa). Il padding resta comunque deciso qui: le schermate
                // dichiarano l'intenzione, non manipolano la finestra.
                val fullBleed = EdgeToEdgeController.isFullBleed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Il colore va PRIMA del padding, così tinge anche le
                        // strisce sotto le barre di sistema. Serve perché da
                        // API 35 `window.statusBarColor` e `navigationBarColor`
                        // sono no-op: le barre sono trasparenti e sotto si vede
                        // quello che disegna l'app.
                        //
                        // `kidBoxColors` e NON `MaterialTheme.colorScheme`:
                        // quest'ultimo è lo schema Material di default (il tema
                        // passa `lightColorScheme()`/`darkColorScheme()` senza
                        // personalizzarli), il cui `background` è un bianco
                        // tendente al lilla. Il fondo vero dell'app è
                        // #F5F3EE / #1C1C1E e vive in `kidBoxColors`.
                        .background(MaterialTheme.kidBoxColors.background)
                        .then(
                            if (fullBleed) {
                                Modifier
                            } else {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.systemBars.union(WindowInsets.displayCutout),
                                )
                            },
                        ),
                ) {
                    AppNavGraph(
                        navController = navController,
                        startDestination = AppDestination.Login.route,
                        onboardingPreferences = onboardingPreferences,
                    )
                    CrashReportConsentDialog(
                        visible = showConsent,
                        issueCount = consentPrompt?.issueCount ?: 0,
                    )
                    UpdateAvailableDialog(
                        visible = updateVersionCode != null,
                        onUpdate = {
                            appUpdateChecker.openPlayStore()
                            updateVersionCode = null
                        },
                        onDismiss = {
                            updateVersionCode?.let { appUpdateChecker.snooze(it) }
                            updateVersionCode = null
                        },
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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
