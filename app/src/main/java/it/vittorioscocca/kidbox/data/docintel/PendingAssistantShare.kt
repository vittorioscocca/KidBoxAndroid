package it.vittorioscocca.kidbox.data.docintel

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PDF o foto condivisi da un'altra app verso l'assistente, in attesa che la
 * sua chat li legga (gemello di `pendingShareAssistantFilePath` su iOS).
 *
 * Il file è già una copia nella cache dell'app: il permesso sull'URI ricevuto
 * vale solo finché vive `ShareReceiverActivity`, che si chiude subito dopo.
 */
object PendingAssistantShare {
    private val _file = MutableStateFlow<File?>(null)
    val file: StateFlow<File?> = _file.asStateFlow()

    fun set(file: File) {
        _file.value = file
    }

    /** Prende il file una volta sola: due consumatori non lo leggono due volte. */
    fun take(): File? {
        val current = _file.value ?: return null
        _file.value = null
        return current
    }
}
