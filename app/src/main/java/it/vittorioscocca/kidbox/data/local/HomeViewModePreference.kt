package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeViewMode { LIST, GRID }

/**
 * Preferenza Home lista/griglia + ordine libero delle card in griglia.
 * Per-device (non sincronizzata su Firestore), come su iOS.
 */
class HomeViewModePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
    private val _viewModeFlow = MutableStateFlow(readViewMode())

    fun getViewMode(): HomeViewMode = readViewMode()

    fun getViewModeFlow(): StateFlow<HomeViewMode> = _viewModeFlow.asStateFlow()

    fun setViewMode(mode: HomeViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
        _viewModeFlow.value = mode
    }

    fun getOrder(): List<String>? =
        prefs.getString(KEY_GRID_ORDER, null)?.split(",")?.filter { it.isNotBlank() }

    fun setOrder(ids: List<String>) {
        prefs.edit().putString(KEY_GRID_ORDER, ids.joinToString(",")).apply()
    }

    private fun readViewMode(): HomeViewMode = when (prefs.getString(KEY_VIEW_MODE, "LIST")) {
        "GRID" -> HomeViewMode.GRID
        else -> HomeViewMode.LIST
    }

    private companion object {
        const val KEY_VIEW_MODE = "home_view_mode"
        const val KEY_GRID_ORDER = "home_grid_order"
    }
}
