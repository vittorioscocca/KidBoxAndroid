package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ChatInputBarUiState(
    val isRecording: Boolean = false,
    val isLocked: Boolean = false,
    val isCancelling: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val waveformSamples: List<Float> = emptyList(),
)

class ChatInputBarViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatInputBarUiState())
    val uiState: StateFlow<ChatInputBarUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var startedAtMs: Long = 0L

    fun onRecordingStarted() {
        startedAtMs = System.currentTimeMillis()
        _uiState.value = ChatInputBarUiState(isRecording = true)
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(120L)
                val elapsed = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt()
                _uiState.value = _uiState.value.copy(elapsedSeconds = elapsed)
            }
        }
    }

    fun onAmplitudeSample(sample01: Float) {
        val s = sample01.coerceIn(0f, 1f)
        val next = (_uiState.value.waveformSamples + s).takeLast(42)
        _uiState.value = _uiState.value.copy(waveformSamples = next)
    }

    fun onLockRecording() {
        _uiState.value = _uiState.value.copy(isLocked = true, isCancelling = false, isPaused = false)
    }

    fun onPauseRecording() {
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun onResumeRecording() {
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun onCancelIntent() {
        _uiState.value = _uiState.value.copy(isCancelling = true)
    }

    fun onRecordingStopped() {
        tickerJob?.cancel()
        _uiState.value = ChatInputBarUiState()
    }

    fun timeLabel(): String {
        val sec = _uiState.value.elapsedSeconds
        val mm = (sec / 60).toString().padStart(2, '0')
        val ss = (sec % 60).toString().padStart(2, '0')
        return "$mm:$ss"
    }

    fun waveformBars(): List<Int> =
        _uiState.value.waveformSamples.map { (4 + it * 22).roundToInt() }
}

