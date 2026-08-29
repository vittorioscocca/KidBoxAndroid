package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeViewMode { LIST, GRID }

/**
 * Preferenze della Home, tutte per-device (non sincronizzate su Firestore),
 * come su iOS: lista/griglia, ordine libero delle card in griglia,
 * Dashboard mostrata o no.
 *
 * `@Singleton` non è decorativo: i flow vivono in memoria, e senza scope Hilt
 * costruirebbe un'istanza per ogni ViewModel. Chi scrive (Impostazioni > Tema)
 * e chi legge (la Home) si ritroverebbero due flow diversi, e il toggle della
 * Dashboard non arriverebbe mai a destinazione.
 */
@Singleton
class HomeViewModePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
    private val _viewModeFlow = MutableStateFlow(readViewMode())
    private val _showDashboardFlow = MutableStateFlow(readShowDashboard())

    fun getViewMode(): HomeViewMode = readViewMode()

    fun getViewModeFlow(): StateFlow<HomeViewMode> = _viewModeFlow.asStateFlow()

    fun setViewMode(mode: HomeViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
        _viewModeFlow.value = mode
    }

    /** La Dashboard è opt-in: spenta finché non la accendi da Impostazioni > Tema. */
    fun getShowDashboardFlow(): StateFlow<Boolean> = _showDashboardFlow.asStateFlow()

    fun setShowDashboard(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DASHBOARD, show).apply()
        _showDashboardFlow.value = show
    }

    fun getOrder(): List<String>? =
        prefs.getString(KEY_GRID_ORDER, null)?.split(",")?.filter { it.isNotBlank() }

    fun setOrder(ids: List<String>) {
        prefs.edit().putString(KEY_GRID_ORDER, ids.joinToString(",")).apply()
    }

    private fun readShowDashboard(): Boolean = prefs.getBoolean(KEY_SHOW_DASHBOARD, false)

    private fun readViewMode(): HomeViewMode = when (prefs.getString(KEY_VIEW_MODE, "LIST")) {
        "GRID" -> HomeViewMode.GRID
        else -> HomeViewMode.LIST
    }

    private companion object {
        const val KEY_VIEW_MODE = "home_view_mode"
        const val KEY_GRID_ORDER = "home_grid_order"
        const val KEY_SHOW_DASHBOARD = "home_show_dashboard"
    }
}
