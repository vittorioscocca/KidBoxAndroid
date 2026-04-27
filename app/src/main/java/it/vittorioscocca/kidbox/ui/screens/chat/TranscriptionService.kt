package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

@Singleton
class TranscriptionService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val tag = "KB_Transcription"

    /**
     * Best-effort on-device transcription of an audio file.
     *
     * Many OEM speech engines (incl. MIUI) silently reject M4A/AAC payloads when
     * fed via [RecognizerIntent.EXTRA_AUDIO_SOURCE], returning [SpeechRecognizer.ERROR_NO_MATCH].
     * To maximise compatibility we decode the input to canonical PCM 16-bit mono 16 kHz
     * WAV first and only then call the recognizer with the matching PCM extras.
     */
    suspend fun transcribeAudioBestEffort(
        file: File,
        localeTag: String = "it-IT",
    ): String? {
        if (!file.exists() || file.length() == 0L) {
            Log.w(tag, "skip: file missing/empty path=${file.absolutePath} size=${file.length()}")
            return null
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.w(tag, "skip: SpeechRecognizer unavailable on this device/service")
            return null
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(tag, "skip: file-based recognition requires API 33+, current=${Build.VERSION.SDK_INT}")
            return null
        }

        Log.w(
            tag,
            "start: file=${file.name} size=${file.length()} locale=$localeTag api=${Build.VERSION.SDK_INT}",
        )

        // 1) Try to convert input to canonical PCM 16-bit mono 16 kHz WAV. If conversion
        //    fails we fall back to the original file (some engines may still cope).
        val wav = withContext(Dispatchers.IO) { prepareWavForRecognition(file) }
        val target = wav ?: file
        Log.w(tag, "engine_input: file=${target.name} size=${target.length()} converted=${wav != null}")

        val text = runRecognizer(target, localeTag)

        if (wav != null) {
            runCatching { wav.delete() }
        }
        return text
    }

    private fun prepareWavForRecognition(input: File): File? {
        val lower = input.name.lowercase()
        if (lower.endsWith(".wav")) return null
        val cacheDir = File(appContext.cacheDir, "transcription").apply { mkdirs() }
        val outFile = File(cacheDir, "decoded_${System.currentTimeMillis()}.wav")
        val ok = AudioPcmDecoder.decodeToMono16kWav(input, outFile)
        return if (ok && outFile.exists() && outFile.length() > 0L) outFile else null
    }

    private suspend fun runRecognizer(
        file: File,
        localeTag: String,
    ): String? = withTimeoutOrNull(20_000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val recognizer = runCatching {
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                }.getOrNull()
                if (recognizer == null) {
                    Log.e(tag, "fail: cannot create SpeechRecognizer")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val pfd = runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }.getOrNull()
                if (pfd == null) {
                    Log.e(tag, "fail: cannot open ParcelFileDescriptor for ${file.absolutePath}")
                    runCatching { recognizer.destroy() }
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                var finished = false
                fun complete(result: String?) {
                    if (finished) return
                    finished = true
                    runCatching { pfd.close() }
                    runCatching { recognizer.destroy() }
                    if (continuation.isActive) continuation.resume(result)
                }

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onError(error: Int) {
                        Log.w(tag, "fail: recognizer error=$error (${speechErrorLabel(error)})")
                        complete(null)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                        Log.w(
                            tag,
                            "results: candidates=${matches?.size ?: 0} first=${matches?.firstOrNull() ?: "<empty>"}",
                        )
                        complete(matches?.firstOrNull())
                    }
                }

                continuation.invokeOnCancellation {
                    runCatching { pfd.close() }
                    runCatching { recognizer.destroy() }
                }

                recognizer.setRecognitionListener(listener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Allow network fallback because many OEM offline models miss it-IT.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    // API 33+: feed recognizer with a file descriptor instead of live mic.
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)

                    // The decoder produces PCM 16-bit mono 16 kHz WAV. Provide matching
                    // metadata so the speech service knows how to read the bytes.
                    val lower = file.name.lowercase()
                    val isPcmLike = lower.endsWith(".wav") || lower.endsWith(".pcm")
                    if (isPcmLike) {
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 2) // PCM 16-bit
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                    }
                }

                runCatching { recognizer.startListening(intent) }
                    .onFailure {
                        Log.e(tag, "fail: startListening threw ${it.javaClass.simpleName}: ${it.message}")
                        complete(null)
                    }
            }
        }
    }.also { result ->
        if (result == null) {
            Log.w(tag, "fail: timeout/unsupported pipeline for file=${file.name}")
        } else {
            Log.w(tag, "success: transcription acquired for file=${file.name}")
        }
    }

    private fun speechErrorLabel(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient_permissions"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network_timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer_busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_timeout"
        else -> "unknown"
    }
}
