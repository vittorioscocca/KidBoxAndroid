package it.vittorioscocca.kidbox.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme { LIGHT, DARK, SYSTEM }

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
