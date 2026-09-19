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

data class DeviceSessionUi(
    val id: String,
    val platform: String,
    val deviceName: String,
    val osVersion: String,
    val lastSeenAt: Date?,
    val isCurrent: Boolean,
)

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
                val list = snap.documents.map { d ->
                    DeviceSessionUi(
                        id = d.id,
                        platform = d.getString("platform").orEmpty(),
                        deviceName = d.getString("deviceName").orEmpty(),
                        osVersion = d.getString("osVersion").orEmpty(),
                        lastSeenAt = d.getTimestamp("lastSeenAt")?.toDate(),
                        isCurrent = d.id == current,
                    )
                }.sortedWith(
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
                db.collection("users").document(uid)
                    .collection("sessions").document(session.id).delete().await()
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
