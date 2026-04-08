package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.data.local.ThemePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreference: ThemePreference,
) : ViewModel() {
    val theme: StateFlow<AppTheme> = themePreference.getThemeFlow()

    fun setTheme(theme: AppTheme) {
        themePreference.setTheme(theme)
    }
}
