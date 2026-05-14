package com.ryoustream.player.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.ryoustream.player.data.local.dao.MediaDao;
import com.ryoustream.player.data.local.dao.NetworkStreamDao;
import com.ryoustream.player.data.local.entity.MediaEntity;
import com.ryoustream.player.data.local.entity.NetworkStreamEntity;
import com.ryoustream.player.domain.model.MediaFolder;
import com.ryoustream.player.domain.model.MediaItem;
import com.ryoustream.player.domain.model.NetworkStream;
import com.ryoustream.player.domain.repository.MediaRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of MediaRepository using Room + MediaStore.
 */
@Singleton
public class MediaRepositoryImpl implements MediaRepository {

    private static final String TAG = "MediaRepositoryImpl";
    private final Context context;
    private final MediaDao mediaDao;
    private final NetworkStreamDao streamDao;
    private final ExecutorService executor;
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);

    @Inject
    public MediaRepositoryImpl(Context context, MediaDao mediaDao, NetworkStreamDao streamDao) {
        this.context = context;
        this.mediaDao = mediaDao;
        this.streamDao = streamDao;
        this.executor = Executors.newFixedThreadPool(2);
    }

    // ── Video ────────────────────────────────────────────────────────────────

    @Override
    public LiveData<List<MediaItem>> getAllVideos() {
        return Transformations.map(mediaDao.getAllVideos(), this::mapEntitiesToItems);
    }

    @Override
    public LiveData<List<MediaItem>> getVideosByFolder(String folderPath) {
        String folderName = new File(folderPath).getName();
        return Transformations.map(mediaDao.getVideosByFolder(folderName), this::mapEntitiesToItems);
    }

    @Override
    public LiveData<List<MediaItem>> searchVideos(String query) {
        return Transformations.map(mediaDao.searchVideos(query), this::mapEntitiesToItems);
    }

    @Override
    public LiveData<List<MediaItem>> getRecentVideos(int limit) {
        return Transformations.map(mediaDao.getRecentVideos(limit), this::mapEntitiesToItems);
    }

    @Override
    public LiveData<List<MediaItem>> getFavoriteVideos() {
        return Transformations.map(mediaDao.getFavoriteVideos(), this::mapEntitiesToItems);
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    @Override
    public LiveData<List<MediaItem>> getAllAudio() {
        return Transformations.map(mediaDao.getAllAudio(), this::mapEntitiesToItems);
    }

    // ── Folders ───────────────────────────────────────────────────────────────

    @Override
    public LiveData<List<MediaFolder>> getAllFolders() {
        return Transformations.map(mediaDao.getAllFolderNames(), folderNames -> {
            List<MediaFolder> folders = new ArrayList<>();
            if (folderNames == null) return folders;
            for (String name : folderNames) {
                folders.add(new MediaFolder("", name, 0, 0, null));
            }
            return folders;
        });
    }

    // ── Playback history ──────────────────────────────────────────────────────

    @Override
    public void updateLastPlayed(long mediaId, long position) {
        executor.execute(() ->
                mediaDao.updateLastPlayed(mediaId, System.currentTimeMillis(), position));
    }

    @Override
    public long getLastPosition(long mediaId) {
        try {
            return mediaDao.getLastPosition(mediaId);
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    @Override
    public void addFavorite(long mediaId) {
        executor.execute(() -> mediaDao.setFavorite(mediaId, true));
    }

    @Override
    public void removeFavorite(long mediaId) {
        executor.execute(() -> mediaDao.setFavorite(mediaId, false));
    }

    @Override
    public boolean isFavorite(long mediaId) {
        try {
            return mediaDao.isFavorite(mediaId);
        } catch (Exception e) {
            return false;
        }
    }

    // ── Network streams ───────────────────────────────────────────────────────

    @Override
    public LiveData<List<NetworkStream>> getAllStreams() {
        return Transformations.map(streamDao.getAllStreams(), entities -> {
            List<NetworkStream> streams = new ArrayList<>();
            if (entities == null) return streams;
            for (NetworkStreamEntity entity : entities) {
                streams.add(new NetworkStream(
                        entity.id, entity.title, entity.url,
                        entity.protocol, entity.addedAt, entity.lastPlayedAt
                ));
            }
            return streams;
        });
    }

    @Override
    public void addStream(NetworkStream stream) {
        executor.execute(() -> {
            NetworkStreamEntity entity = new NetworkStreamEntity();
            entity.title = stream.getTitle();
            entity.url = stream.getUrl();
            entity.protocol = stream.getProtocol();
            entity.addedAt = System.currentTimeMillis();
            entity.lastPlayedAt = 0;
            streamDao.insert(entity);
        });
    }

    @Override
    public void deleteStream(long streamId) {
        executor.execute(() -> streamDao.deleteById(streamId));
    }

    @Override
    public void updateStreamLastPlayed(long streamId) {
        executor.execute(() -> streamDao.updateLastPlayed(streamId, System.currentTimeMillis()));
    }

    // ── Scanner ───────────────────────────────────────────────────────────────

    @Override
    public void scanMedia() {
        if (Boolean.TRUE.equals(scanning.getValue())) return;
        scanning.postValue(true);
        executor.execute(() -> {
            try {
                List<MediaEntity> videos = scanVideosFromMediaStore();
                List<MediaEntity> audio = scanAudioFromMediaStore();
                mediaDao.deleteAllVideos();
                mediaDao.insertAll(videos);
                mediaDao.insertAll(audio);
                Log.d(TAG, "Scan complete: " + videos.size() + " videos, " + audio.size() + " audio");
            } catch (Exception e) {
                Log.e(TAG, "Scan failed", e);
            } finally {
                scanning.postValue(false);
            }
        });
    }

    @Override
    public LiveData<Boolean> isScanning() {
        return scanning;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<MediaEntity> scanVideosFromMediaStore() {
        List<MediaEntity> items = new ArrayList<>();
        Uri videoUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);

        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
        };

        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(
                videoUri, projection, null, null, sortOrder)) {
            if (cursor == null) return items;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
            int displayCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            int dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE);
            int bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
            int widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
            int heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);

            while (cursor.moveToNext()) {
                MediaEntity entity = new MediaEntity();
                entity.id = cursor.getLong(idCol);
                entity.title = cursor.getString(titleCol) != null ? cursor.getString(titleCol) : "";
                entity.displayName = cursor.getString(displayCol) != null ? cursor.getString(displayCol) : "";
                entity.path = cursor.getString(dataCol) != null ? cursor.getString(dataCol) : "";
                entity.uri = ContentUris.withAppendedId(videoUri, entity.id).toString();
                entity.duration = cursor.getLong(durationCol);
                entity.size = cursor.getLong(sizeCol);
                entity.dateAdded = cursor.getLong(dateAddedCol) * 1000;
                entity.dateModified = cursor.getLong(dateModCol) * 1000;
                entity.mimeType = cursor.getString(mimeCol) != null ? cursor.getString(mimeCol) : "";
                entity.folderName = cursor.getString(bucketCol);
                entity.width = cursor.getInt(widthCol);
                entity.height = cursor.getInt(heightCol);
                entity.type = "VIDEO";
                items.add(entity);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning videos", e);
        }
        return items;
    }

    private List<MediaEntity> scanAudioFromMediaStore() {
        List<MediaEntity> items = new ArrayList<>();
        Uri audioUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
        };

        try (Cursor cursor = context.getContentResolver().query(
                audioUri, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor == null) return items;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);

            while (cursor.moveToNext()) {
                MediaEntity entity = new MediaEntity();
                entity.id = cursor.getLong(idCol);
                entity.title = cursor.getString(titleCol) != null ? cursor.getString(titleCol) : "";
                entity.displayName = cursor.getString(displayCol) != null ? cursor.getString(displayCol) : "";
                entity.path = cursor.getString(dataCol) != null ? cursor.getString(dataCol) : "";
                entity.uri = ContentUris.withAppendedId(audioUri, entity.id).toString();
                entity.duration = cursor.getLong(durationCol);
                entity.size = cursor.getLong(sizeCol);
                entity.dateAdded = cursor.getLong(dateAddedCol) * 1000;
                entity.dateModified = cursor.getLong(dateModCol) * 1000;
                entity.mimeType = cursor.getString(mimeCol) != null ? cursor.getString(mimeCol) : "";
                entity.type = "AUDIO";
                items.add(entity);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning audio", e);
        }
        return items;
    }

    private List<MediaItem> mapEntitiesToItems(List<MediaEntity> entities) {
        List<MediaItem> items = new ArrayList<>();
        if (entities == null) return items;
        for (MediaEntity entity : entities) {
            items.add(mapEntityToItem(entity));
        }
        return items;
    }

    private MediaItem mapEntityToItem(MediaEntity entity) {
        Uri uri = entity.uri != null && !entity.uri.isEmpty()
                ? Uri.parse(entity.uri) : Uri.EMPTY;
        return new MediaItem.Builder()
                .id(entity.id)
                .title(entity.title)
                .displayName(entity.displayName)
                .uri(uri)
                .path(entity.path)
                .duration(entity.duration)
                .size(entity.size)
                .dateAdded(entity.dateAdded)
                .dateModified(entity.dateModified)
                .mimeType(entity.mimeType)
                .type("AUDIO".equals(entity.type) ? MediaItem.Type.AUDIO : MediaItem.Type.VIDEO)
                .folderName(entity.folderName)
                .width(entity.width)
                .height(entity.height)
                .frameRate(entity.frameRate)
                .thumbnailPath(entity.thumbnailPath)
                .build();
    }
}
