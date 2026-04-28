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
     * Returns true if the current device can perform file-based on-device transcription.
     * Requires Android 13+ (API 33) and a working SpeechRecognizer service.
     */
    fun isRecognitionAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    /**
     * Best-effort on-device transcription of an audio file.
     *
     * Strategy:
     * 1. Decode the input audio to WAV (16 kHz, mono, 16-bit PCM) using [AudioPcmDecoder].
     *    WAV files carry their own RIFF header so the recognizer can detect format without
     *    relying on [RecognizerIntent] PCM-extras — this is critical on MIUI/Xiaomi where
     *    the OEM engine ignores those extras and returns 0 candidates for raw PCM.
     * 2. Try [SpeechRecognizer.createOnDeviceSpeechRecognizer] first (API 33+). This bypasses
     *    OEM routing (e.g. Xiaomi's cloud engine) and uses the AOSP on-device model directly.
     *    Fall back to [SpeechRecognizer.createSpeechRecognizer] if the on-device variant is
     *    unavailable.
     * 3. Do NOT send raw-PCM format extras (channel/encoding/sampleRate) for WAV files —
     *    those extras apply only to headerless raw PCM; sending them alongside a WAV file
     *    confuses some engines which then skip the RIFF header and mis-parse the audio.
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

        // Convert input to WAV (RIFF header + 16 kHz mono 16-bit PCM).
        // WAV lets the engine detect format from the RIFF header — no need to send PCM extras.
        // If conversion fails fall back to the original file as a last resort.
        val wav = withContext(Dispatchers.IO) { prepareWavForRecognition(file) }
        val target = wav ?: file
        Log.w(tag, "engine_input: file=${target.name} size=${target.length()} converted=${wav != null}")

        val text = runRecognizer(target, localeTag)

        if (wav != null) runCatching { wav.delete() }
        return text
    }

    /**
     * Decode [input] to a temporary WAV file (16 kHz mono 16-bit PCM with RIFF header).
     * Returns null if the file is already a raw-PCM file or if conversion fails.
     */
    private fun prepareWavForRecognition(input: File): File? {
        val lower = input.name.lowercase()
        // Already raw PCM — keep as-is (caller must send PCM extras in this case)
        if (lower.endsWith(".pcm")) return null
        val cacheDir = File(appContext.cacheDir, "transcription").apply { mkdirs() }
        val outFile = File(cacheDir, "decoded_${System.currentTimeMillis()}.wav")
        val ok = AudioPcmDecoder.decodeToMono16kWav(input, outFile)
        return if (ok && outFile.exists() && outFile.length() > 44L) outFile else null
    }

    private suspend fun runRecognizer(
        file: File,
        localeTag: String,
    ): String? = withTimeoutOrNull(30_000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // Prefer createOnDeviceSpeechRecognizer (API 33+) to bypass OEM routing layers
                // (e.g. Xiaomi routes createSpeechRecognizer to its cloud engine which ignores
                // EXTRA_AUDIO_SOURCE). Fall back to the standard factory if unavailable.
                val recognizer: SpeechRecognizer? = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
                    ) {
                        Log.w(tag, "recognizer: using createOnDeviceSpeechRecognizer")
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    } else {
                        Log.w(tag, "recognizer: using createSpeechRecognizer (fallback)")
                        SpeechRecognizer.createSpeechRecognizer(appContext)
                    }
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

                val lower = file.name.lowercase()
                val isRawPcm = lower.endsWith(".pcm")
                val isWav = lower.endsWith(".wav")

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Allow network fallback — many OEM offline models lack it-IT.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    // API 33+: feed recognizer with a file descriptor instead of live mic.
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)

                    when {
                        isRawPcm -> {
                            // Raw PCM has no header — must tell the engine the format explicitly.
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 2) // AudioFormat.ENCODING_PCM_16BIT
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                            Log.w(tag, "intent: raw PCM extras sent (16kHz mono 16-bit)")
                        }
                        isWav -> {
                            // WAV carries a RIFF header — the engine reads format and duration
                            // directly from it. Do NOT send PCM extras: they would override
                            // the engine's RIFF-header parsing and cause mis-interpretation.
                            Log.w(tag, "intent: WAV format — no PCM extras sent")
                        }
                        else -> {
                            // Unknown format — no extras; let the engine figure it out.
                            Log.w(tag, "intent: unknown format, no PCM extras sent")
                        }
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
            Log.w(tag, "fail: no result (timeout or engine returned nothing) for file=${file.name}")
        } else {
            Log.w(tag, "success: transcription acquired for file=${file.name}: \"$result\"")
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
