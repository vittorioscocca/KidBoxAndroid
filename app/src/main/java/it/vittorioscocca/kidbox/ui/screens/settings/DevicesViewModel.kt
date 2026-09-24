package it.vittorioscocca.kidbox.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.PhotoPreviewCache
import it.vittorioscocca.kidbox.data.remote.auth.DeviceSessionRegistry
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Una riga dell'elenco: **un dispositivo**, non un documento.
 *
 * I documenti in `users/{uid}/sessions` sono per *installazione* (vedi
 * `DeviceSessionRegistry.installId`): reinstallando l'app, o svuotandone i
 * dati, se ne crea uno nuovo e il vecchio resta lì. L'elenco mostrava così lo
 * stesso telefono due o tre volte, con date diverse. Qui le righe dello stesso
 * dispositivo diventano una sola, con la data di connessione più recente.
 */
data class DeviceSessionUi(
    /**
     * Tutti i documenti che appartengono a questo dispositivo, dal più recente.
     * Servono interi: disconnetterlo deve cancellarli tutti, o il duplicato
     * riapparirebbe al caricamento successivo.
     */
    val sessionIds: List<String>,
    val platform: String,
    val deviceName: String,
    val osVersion: String,
    val lastSeenAt: Date?,
    val isCurrent: Boolean,
) {
    val id: String get() = sessionIds.firstOrNull() ?: deviceName
}

/** Un documento `sessions/{id}` così com'è su Firestore, prima di raggruppare. */
private data class RawDeviceSession(
    val id: String,
    val platform: String,
    val deviceName: String,
    val osVersion: String,
    val lastSeenAt: Date?,
)

/**
 * Raggruppa i documenti per dispositivo: stessa piattaforma e stesso nome sono
 * la stessa macchina, e ne resta una riga sola con la connessione più recente.
 * La versione di sistema non entra nella chiave: un aggiornamento di Android
 * non crea un documento nuovo (quello esistente viene riscritto in place),
 * quindi tenerla dentro avrebbe solo rischiato di separare i duplicati veri.
 *
 * Il prezzo è dichiarato: due telefoni dello stesso modello, sullo stesso
 * account, finiscono in una riga sola. Il nome leggibile è il modello (vedi
 * `DeviceSessionRegistry.deviceName`) e non c'è niente di più fine da usare
 * senza permessi, quindi l'alternativa era continuare a mostrare righe doppie.
 */
private fun collapse(raw: List<RawDeviceSession>, currentId: String): List<DeviceSessionUi> =
    raw.groupBy { "${it.platform.lowercase()}|${it.deviceName.trim().lowercase()}" }
        .map { (_, items) ->
            // Dal più recente: la prima riga detta data, versione e id mostrato.
            val sorted = items.sortedByDescending { it.lastSeenAt?.time ?: 0L }
            val newest = sorted.first()
            DeviceSessionUi(
                sessionIds = sorted.map { it.id },
                platform = newest.platform,
                deviceName = newest.deviceName,
                osVersion = newest.osVersion,
                lastSeenAt = newest.lastSeenAt,
                isCurrent = sorted.any { it.id == currentId },
            )
        }

data class DevicesUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val sessions: List<DeviceSessionUi> = emptyList(),
    val message: String? = null,
    /** Alzato quando è questo dispositivo a essere uscito: la UI torna al login. */
    val signedOut: Boolean = false,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val deviceSessionRegistry: DeviceSessionRegistry,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    fun load() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                db.collection("users").document(uid).collection("sessions").get().await()
            }.onSuccess { snap ->
                val current = deviceSessionRegistry.installId
                val raw = snap.documents.map { d ->
                    RawDeviceSession(
                        id = d.id,
                        platform = d.getString("platform").orEmpty(),
                        deviceName = d.getString("deviceName").orEmpty(),
                        osVersion = d.getString("osVersion").orEmpty(),
                        lastSeenAt = d.getTimestamp("lastSeenAt")?.toDate(),
                    )
                }
                val list = collapse(raw, current).sortedWith(
                    // Questo dispositivo in cima, poi i più recenti.
                    compareByDescending<DeviceSessionUi> { it.isCurrent }
                        .thenByDescending { it.lastSeenAt?.time ?: 0L },
                )
                _uiState.value = _uiState.value.copy(isLoading = false, sessions = list, message = null)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, message = err.message)
            }
        }
    }

    fun signOutDevice(session: DeviceSessionUi) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isWorking = true)
        viewModelScope.launch {
            runCatching {
                // Tutti i documenti del dispositivo, non solo quello mostrato: se
                // ne restasse indietro uno, la riga tornerebbe al prossimo
                // caricamento e quell'installazione resterebbe collegata.
                val sessions = db.collection("users").document(uid).collection("sessions")
                session.sessionIds.forEach { sessions.document(it).delete().await() }
            }.onSuccess {
                if (session.isCurrent) {
                    // Non si aspetta il proprio listener: il logout è già
                    // deciso, passare dal giro remoto lo renderebbe solo più
                    // lento e dipendente dalla rete.
                    localSignOut()
                    _uiState.value = _uiState.value.copy(isWorking = false, signedOut = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        sessions = _uiState.value.sessions.filterNot { it.id == session.id },
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isWorking = false, message = err.message)
            }
        }
    }

    fun signOutAllDevices() {
        _uiState.value = _uiState.value.copy(isWorking = true)
        viewModelScope.launch {
            runCatching {
                functions.getHttpsCallable("signOutAllDevices").call().await()
            }.onSuccess {
                localSignOut()
                _uiState.value = _uiState.value.copy(isWorking = false, signedOut = true)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isWorking = false, message = err.message)
            }
        }
    }

    private suspend fun localSignOut() {
        runCatching { deviceSessionRegistry.stopAndRemove() }
        runCatching {
            auth.signOut()
            PhotoPreviewCache.clearAll(appContext)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
