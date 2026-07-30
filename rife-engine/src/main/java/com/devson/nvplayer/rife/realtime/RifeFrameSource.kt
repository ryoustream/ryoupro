package com.devson.nvplayer.rife.realtime

import android.util.Log
import com.devson.nvplayer.rife.RifeConfig

private const val TAG = "RifeFrameSource"

/**
 * Mode B entry point: registers a "rife://" mpv stream source so real-time
 * in-player smoothing can work the way SVP works on desktop (mpv treats the
 * interpolated stream as if it were the actual decoded video).
 *
 * THIS CANNOT WORK YET against the stock mpvlib.aar. [tryEnable] is written
 * to fail closed and explain why, rather than pretend this is wired up:
 * getRawMpvHandle() below is the exact method that needs to exist on a
 * patched MPVLib - see /mpv-android-patch/README.md for the patch this
 * depends on. Once that patch is applied and MPVLib exposes it, delete the
 * `else` branch below and this becomes a real two-line call.
 */
object RifeFrameSource {

    /**
     * @return true if real-time smoothing was successfully registered for
     * this playback session. Always call [RifeCapabilityProbe] first - this
     * function does not re-check device tier itself.
     */
    fun tryEnable(mpvEngine: Any, config: RifeConfig): Boolean {
        val rawHandle = getRawMpvHandle(mpvEngine)
        if (rawHandle == null) {
            Log.w(TAG, "Real-time smoothing unavailable: mpvlib.aar does not expose a raw " +
                "mpv_handle*. Apply the patch in /mpv-android-patch/ first. Falling back - " +
                "playback continues normally, un-smoothed.")
            return false
        }
        val rc = nativeRegisterStreamSource(rawHandle, config.model.assetFolder, config.scale.factor)
        if (rc != 0) {
            Log.e(TAG, "mpv_stream_cb_add_ro registration failed rc=$rc")
            return false
        }
        return true
    }

    /**
     * Placeholder for the accessor a patched MPVLib would need to add
     * (e.g. `MPVLib.getRawHandle(): Long`, wired through to whatever static
     * mpv_handle* mpv-android's own JNI bridge already holds internally).
     * Returns null unconditionally until that patch exists - see
     * mpv-android-patch/0001-add-rife-stream-cb-bridge.patch.
     */
    private fun getRawMpvHandle(mpvEngine: Any): Long? {
        return null
    }

    private external fun nativeRegisterStreamSource(mpvHandle: Long, modelFolder: String, scaleFactor: Int): Int

    init {
        runCatching { System.loadLibrary("rife_engine") }
            .onFailure { Log.e(TAG, "Failed to load rife_engine native library", it) }
    }
}
