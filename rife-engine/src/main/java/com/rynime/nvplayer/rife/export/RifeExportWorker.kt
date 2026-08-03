package com.rynime.nvplayer.rife.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rynime.nvplayer.rife.RifeConfig
import com.rynime.nvplayer.rife.RifeInterpolator
import com.rynime.nvplayer.rife.RifeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

private const val TAG = "RifeExportWorker"
private const val CODEC_TIMEOUT_US = 10_000L

/**
 * Batch/export interpolation ("Mode A" from the phased plan). Runs entirely
 * on android.media (MediaExtractor/MediaCodec/MediaMuxer) - the only RIFE-
 * specific dependency is [RifeInterpolator]. This is intentionally decoupled
 * from mpv/MPVPlayerEngine entirely: it operates on the source file directly,
 * independent of whatever is or isn't currently playing.
 *
 * Per AGENTS.md: heavy work stays off the main thread (withContext(Dispatchers.IO)
 * / Dispatchers.Default via RifeInterpolator itself), no `!!`, everything
 * that can fail is wrapped and reported as a failed Result rather than left
 * to crash - a long export job crashing the whole app on a malformed frame
 * would be a much worse experience than a caught, reported failure.
 */
class RifeExportWorker(appContext: android.content.Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ExportNotifications.buildForegroundInfo(applicationContext, progressText = "Starting…")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputUriString = inputData.getString(KEY_INPUT_PATH)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing input path"))
        val outputPath = inputData.getString(KEY_OUTPUT_PATH)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing output path"))
        val scaleFactor = inputData.getInt(KEY_SCALE_FACTOR, 2)

        try {
            runExport(android.net.Uri.parse(inputUriString), outputPath, scaleFactor)
            Result.success(workDataOf(KEY_OUTPUT_PATH to outputPath))
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for $inputUriString", e)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown export failure")))
        }
    }

    private suspend fun runExport(inputUri: android.net.Uri, outputPath: String, scaleFactor: Int) {
        com.rynime.nvplayer.rife.NativeTrace.mark(applicationContext, "runExport: start, uri=$inputUri, scale=$scaleFactor")
        val extractor = MediaExtractor()
        extractor.setDataSource(applicationContext, inputUri, null)

        val videoTrackIndex = findTrackIndex(extractor, "video/")
            ?: throw IllegalStateException("No video track found in $inputUri")
        val audioTrackIndex = findTrackIndex(extractor, "audio/")
        val audioFormat = audioTrackIndex?.let { extractor.getTrackFormat(it) }

        val videoFormat = extractor.getTrackFormat(videoTrackIndex)
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val sourceFrameRate = runCatching { videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(30)
        val durationUs = runCatching { videoFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
        val estimatedFrameCount = if (sourceFrameRate > 0 && durationUs > 0) {
            ((durationUs / 1_000_000.0) * sourceFrameRate).toInt()
        } else 0

        val scale = RifeScale.entries.first { it.factor == scaleFactor }
        val config = RifeConfig(scale = scale)
        val interpolator = RifeInterpolator.create(applicationContext, config, width, height)
            ?: throw IllegalStateException(
                "RIFE engine unavailable (${config.model.displayName}) - check RifeCapabilityProbe / model assets"
            )
        com.rynime.nvplayer.rife.NativeTrace.mark(applicationContext, "runExport: RifeInterpolator.create succeeded, entering decode/encode setup")

        val mime = requireNotNull(videoFormat.getString(MediaFormat.KEY_MIME)) { "Video track missing MIME type" }
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(videoFormat, null, null, 0)
        decoder.start()

        val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, estimateBitrate(width, height))
            setInteger(MediaFormat.KEY_FRAME_RATE, sourceFrameRate * scaleFactor)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // MediaMuxer requires every addTrack() call to happen before the single
        // start() call. Audio's format is known immediately from the source, so
        // add it now - the video track gets added inside drainEncoder() once the
        // encoder reports its output format, and THAT is what triggers start().
        val audioMuxerTrack = audioFormat?.let { muxer.addTrack(it) }

        try {
            extractor.selectTrack(videoTrackIndex)
            pumpVideoThroughRife(
                extractor = extractor,
                decoder = decoder,
                encoder = encoder,
                muxer = muxer,
                interpolator = interpolator,
                scale = scale,
                sourceFrameRate = sourceFrameRate,
                estimatedFrameCount = estimatedFrameCount,
            )
            extractor.unselectTrack(videoTrackIndex)

            if (audioTrackIndex != null && audioMuxerTrack != null) {
                copyAudioTrackUnchanged(inputUri, audioTrackIndex, muxer, audioMuxerTrack)
            }
        } finally {
            interpolator.release()
            runCatching { decoder.stop() }
            decoder.release()
            runCatching { encoder.stop() }
            encoder.release()
            runCatching { muxer.stop() }
            muxer.release()
            extractor.release()
        }
    }

    /**
     * Core loop: decode -> pair consecutive frames -> RIFE interpolate the
     * gaps -> feed [interp..., original] to the encoder with evenly-spaced
     * presentation timestamps -> drain encoder output into the muxer.
     *
     * NOTE ON PIXEL FORMAT: reads via decoder.getOutputImage() (YUV_420_888
     * planes with their own real rowStride/pixelStride per plane) rather
     * than the raw output ByteBuffer + KEY_STRIDE/KEY_SLICE_HEIGHT guessing
     * this used to do - COLOR_FormatYUV420Flexible's actual physical layout
     * genuinely varies by vendor (semi-planar vs fully-planar, interleaved
     * vs separate U/V), which the raw-buffer approach got wrong on at least
     * one real device (green/noise corruption at the bottom of exported
     * frames - confirmed and fixed, not hypothetical). See imageToNv12().
     */
    private suspend fun pumpVideoThroughRife(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        interpolator: RifeInterpolator,
        scale: RifeScale,
        sourceFrameRate: Int,
        estimatedFrameCount: Int,
    ) {
        val bufferInfo = MediaCodec.BufferInfo() // decoder dequeue only
        val encoderBufferInfo = MediaCodec.BufferInfo() // encoder drain only - MUST be separate,
        // see the bug note in drainEncoder()'s call sites below
        var inputDone = false
        var decodeDone = false
        var previousFrame: ByteBuffer? = null
        var previousPtsUs = 0L
        var framesEncoded = 0
        val muxerState = MuxerState()
        val startTimeMs = System.currentTimeMillis()

        while (!decodeDone) {
            // --- feed compressed input into the decoder ---
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = decoder.getInputBuffer(inIndex)
                    val sampleSize = if (inBuffer != null) extractor.readSampleData(inBuffer, 0) else -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // --- drain decoded frames, pair them, interpolate, feed encoder ---
            val outIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outIndex >= 0) {
                val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                if (bufferInfo.size > 0) {
                    val image = decoder.getOutputImage(outIndex)
                    val currentFrame = image?.let { imageToNv12(it) }
                    image?.close()

                    if (currentFrame != null) {
                        val currentPtsUs = bufferInfo.presentationTimeUs
                        val prev = previousFrame
                        if (prev != null) {
                            // Real midpoint between the two actual frame timestamps, not an
                            // assumed-constant-framerate offset - phone-captured video is
                            // frequently variable frame rate, and using a wrong assumed gap
                            // here was producing non-monotonic PTS values fed to the encoder,
                            // which corrupted the whole container's sample table (this is what
                            // caused the audio track's duration to read as ~54 hours and the
                            // claimed vs. actually-decodable frame count mismatch).
                            for (t in scale.timesteps()) {
                                val interpolated = interpolator.interpolate(prev, currentFrame, t)
                                val ptsUs = previousPtsUs + ((currentPtsUs - previousPtsUs) * t).toLong()
                                feedEncoder(encoder, interpolated, ptsUs, endOfStream = false)
                                drainEncoder(encoder, muxer, encoderBufferInfo, muxerState)
                                framesEncoded++
                            }
                        }
                        feedEncoder(encoder, currentFrame, currentPtsUs, endOfStream = false)
                        drainEncoder(encoder, muxer, encoderBufferInfo, muxerState)
                        framesEncoded++
                        previousFrame = currentFrame
                        previousPtsUs = currentPtsUs

                        if (estimatedFrameCount > 0) {
                            val totalTarget = estimatedFrameCount * scale.factor
                            val elapsedMs = System.currentTimeMillis() - startTimeMs
                            val etaSeconds = if (framesEncoded > 0) {
                                val msPerFrame = elapsedMs.toDouble() / framesEncoded
                                ((totalTarget - framesEncoded) * msPerFrame / 1000.0).toInt().coerceAtLeast(0)
                            } else null
                            setProgress(workDataOf(
                                "framesDone" to framesEncoded,
                                "framesTotal" to totalTarget,
                                "etaSeconds" to (etaSeconds ?: -1),
                            ))
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIndex, false)
                if (isEos) {
                    feedEncoder(encoder, null, 0, endOfStream = true)
                    drainEncoder(encoder, muxer, encoderBufferInfo, muxerState)
                    decodeDone = true
                }
            }
        }
        check(muxerState.started) { "Encoder never produced a usable output format" }
    }

    private class MuxerState {
        var track = -1
        var started = false
    }

    private fun feedEncoder(encoder: MediaCodec, frame: ByteBuffer?, ptsUs: Long, endOfStream: Boolean) {
        val inIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inIndex < 0) return
        val inBuffer = encoder.getInputBuffer(inIndex) ?: return
        if (endOfStream) {
            encoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return
        }
        val nonNullFrame = requireNotNull(frame) { "feedEncoder called without a frame and without endOfStream" }
        inBuffer.clear()
        inBuffer.put(nonNullFrame)
        encoder.queueInputBuffer(inIndex, 0, nonNullFrame.capacity(), ptsUs, 0)
    }

    private fun drainEncoder(
        encoder: MediaCodec, muxer: MediaMuxer, bufferInfo: MediaCodec.BufferInfo, state: MuxerState,
    ) {
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    state.track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    state.started = true
                }
                outIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outIndex)
                    if (encodedData != null && bufferInfo.size > 0 && state.track >= 0) {
                        muxer.writeSampleData(state.track, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
                else -> return
            }
        }
    }

    private fun copyAudioTrackUnchanged(
        inputUri: android.net.Uri, audioTrackIndex: Int, muxer: MediaMuxer, muxerAudioTrack: Int,
    ) {
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(applicationContext, inputUri, null)
        audioExtractor.selectTrack(audioTrackIndex)
        // muxerAudioTrack was registered via muxer.addTrack() in runExport,
        // before muxer.start() - fixed from an earlier version of this file
        // that called addTrack() here too, after start() had already been
        // triggered by the video track (MediaMuxer throws in that ordering).
        val buffer = ByteBuffer.allocate(1 shl 20)
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = audioExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            bufferInfo.set(0, sampleSize, audioExtractor.sampleTime, 0)
            muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
            audioExtractor.advance()
        }
        audioExtractor.release()
    }

    /**
     * Converts a decoder's output Image (YUV_420_888, 3 planes) to tightly
     * packed NV12 (Y plane, then interleaved U,V). Reads each plane using
     * its OWN reported rowStride/pixelStride rather than assuming NV12
     * semi-planar layout - COLOR_FormatYUV420Flexible's actual physical
     * layout (semi-planar vs fully-planar, interleaved vs separate U/V,
     * padding) genuinely varies by vendor, which KEY_STRIDE/KEY_SLICE_HEIGHT
     * on the raw ByteBuffer never reliably captured. This was the real
     * cause of the green/noise ("semut") corruption at the bottom of
     * exported frames on this device - not a metadata/PTS issue (already
     * fixed separately), a genuine wrong-bytes-read pixel bug.
     */
    private var frameIndexForTrace = 0

    private fun imageToNv12(image: android.media.Image): ByteBuffer {
        val width = image.width
        val height = image.height
        val out = ByteBuffer.allocateDirect(width * height * 3 / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // --- Y plane: one byte per pixel, row by row using its real rowStride ---
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yRow = ByteArray(yRowStride)
        var yShortReads = 0
        for (row in 0 until height) {
            yBuf.position(row * yRowStride)
            val avail = yBuf.remaining()
            if (avail < yRowStride) yShortReads++
            val readLen = minOf(yRowStride, avail).coerceAtLeast(0)
            if (readLen > 0) yBuf.get(yRow, 0, readLen)
            if (readLen < yRowStride) java.util.Arrays.fill(yRow, readLen, yRowStride, 16) // neutral-ish black, not zero
            out.put(yRow, 0, width)
        }

        // --- Chroma: build interleaved NV12 U,V output regardless of the
        // source's own pixelStride (1 = separate planar, 2 = already
        // interleaved semi-planar). Falls back to neutral 128 (no color,
        // not the same as 0 which reads as strong green after YUV->RGB)
        // for any row that comes up short, rather than leaving whatever
        // allocateDirect() happened to zero-initialize. ---
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRow = ByteArray(uRowStride) { 128.toByte() }
        val vRow = ByteArray(vRowStride) { 128.toByte() }
        var uShortReads = 0
        var vShortReads = 0

        for (row in 0 until chromaHeight) {
            uBuf.position(row * uRowStride)
            val uAvail = uBuf.remaining()
            if (uAvail < uRowStride) uShortReads++
            val uReadLen = minOf(uRowStride, uAvail).coerceAtLeast(0)
            if (uReadLen > 0) uBuf.get(uRow, 0, uReadLen)
            if (uReadLen < uRowStride) java.util.Arrays.fill(uRow, uReadLen, uRowStride, 128.toByte())

            vBuf.position(row * vRowStride)
            val vAvail = vBuf.remaining()
            if (vAvail < vRowStride) vShortReads++
            val vReadLen = minOf(vRowStride, vAvail).coerceAtLeast(0)
            if (vReadLen > 0) vBuf.get(vRow, 0, vReadLen)
            if (vReadLen < vRowStride) java.util.Arrays.fill(vRow, vReadLen, vRowStride, 128.toByte())

            for (col in 0 until chromaWidth) {
                out.put(uRow[col * uPixelStride])
                out.put(vRow[col * vPixelStride])
            }
        }

        // Terse one-line-per-frame diagnostic - cheap enough to log every
        // frame (NativeTrace caps the file at 512KB and truncates on
        // overflow), and this is exactly the data needed to pin down an
        // intermittent per-frame corruption without more guessing.
        com.rynime.nvplayer.rife.NativeTrace.mark(
            applicationContext,
            "imageToNv12 #${frameIndexForTrace++}: fmt=${image.format} planes=${image.planes.size} " +
                "Y(stride=$yRowStride,short=$yShortReads) " +
                "U(stride=$uRowStride,px=$uPixelStride,short=$uShortReads) " +
                "V(stride=$vRowStride,px=$vPixelStride,short=$vShortReads)"
        )

        out.flip()
        return out
    }

    private fun findTrackIndex(extractor: MediaExtractor, mimePrefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return null
    }

    private fun estimateBitrate(width: Int, height: Int): Int =
        (width * height * 4).coerceAtLeast(2_000_000) // ~4 bits/pixel, floor for small sources

    companion object {
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_SCALE_FACTOR = "scale_factor"
        const val KEY_MODEL = "model"
    }
}
