package com.ryoustream.player.domain.repository;

import androidx.lifecycle.LiveData;
import com.ryoustream.player.domain.model.MediaFolder;
import com.ryoustream.player.domain.model.MediaItem;
import com.ryoustream.player.domain.model.NetworkStream;
import java.util.List;

/**
 * Repository interface for media data operations.
 * Part of the domain layer — no Android or framework dependencies.
 */
public interface MediaRepository {

    // ── Video ──────────────────────────────────────────────────────────────
    LiveData<List<MediaItem>> getAllVideos();
    LiveData<List<MediaItem>> getVideosByFolder(String folderPath);
    LiveData<List<MediaItem>> searchVideos(String query);
    LiveData<List<MediaItem>> getRecentVideos(int limit);
    LiveData<List<MediaItem>> getFavoriteVideos();

    // ── Audio ──────────────────────────────────────────────────────────────
    LiveData<List<MediaItem>> getAllAudio();

    // ── Folders ────────────────────────────────────────────────────────────
    LiveData<List<MediaFolder>> getAllFolders();

    // ── Playback history ──────────────────────────────────────────────────
    void updateLastPlayed(long mediaId, long position);
    long getLastPosition(long mediaId);

    // ── Favorites ─────────────────────────────────────────────────────────
    void addFavorite(long mediaId);
    void removeFavorite(long mediaId);
    boolean isFavorite(long mediaId);

    // ── Network streams ───────────────────────────────────────────────────
    LiveData<List<NetworkStream>> getAllStreams();
    void addStream(NetworkStream stream);
    void deleteStream(long streamId);
    void updateStreamLastPlayed(long streamId);

    // ── Scanner ───────────────────────────────────────────────────────────
    void scanMedia();
    LiveData<Boolean> isScanning();
}
