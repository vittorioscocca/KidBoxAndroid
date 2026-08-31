package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.AppLanguage
import it.vittorioscocca.kidbox.data.local.AppLanguagePreference
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languagePreference: AppLanguagePreference,
    private val pushNotificationManager: PushNotificationManager,
) : ViewModel() {
    val language: StateFlow<AppLanguage> = languagePreference.getLanguageFlow()

    fun setLanguage(language: AppLanguage) {
        // Prima la scrittura, poi il cambio: `setLanguage` ricrea le activity e
        // porta via con sé questo `viewModelScope`. La chiamata a Firestore però
        // parte subito e l'SDK la completa per conto suo anche se la coroutine
        // che l'attendeva muore.
        // Serve perché è il server a tradurre le push, leggendo la lingua salvata.
        viewModelScope.launch {
            runCatching {
                pushNotificationManager.syncNotificationLanguage(
                    AppLanguage.resolvedTagFor(language),
                )
            }
        }
        languagePreference.setLanguage(language)
    }
}
