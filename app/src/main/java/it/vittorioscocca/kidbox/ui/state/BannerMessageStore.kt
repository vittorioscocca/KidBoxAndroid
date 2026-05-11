package it.vittorioscocca.kidbox.ui.state

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BannerMessageStore @Inject constructor() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun show(text: String) {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}
