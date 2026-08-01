package com.rynime.nvplayer.rife

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

private const val TAG = "RifeInterpolator"

/**
 * Owns one native RifeEngine instance for a given [RifeConfig]. Not
 * thread-safe by design - callers (RifeExportWorker / RifeFrameSource) are
 * expected to own an instance exclusively per active job, matching how
 * MPVPlayerEngine treats MPVLib as a single-owner resource.
 */
class RifeInterpolator private constructor(
    private val appContext: Context,
    private val handle: Long,
    val width: Int,
    val height: Int,
) {
    private val frameBytes = width * height * 3 / 2 // NV12
    private var released = false

    /**
     * Runs one interpolation pass. frameA/frameB must already be NV12 and
     * exactly width*height*3/2 bytes - callers own the conversion from
     * whatever the decoder handed them (see RifeExportWorker for the
     * MediaCodec YUV420Flexible -> NV12 path).
     *
     * Returns a fresh direct ByteBuffer per call rather than accepting a
     * caller-supplied output buffer - simpler call site, and the extra
     * allocation is dwarfed by the interpolation cost itself.
     */
    suspend fun interpolate(frameA: ByteBuffer, frameB: ByteBuffer, timestep: Float): ByteBuffer =
        withContext(Dispatchers.Default) {
            check(!released) { "RifeInterpolator already released" }
            val out = ByteBuffer.allocateDirect(frameBytes)
            NativeTrace.mark(appContext, "nativeInterpolate: BEFORE (t=$timestep)")
            val rc = nativeInterpolate(handle, frameA, frameB, width, height, timestep, out)
            NativeTrace.mark(appContext, "nativeInterpolate: AFTER rc=$rc")
            if (rc != 0) {
                Log.e(TAG, "nativeInterpolate failed rc=$rc")
                throw RifeInterpolationException(rc)
            }
            out
        }

    fun release() {
        if (released) return
        NativeTrace.mark(appContext, "nativeRelease: BEFORE")
        nativeRelease(handle)
        NativeTrace.mark(appContext, "nativeRelease: AFTER")
        released = true
    }

    private external fun nativeInterpolate(
        handle: Long, frameA: ByteBuffer, frameB: ByteBuffer,
        width: Int, height: Int, timestep: Float, out: ByteBuffer,
    ): Int

    companion object {
        init {
            System.loadLibrary("rife_engine")
        }

        @JvmStatic
        private external fun nativeInstallCrashHandler(logFilePath: String)

        /** Call once, early (e.g. from RifeInterpolator.create), before any other native call. */
        fun installCrashHandlerIfNeeded(context: Context) {
            if (crashHandlerInstalled) return
            val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
            val path = File(dir, "native_crash_log.txt").absolutePath
            nativeInstallCrashHandler(path)
            crashHandlerInstalled = true
        }

        @Volatile
        private var crashHandlerInstalled = false

        /**
         * Returns null (never throws) if the model asset is missing or the
         * native engine fails to initialize - callers should already have
         * checked [RifeCapabilityProbe] before reaching this point, but this
         * is the last line of defense per AGENTS.md's zero-crash policy.
         */
        suspend fun create(context: Context, config: RifeConfig, width: Int, height: Int): RifeInterpolator? =
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                installCrashHandlerIfNeeded(appContext)
                NativeTrace.markSessionStart(appContext, "RifeInterpolator.create ${config.model.displayName} ${width}x${height}")
                val modelDir = try {
                    RifeModelInstaller.ensureExtracted(appContext, config.model)
                } catch (e: Exception) {
                    Log.e(TAG, "Model extraction failed for ${config.model.displayName}", e)
                    return@withContext null
                }
                NativeTrace.mark(appContext, "nativeInit: BEFORE (modelDir=${modelDir.absolutePath}, gpuId=${config.gpuId})")
                val handle = nativeInit(
                    modelDir.absolutePath, config.gpuId,
                    config.ttaSpatial, config.ttaTemporal, config.uhdMode, config.numThreads,
                )
                NativeTrace.mark(appContext, "nativeInit: AFTER handle=$handle")
                if (handle == 0L) {
                    Log.e(TAG, "nativeInit failed for ${config.model.displayName}")
                    return@withContext null
                }
                RifeInterpolator(appContext, handle, width, height)
            }

        @JvmStatic
        private external fun nativeInit(
            modelDir: String, gpuId: Int, ttaSpatial: Boolean, ttaTemporal: Boolean,
            uhdMode: Boolean, numThreads: Int,
        ): Long

        @JvmStatic
        private external fun nativeRelease(handle: Long)
    }
}

class RifeInterpolationException(val nativeErrorCode: Int) :
    Exception("RIFE native interpolate failed (code=$nativeErrorCode)")
