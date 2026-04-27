package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.File
import kotlin.math.max

data class RecordedAudio(
    val file: File,
    val durationSeconds: Int,
    val mimeType: String = "audio/x-m4a",
)

class AudioRecorderManager(
    private val appContext: Context,
) {
    private val tag = "KB_Transcription"
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L
    private var paused = false

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start(): Boolean {
        if (recorder != null) return false
        val parent = File(appContext.cacheDir, "chat-audio").apply { mkdirs() }
        val file = File(parent, "voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = MediaRecorder()
        return runCatching {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            paused = false
            Log.w(tag, "recorder_start_ok path=${file.absolutePath}")
            true
        }.getOrElse {
            Log.e(tag, "recorder_start_fail ${it.javaClass.simpleName}: ${it.message}", it)
            mediaRecorder.reset()
            mediaRecorder.release()
            false
        }
    }

    fun currentAmplitude01(): Float {
        if (paused) return 0f
        val value = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (value / 32767f).coerceIn(0f, 1f)
    }

    fun pause() {
        val r = recorder ?: return
        if (paused) return
        runCatching { r.pause() }
        paused = true
    }

    fun resume() {
        val r = recorder ?: return
        if (!paused) return
        runCatching { r.resume() }
        paused = false
    }

    fun isPaused(): Boolean = paused

    fun stop(save: Boolean): RecordedAudio? {
        val r = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        paused = false
        val durationSec = max(((System.currentTimeMillis() - startedAtMs) / 1000L).toInt(), 1)
        runCatching { r.stop() }
            .onFailure { Log.e(tag, "recorder_stop_fail ${it.javaClass.simpleName}: ${it.message}", it) }
        r.reset()
        r.release()
        if (!save) {
            file?.delete()
            Log.w(tag, "recorder_stop_discarded")
            return null
        }
        if (file == null || !file.exists()) {
            Log.w(tag, "recorder_stop_no_file")
            return null
        }
        Log.w(tag, "recorder_stop_ok path=${file.absolutePath} size=${file.length()} durationSec=$durationSec")
        return RecordedAudio(file = file, durationSeconds = durationSec)
    }
}

