package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentsUiPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    fun getViewMode(): DocumentsSavedViewMode =
        when (prefs.getString(KEY_DOCUMENTS_VIEW_MODE, DocumentsSavedViewMode.GRID.name)) {
            DocumentsSavedViewMode.LIST.name -> DocumentsSavedViewMode.LIST
            else -> DocumentsSavedViewMode.GRID
        }

    fun setViewMode(mode: DocumentsSavedViewMode) {
        prefs.edit().putString(KEY_DOCUMENTS_VIEW_MODE, mode.name).apply()
    }

    fun getSort(): DocumentsSavedSort =
        when (prefs.getString(KEY_DOCUMENTS_SORT, DocumentsSavedSort.NAME.name)) {
            DocumentsSavedSort.TYPE.name -> DocumentsSavedSort.TYPE
            DocumentsSavedSort.DATE.name -> DocumentsSavedSort.DATE
            DocumentsSavedSort.SIZE.name -> DocumentsSavedSort.SIZE
            else -> DocumentsSavedSort.NAME
        }

    fun getSortAscending(): Boolean =
        prefs.getBoolean(KEY_DOCUMENTS_SORT_ASCENDING, true)

    fun setSort(sort: DocumentsSavedSort, ascending: Boolean) {
        prefs.edit()
            .putString(KEY_DOCUMENTS_SORT, sort.name)
            .putBoolean(KEY_DOCUMENTS_SORT_ASCENDING, ascending)
            .apply()
    }

    private companion object {
        private const val KEY_DOCUMENTS_VIEW_MODE = "documents_view_mode"
        private const val KEY_DOCUMENTS_SORT = "documents_sort"
        private const val KEY_DOCUMENTS_SORT_ASCENDING = "documents_sort_ascending"
    }
}

enum class DocumentsSavedViewMode { GRID, LIST }
enum class DocumentsSavedSort { NAME, TYPE, DATE, SIZE }
