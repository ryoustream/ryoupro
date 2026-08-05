package com.rynime.nvplayer.rife

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * For diagnosing native (C++/JNI) crashes that a Thread.UncaughtExceptionHandler
 * can never see - a SIGSEGV/SIGABRT in native code terminates the process at
 * the OS level, bypassing the JVM's exception mechanism entirely. This just
 * writes a line, with an explicit flush+fsync, before and after each risky
 * native call, so if the process dies mid-call the last line in the file
 * tells us exactly which one was in flight - no backtrace, but "which call"
 * is most of the way to a diagnosis on its own.
 *
 * File: Android/data/com.rynime.nvplayer/files/native_trace.txt
 */
object NativeTrace {
    private const val FILE_NAME = "native_trace.txt"
    private const val MAX_BYTES = 512 * 1024

    /**
     * Same path this object writes to. Exposed so native (C++) code can be
     * handed the exact same file and append to it directly - useful when a
     * device's logcat isn't reliably readable but this file always has
     * been (see RifeEngine::InitParams.traceFilePath).
     */
    fun filePath(context: Context): String {
        val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
        return File(dir, FILE_NAME).absolutePath
    }

    fun mark(context: Context, step: String) {
        try {
            val file = File(filePath(context))
            if (file.length() > MAX_BYTES) file.delete()

            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "[$ts] $step\n"

            FileOutputStream(file, /* append = */ true).use { out ->
                out.write(line.toByteArray())
                out.flush()
                out.fd.sync()
            }
        } catch (_: Exception) {
            // Diagnostics must never be the thing that crashes the app.
        }
    }

    fun markSessionStart(context: Context, sessionLabel: String) {
        mark(context, "==== $sessionLabel | ${Build.MANUFACTURER} ${Build.MODEL} " +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) ====")
    }
}
