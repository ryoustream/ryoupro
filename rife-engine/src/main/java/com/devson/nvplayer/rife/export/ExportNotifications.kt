package com.devson.nvplayer.rife.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * Nosved-Player doesn't have a shared notification-channel helper yet (see
 * MediaPlaybackService.kt for the closest existing precedent, playback's own
 * channel) - this creates a dedicated channel for export jobs rather than
 * reusing playback's, since a long export running while nothing is playing
 * should behave like its own thing, not implicitly announce "now playing".
 */
internal object ExportNotifications {
    private const val CHANNEL_ID = "rife_export"
    private const val NOTIFICATION_ID = 4201 // arbitrary, distinct from playback's IDs

    fun buildForegroundInfo(context: Context, progressText: String): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Smooth Motion export")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history) // TODO: swap for app's own icon
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Smooth Motion export", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
