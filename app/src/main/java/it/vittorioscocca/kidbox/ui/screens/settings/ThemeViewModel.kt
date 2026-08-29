package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.data.local.HomeViewModePreference
import it.vittorioscocca.kidbox.data.local.ThemePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreference: ThemePreference,
    private val homeViewModePreference: HomeViewModePreference,
) : ViewModel() {
    val theme: StateFlow<AppTheme> = themePreference.getThemeFlow()

    /** Dashboard in Home: spenta di default, chi la vuole la accende da qui. */
    val showDashboard: StateFlow<Boolean> = homeViewModePreference.getShowDashboardFlow()

    fun setTheme(theme: AppTheme) {
        themePreference.setTheme(theme)
    }

    fun setShowDashboard(show: Boolean) {
        homeViewModePreference.setShowDashboard(show)
    }
}
