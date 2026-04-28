package it.vittorioscocca.kidbox.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Lightweight video compressor using Android's built-in MediaCodec pipeline.
 *
 * Strategy
 * ─────────
 * • Videos under [THRESHOLD_BYTES] are uploaded as-is.
 * • Larger videos are re-encoded to H.264 with a capped resolution ([MAX_SIDE])
 *   and bitrate ([VIDEO_BITRATE_BPS]).  The decoder output Surface feeds
 *   directly into the encoder input Surface — raw YUV frames never touch the
 *   JVM heap, which keeps memory pressure low on large files.
 * • The audio track is copied byte-for-byte (no decode/re-encode).
 * • Any failure returns the original bytes so the send always succeeds.
 */
internal object VideoCompressor {

    private const val TAG = "VideoCompressor"

    /** Videos at or below this size skip compression entirely. */
    const val THRESHOLD_BYTES = 8L * 1024 * 1024       // 8 MB

    /** Maximum width or height of the re-encoded output (maintains aspect ratio). */
    private const val MAX_SIDE = 1280

    /** H.264 target video bitrate (bits/sec). */
    private const val VIDEO_BITRATE_BPS = 1_500_000     // 1.5 Mbps

    /** Key-frame interval for the encoder (seconds). */
    private const val IFRAME_INTERVAL = 2

    /** dequeueBuffer polling timeout (µs). */
    private const val TIMEOUT_US = 10_000L

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compresses [bytes] if they exceed [THRESHOLD_BYTES].
     * Returns the compressed bytes on success, or the original bytes if
     * compression is not needed or fails for any reason.
     */
    suspend fun compressIfNeeded(bytes: ByteArray, context: Context): ByteArray =
        withContext(Dispatchers.IO) {
            if (bytes.size.toLong() <= THRESHOLD_BYTES) return@withContext bytes

            val ts = System.currentTimeMillis()
            val inputFile  = File(context.cacheDir, "kb_vc_in_$ts.mp4")
            val outputFile = File(context.cacheDir, "kb_vc_out_$ts.mp4")
            try {
                inputFile.writeBytes(bytes)
                val compressed = runCatching {
                    transcode(inputFile.absolutePath, outputFile.absolutePath)
                }.onFailure {
                    Log.w(TAG, "transcode failed: ${it.message}")
                }.getOrDefault(false)

                if (compressed && outputFile.length() in 1 until bytes.size.toLong()) {
                    Log.d(TAG, "Video ${bytes.size / 1024}KB → ${outputFile.length() / 1024}KB")
                    outputFile.readBytes()
                } else {
                    bytes   // fallback: upload original
                }
            } finally {
                inputFile.delete()
                outputFile.delete()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Transcoding pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transcodes the video at [inputPath] to [outputPath].
     * Returns true if the output was written successfully.
     */
    private fun transcode(inputPath: String, outputPath: String): Boolean {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        // ── Locate tracks ────────────────────────────────────────────────────
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var videoFmt: MediaFormat? = null
        var audioFmt: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            when (fmt.getString(MediaFormat.KEY_MIME)?.substringBefore('/')) {
                "video" -> if (videoTrackIdx < 0) { videoTrackIdx = i; videoFmt = fmt }
                "audio" -> if (audioTrackIdx < 0) { audioTrackIdx = i; audioFmt = fmt }
            }
        }
        if (videoTrackIdx < 0 || videoFmt == null) { extractor.release(); return false }

        val inputMime = videoFmt.getString(MediaFormat.KEY_MIME)
            ?: run { extractor.release(); return false }

        // ── Compute output dimensions ────────────────────────────────────────
        val inW = videoFmt.safeInt(MediaFormat.KEY_WIDTH)
            ?: run { extractor.release(); return false }
        val inH = videoFmt.safeInt(MediaFormat.KEY_HEIGHT)
            ?: run { extractor.release(); return false }
        val (outW, outH) = scaleDimensions(inW, inH)

        // Skip re-encoding if the video is already within target parameters
        val inBitrate = videoFmt.safeInt(MediaFormat.KEY_BIT_RATE) ?: Int.MAX_VALUE
        if (outW == inW && outH == inH && inBitrate <= VIDEO_BITRATE_BPS) {
            extractor.release()
            return false
        }

        // Preserve the input container's rotation hint (if any)
        val rotation = videoFmt.safeInt("rotation-degrees")
            ?: videoFmt.safeInt(MediaFormat.KEY_ROTATION)
            ?: 0

        // ── Set up encoder ───────────────────────────────────────────────────
        val encFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE_BPS)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderSurface: Surface = encoder.createInputSurface()
        encoder.start()

        // ── Set up decoder (outputs directly to encoder's Surface) ───────────
        val decoder = MediaCodec.createDecoderByType(inputMime)
        decoder.configure(videoFmt, encoderSurface, null, 0)
        decoder.start()

        // ── Set up muxer ─────────────────────────────────────────────────────
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if (rotation != 0) muxer.setOrientationHint(rotation)

        var muxVideoTrack = -1
        var muxAudioTrack = audioFmt?.let { muxer.addTrack(it) } ?: -1
        var muxerStarted = false

        extractor.selectTrack(videoTrackIdx)

        val bufInfo = MediaCodec.BufferInfo()
        var decoderInputDone = false
        var decoderOutputDone = false
        var encoderDone = false

        try {
            // ── Main transcode loop ─────────────────────────────────────────
            while (!encoderDone) {

                // Feed extractor → decoder input
                if (!decoderInputDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderInputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, n,
                                extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                // Drain decoder → release to Surface (encoder picks up automatically)
                if (!decoderOutputDone) {
                    val outIdx = decoder.dequeueOutputBuffer(bufInfo, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val eos = (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outIdx, /* render = */ true)
                        if (eos) {
                            encoder.signalEndOfInputStream()
                            decoderOutputDone = true
                        }
                    }
                }

                // Drain encoder → muxer
                val encIdx = encoder.dequeueOutputBuffer(bufInfo, TIMEOUT_US)
                when {
                    encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxVideoTrack = muxer.addTrack(encoder.outputFormat)
                        if (!muxerStarted && (muxAudioTrack >= 0 || audioTrackIdx < 0)) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    encIdx >= 0 -> {
                        val isConfig = (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (muxerStarted && bufInfo.size > 0 && !isConfig) {
                            val outBuf = encoder.getOutputBuffer(encIdx)!!
                            outBuf.position(bufInfo.offset)
                            outBuf.limit(bufInfo.offset + bufInfo.size)
                            muxer.writeSampleData(muxVideoTrack, outBuf, bufInfo)
                        }
                        encoder.releaseOutputBuffer(encIdx, false)
                        if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            // ── Audio passthrough (copy compressed samples without re-encoding) ──
            if (audioTrackIdx >= 0 && muxAudioTrack >= 0 && muxerStarted) {
                val audioExtractor = MediaExtractor()
                audioExtractor.setDataSource(inputPath)
                audioExtractor.selectTrack(audioTrackIdx)
                val audioBuf = ByteBuffer.allocate(512 * 1024)
                val abInfo = MediaCodec.BufferInfo()
                while (true) {
                    audioBuf.clear()
                    val n = audioExtractor.readSampleData(audioBuf, 0)
                    if (n < 0) break
                    abInfo.set(0, n, audioExtractor.sampleTime, audioExtractor.sampleFlags)
                    muxer.writeSampleData(muxAudioTrack, audioBuf, abInfo)
                    audioExtractor.advance()
                }
                audioExtractor.release()
            }

            return muxerStarted
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { encoderSurface.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
            extractor.release()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scales [w]×[h] down so neither side exceeds [MAX_SIDE], maintaining
     * aspect ratio and ensuring both output dimensions are even (required by H.264).
     */
    private fun scaleDimensions(w: Int, h: Int): Pair<Int, Int> {
        if (w <= MAX_SIDE && h <= MAX_SIDE) return w to h
        return if (w >= h) {
            val nw = MAX_SIDE
            val nh = ((h.toFloat() / w.toFloat() * MAX_SIDE).toInt() / 2) * 2
            nw to nh.coerceAtLeast(2)
        } else {
            val nh = MAX_SIDE
            val nw = ((w.toFloat() / h.toFloat() * MAX_SIDE).toInt() / 2) * 2
            nw.coerceAtLeast(2) to nh
        }
    }

    private fun MediaFormat.safeInt(key: String): Int? = runCatching { getInteger(key) }.getOrNull()
}
