package com.ryoustream.player;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import dagger.hilt.android.HiltAndroidApp;

/**
 * RyouPlayer Application class.
 * Entry point for Hilt dependency injection and app-wide initialization.
 */
@HiltAndroidApp
public class RyouPlayerApp extends Application {

    public static final String NOTIFICATION_CHANNEL_PLAYBACK = "ryou_playback";
    public static final String NOTIFICATION_CHANNEL_SCANNER = "ryou_scanner";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // Playback channel
            NotificationChannel playbackChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_PLAYBACK,
                    getString(R.string.channel_playback_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            playbackChannel.setDescription(getString(R.string.channel_playback_desc));
            playbackChannel.setShowBadge(false);
            manager.createNotificationChannel(playbackChannel);

            // Scanner channel
            NotificationChannel scannerChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_SCANNER,
                    getString(R.string.channel_scanner_name),
                    NotificationManager.IMPORTANCE_MIN
            );
            scannerChannel.setDescription(getString(R.string.channel_scanner_desc));
            scannerChannel.setShowBadge(false);
            manager.createNotificationChannel(scannerChannel);
        }
    }
}
