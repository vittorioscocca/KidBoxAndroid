package it.vittorioscocca.kidbox.ui.screens.chat

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.media.MediaRecorder
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
            KBLog.ui.warning("recorder_start_ok path=${file.absolutePath}", tag)
            true
        }.getOrElse {
            KBLog.ui.error("recorder_start_fail ${it.javaClass.simpleName}: ${it.message}", tag, it)
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
            .onFailure { KBLog.ui.error("recorder_stop_fail ${it.javaClass.simpleName}: ${it.message}", tag, it) }
        r.reset()
        r.release()
        if (!save) {
            file?.delete()
            KBLog.ui.warning("recorder_stop_discarded", tag)
            return null
        }
        if (file == null || !file.exists()) {
            KBLog.ui.warning("recorder_stop_no_file", tag)
            return null
        }
        KBLog.ui.warning("recorder_stop_ok path=${file.absolutePath} size=${file.length()} durationSec=$durationSec", tag)
        return RecordedAudio(file = file, durationSeconds = durationSec)
    }
}

