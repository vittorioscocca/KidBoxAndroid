package it.vittorioscocca.kidbox.ui.screens.chat

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes a compressed audio file (M4A/AAC, MP3, OGG, ...) into a 16 kHz mono 16-bit
 * PCM WAV file suitable for Android's [android.speech.SpeechRecognizer]. Most OEM
 * speech engines refuse direct M4A/AAC input; converting to canonical PCM/WAV before
 * recognition dramatically increases compatibility.
 */
internal object AudioPcmDecoder {
    private const val TAG = "KB_Transcription"
    private const val TIMEOUT_US = 10_000L

    /**
     * Decode [input] to mono 16 kHz PCM and write raw 16-bit little-endian samples
     * at [output] (no RIFF/WAV header). Returns true on success.
     *
     * SpeechRecognizer's EXTRA_AUDIO_SOURCE expects raw PCM that matches the PCM
     * encoding/channel/sample-rate extras: any container header (e.g. WAV's 44-byte
     * RIFF prefix) is interpreted as PCM samples and triggers early NO_MATCH on
     * several OEM engines (MIUI/Xiaomi in particular).
     */
    fun decodeToMono16kPcm(input: File, output: File): Boolean = decode(input, output, writeWavHeader = false)

    /**
     * Decode [input] to mono 16 kHz PCM and write a minimal WAV container at [outputWav].
     * Returns true on success.
     */
    fun decodeToMono16kWav(input: File, outputWav: File): Boolean = decode(input, outputWav, writeWavHeader = true)

    private fun decode(input: File, output: File, writeWavHeader: Boolean): Boolean {
        if (!input.exists() || input.length() == 0L) {
            Log.w(TAG, "decoder: skip missing/empty input ${input.absolutePath}")
            return false
        }

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            if (trackIndex == null) {
                Log.w(TAG, "decoder: no audio track in ${input.name}")
                return false
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            // KEY_SAMPLE_RATE / KEY_CHANNEL_COUNT may be absent in some iOS-produced M4A files
            // (the codec info lives inside the CSD box, not the track header). getInteger() throws
            // NullPointerException when the key is missing, so we guard with containsKey().
            val sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val sourceChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) else 1
            Log.w(TAG, "decoder: input mime=$mime sr=$sourceSampleRate ch=$sourceChannels")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                            ?: error("null input buffer")
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                        ?: error("null output buffer")
                    if (info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk, 0, info.size)
                        outBuf.clear()
                        pcm.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            val rawPcm = pcm.toByteArray()
            if (rawPcm.isEmpty()) {
                Log.w(TAG, "decoder: empty PCM after decode")
                return false
            }
            Log.w(TAG, "decoder: raw pcm bytes=${rawPcm.size}")

            val mono16k = downmixAndResample(rawPcm, sourceSampleRate, sourceChannels, 16_000)
            if (writeWavHeader) {
                writeWav(
                    target = output,
                    pcm = mono16k,
                    sampleRate = 16_000,
                    channels = 1,
                    bitsPerSample = 16,
                )
                Log.w(TAG, "decoder: wrote wav=${output.absolutePath} size=${output.length()}")
            } else {
                FileOutputStream(output).use { fos -> fos.write(mono16k) }
                Log.w(TAG, "decoder: wrote pcm=${output.absolutePath} size=${output.length()}")
            }
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "decoder: failed ${t.javaClass.simpleName}: ${t.message}", t)
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    /**
     * Downmix interleaved 16-bit little-endian PCM to mono and linearly resample to
     * [outRate] Hz. Works fine for speech-grade audio quality.
     */
    private fun downmixAndResample(
        pcm: ByteArray,
        inRate: Int,
        channels: Int,
        outRate: Int,
    ): ByteArray {
        val src = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frameCountIn = src.remaining() / channels
        if (frameCountIn <= 0) return ByteArray(0)

        val mono = ShortArray(frameCountIn)
        for (i in 0 until frameCountIn) {
            var sum = 0
            for (c in 0 until channels) {
                sum += src.get(i * channels + c).toInt()
            }
            mono[i] = (sum / channels)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        if (inRate == outRate) {
            val bb = ByteBuffer.allocate(mono.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            mono.forEach { bb.putShort(it) }
            return bb.array()
        }

        val ratio = inRate.toDouble() / outRate.toDouble()
        val frameCountOut = (frameCountIn / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(frameCountOut)
        for (j in 0 until frameCountOut) {
            val srcIdx = j * ratio
            val i0 = srcIdx.toInt()
            val i1 = (i0 + 1).coerceAtMost(frameCountIn - 1)
            val frac = srcIdx - i0
            val v = mono[i0] + (mono[i1] - mono[i0]) * frac
            out[j] = v.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        val bb = ByteBuffer.allocate(out.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.forEach { bb.putShort(it) }
        return bb.array()
    }

    /** Write a minimal RIFF/WAV header followed by [pcm] LE 16-bit samples. */
    private fun writeWav(
        target: File,
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        FileOutputStream(target).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + pcm.size)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcm.size)
            fos.write(header.array())
            fos.write(pcm)
        }
    }
}
