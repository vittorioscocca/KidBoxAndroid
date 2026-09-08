package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.domain.family.resolveActiveFamilyId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Un collegamento Alexa di un membro della famiglia.
 *
 * `name` può essere null: un membro senza displayName esiste, e la schermata
 * ricade su un'etichetta generica invece di mostrare una riga vuota.
 */
data class AlexaFamilyLinkUi(
    val uid: String,
    val name: String?,
    val linkedAt: Long?,
)

data class AlexaSettingsUiState(
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    /** Questo account ha un collegamento suo. */
    val linked: Boolean = false,
    val linkedAt: Long? = null,
    /** Collegamenti di ALTRI membri: il proprio è già in `linked`. */
    val otherLinks: List<AlexaFamilyLinkUi> = emptyList(),
    /** Codice attivo, già formattato per la lettura ("482 915"). */
    val pairingCode: String? = null,
    val secondsLeft: Int = 0,
    val errorMessage: String? = null,
)

/**
 * Collegamento fra questo account KidBox e la skill Alexa.
 *
 * Perché un codice da dettare e non un login dentro la skill: l'account linking
 * di Alexa è OAuth2 e vuole una pagina di login propria, mentre i nostri account
 * nascono in gran parte da Google e Apple. Qui l'utente è già autenticato
 * nell'app, quindi il codice trasporta quell'identità senza richiedere di nuovo
 * le credenziali. Vedi `internal/alexa/README.md`.
 */
@HiltViewModel
class AlexaSettingsViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familySessionPreferences: FamilySessionPreferences,
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    private val _uiState = MutableStateFlow(AlexaSettingsUiState())
    val uiState: StateFlow<AlexaSettingsUiState> = _uiState.asStateFlow()

    private var expiresAtMs: Long = 0L
    private var tickJob: Job? = null

    init {
        load()
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val familyId = resolveActiveFamilyId(familySessionPreferences, familyDao)
            if (familyId.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = NO_FAMILY) }
                return@launch
            }
            fetchStatus(familyId, silent = false)
        }
    }

    /**
     * @param silent durante il polling un errore di rete non deve riempire la
     * schermata di avvisi mentre l'utente sta parlando all'Echo.
     */
    private suspend fun fetchStatus(familyId: String, silent: Boolean) {
        runCatching {
            val result = functions.getHttpsCallable("getAlexaLinkStatus")
                .call(hashMapOf("familyId" to familyId))
                .await()
            @Suppress("UNCHECKED_CAST")
            result.getData() as? Map<String, Any?>
        }.onSuccess { payload ->
            val data = payload.orEmpty()

            @Suppress("UNCHECKED_CAST")
            val rawLinks = data["familyLinks"] as? List<Map<String, Any?>> ?: emptyList()

            val others = rawLinks
                .filter { it["isMe"] != true }
                .mapNotNull { entry ->
                    val uid = entry["uid"] as? String ?: return@mapNotNull null
                    if (uid.isEmpty()) return@mapNotNull null
                    AlexaFamilyLinkUi(
                        uid = uid,
                        name = entry["name"] as? String,
                        linkedAt = (entry["linkedAt"] as? Number)?.toLong(),
                    )
                }
                .sortedBy { it.linkedAt ?: Long.MAX_VALUE }

            val linked = data["linked"] == true
            _uiState.update {
                it.copy(
                    isLoading = false,
                    linked = linked,
                    linkedAt = (data["linkedAt"] as? Number)?.toLong(),
                    otherLinks = others,
                    // Collegamento fatto: il codice non serve più, e lasciarlo a
                    // schermo farebbe credere che manchi ancora un passo.
                    pairingCode = if (linked) null else it.pairingCode,
                    secondsLeft = if (linked) 0 else it.secondsLeft,
                )
            }
            if (linked) tickJob?.cancel()
        }.onFailure {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = if (silent) it.errorMessage else STATUS_ERROR)
            }
        }
    }

    fun generateCode() {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            val familyId = resolveActiveFamilyId(familySessionPreferences, familyDao)
            if (familyId.isEmpty()) {
                _uiState.update { it.copy(isGenerating = false, errorMessage = NO_FAMILY) }
                return@launch
            }

            runCatching {
                val result = functions.getHttpsCallable("createAlexaPairingCode")
                    .call(hashMapOf("familyId" to familyId))
                    .await()
                @Suppress("UNCHECKED_CAST")
                result.getData() as? Map<String, Any?>
            }.onSuccess { payload ->
                val code = payload.orEmpty()["code"] as? String
                val expires = (payload.orEmpty()["expiresAt"] as? Number)?.toLong()
                if (code == null || expires == null) {
                    _uiState.update { it.copy(isGenerating = false, errorMessage = UNEXPECTED) }
                    return@onSuccess
                }
                expiresAtMs = expires
                _uiState.update {
                    it.copy(isGenerating = false, pairingCode = formatCode(code))
                }
                startTicking(familyId)
            }.onFailure {
                _uiState.update { it.copy(isGenerating = false, errorMessage = CODE_ERROR) }
            }
        }
    }

    /** "482915" → "482 915": a gruppi si detta senza perdere il segno. */
    private fun formatCode(code: String): String =
        if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

    /**
     * Conto alla rovescia più polling: l'app non ha modo di sapere quando
     * l'utente ha finito di parlare all'Echo, quindi finché il codice è vivo si
     * richiede lo stato ogni pochi secondi e la schermata si aggiorna da sola
     * nel momento in cui il collegamento nasce.
     */
    private fun startTicking(familyId: String) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var elapsed = 0
            while (true) {
                val remaining = ((expiresAtMs - System.currentTimeMillis()) / 1000).toInt()
                _uiState.update { it.copy(secondsLeft = remaining.coerceAtLeast(0)) }
                if (remaining <= 0) {
                    _uiState.update { it.copy(pairingCode = null, secondsLeft = 0) }
                    return@launch
                }
                if (elapsed > 0 && elapsed % 5 == 0) {
                    fetchStatus(familyId, silent = true)
                    if (_uiState.value.linked) return@launch
                }
                delay(1_000)
                elapsed++
            }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                functions.getHttpsCallable("unlinkAlexa").call(hashMapOf<String, Any>()).await()
            }.onSuccess {
                tickJob?.cancel()
                _uiState.update {
                    it.copy(isLoading = false, linked = false, linkedAt = null, pairingCode = null, secondsLeft = 0)
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, errorMessage = UNLINK_ERROR) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Chiavi simboliche: la schermata le traduce in stringhe localizzate, così
    // il ViewModel non tiene un Context solo per gli errori.
    companion object {
        const val NO_FAMILY = "no_family"
        const val STATUS_ERROR = "status_error"
        const val CODE_ERROR = "code_error"
        const val UNLINK_ERROR = "unlink_error"
        const val UNEXPECTED = "unexpected"
    }
}
