package com.ryoustream.player.service;

import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Background media playback service with MediaSession support.
 * Enables lockscreen controls, notification playback controls,
 * and background audio.
 */
@UnstableApi
@AndroidEntryPoint
public class MediaPlaybackService extends MediaSessionService {

    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        // MediaSession will be configured when player is initialized
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    public void setMediaSession(MediaSession session) {
        this.mediaSession = session;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
