package it.vittorioscocca.kidbox.feature.passwords.autofill

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.ui.screens.settings.AutoFillSettingsScreen
import it.vittorioscocca.kidbox.ui.theme.KidBoxTheme

/**
 * Avviata dalle impostazioni di sistema (Credential provider / Autofill) tramite [R.xml.provider] e [R.xml.autofill_service].
 */
@AndroidEntryPoint
class AutoFillProviderSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KidBoxTheme(darkTheme = isSystemInDarkTheme()) {
                AutoFillSettingsScreen(onBack = { finish() })
            }
        }
    }
}
