package it.vittorioscocca.kidbox.data.work

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PDF o foto condivisi da un'altra app verso Lavoro, in attesa che la scheda
 * li metta nel riquadro «Documento pronto da leggere» (gemello di
 * `pendingShareWorkFilePath` su iOS). Il file è già una copia nella cache: il
 * permesso sull'URI ricevuto vale solo finché vive `ShareReceiverActivity`.
 */
object PendingWorkShare {
    private val _file = MutableStateFlow<File?>(null)
    val file: StateFlow<File?> = _file.asStateFlow()

    fun set(file: File) { _file.value = file }

    /** Prende il file una volta sola. */
    fun take(): File? {
        val current = _file.value ?: return null
        _file.value = null
        return current
    }
}
