package com.rynime.nvplayer.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fallback crash diagnostics for when adb/logcat isn't usable. Installs a
 * Thread.UncaughtExceptionHandler that appends the full stack trace (plus
 * basic device info) to a plain-text file under external app-storage, then
 * delegates to whatever handler was already installed (so normal crash-
 * dialog/process-death behavior is unaffected - this only ever adds a file
 * write on the way out, never suppresses or changes the crash itself).
 *
 * File location: Android/data/com.rynime.nvplayer/files/crash_log.txt
 * (visible to any file manager with "show hidden/Android folder" enabled,
 * or via `adb pull` even when live `adb logcat` streaming isn't working).
 */
object CrashLogger {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024 // 2MB - trim if it grows past this

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let the crash logger itself mask the real crash.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val file = logFile(context) ?: return
        if (file.length() > MAX_FILE_BYTES) {
            file.delete()
        }

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("=".repeat(60))
            appendLine("Nokvez Play crash - $timestamp")
            appendLine("Thread: ${thread.name}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${appVersionInfo(context)}")
            appendLine("-".repeat(60))
            append(sw.toString())
            appendLine()
        }

        file.appendText(report)
    }

    private fun appVersionInfo(context: Context): String = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pInfo.versionName} (${pInfo.longVersionCode})"
    } catch (_: Exception) {
        "unknown"
    }

    private fun logFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    /** For a future "Copy crash log" settings action - not wired into UI yet. */
    fun readLogOrNull(context: Context): String? =
        logFile(context)?.takeIf { it.exists() }?.readText()
}
