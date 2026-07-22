package it.vittorioscocca.kidbox.data.local

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val tag: String) {
    /** Nessun override: l'app segue la lingua di sistema. */
    SYSTEM(""),
    IT("it"),
    EN("en"),
    FR("fr"),
    ES("es"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: SYSTEM
    }
}

/**
 * Per-app language override. AppCompat persists and restores the chosen
 * [androidx.core.os.LocaleListCompat] itself (no SharedPreferences needed here) and
 * applying it recreates activities so the change is live, without a full app restart.
 */
class AppLanguagePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _languageFlow = MutableStateFlow(readLanguage())

    fun getLanguage(): AppLanguage = readLanguage()

    fun getLanguageFlow(): StateFlow<AppLanguage> = _languageFlow.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _languageFlow.value = language
        // Lista vuota = nessun override per-app: AppCompat torna a seguire il sistema.
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            },
        )
    }

    private fun readLanguage(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0]?.language)
    }
}
