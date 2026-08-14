package it.vittorioscocca.kidbox.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme { LIGHT, DARK, SYSTEM }

/**
 * Converte la preferenza in-app nella costante che [AppCompatDelegate] si aspetta.
 *
 * Serve a tenere sincronizzati i componenti nativi (dialog di sistema come
 * `DatePickerDialog`, notifiche, ecc.) con la scelta fatta dentro l'app: [KidBoxTheme][it.vittorioscocca.kidbox.ui.theme.KidBoxTheme]
 * segue già questa preferenza per l'albero Compose, ma senza questa chiamata i
 * componenti nativi seguono solo il tema di sistema, indipendentemente da cosa
 * l'utente ha scelto in Impostazioni.
 */
fun AppTheme.toNightMode(): Int = when (this) {
    AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}

class ThemePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
    private val _themeFlow = MutableStateFlow(readTheme())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "app_theme") {
            _themeFlow.value = readTheme()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun getTheme(): AppTheme = readTheme()

    fun getThemeFlow(): StateFlow<AppTheme> = _themeFlow.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
        _themeFlow.value = theme
    }

    private fun readTheme(): AppTheme = when (prefs.getString("app_theme", "SYSTEM")) {
        "LIGHT" -> AppTheme.LIGHT
        "DARK" -> AppTheme.DARK
        else -> AppTheme.SYSTEM
    }
}
