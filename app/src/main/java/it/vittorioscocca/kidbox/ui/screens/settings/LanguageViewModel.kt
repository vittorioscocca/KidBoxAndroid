package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.AppLanguage
import it.vittorioscocca.kidbox.data.local.AppLanguagePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languagePreference: AppLanguagePreference,
) : ViewModel() {
    val language: StateFlow<AppLanguage> = languagePreference.getLanguageFlow()

    fun setLanguage(language: AppLanguage) {
        languagePreference.setLanguage(language)
    }
}
