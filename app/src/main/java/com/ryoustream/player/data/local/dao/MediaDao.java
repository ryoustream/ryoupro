package com.ryoustream.player.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.ryoustream.player.data.local.entity.MediaEntity;
import java.util.List;

/**
 * Room DAO for media item operations.
 */
@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MediaEntity> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MediaEntity item);

    @Update
    void update(MediaEntity item);

    @Delete
    void delete(MediaEntity item);

    @Query("DELETE FROM media_items WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM media_items WHERE type = 'VIDEO' ORDER BY date_added DESC")
    LiveData<List<MediaEntity>> getAllVideos();

    @Query("SELECT * FROM media_items WHERE type = 'AUDIO' ORDER BY title ASC")
    LiveData<List<MediaEntity>> getAllAudio();

    @Query("SELECT * FROM media_items WHERE folder_name = :folder AND type = 'VIDEO' ORDER BY display_name ASC")
    LiveData<List<MediaEntity>> getVideosByFolder(String folder);

    @Query("SELECT * FROM media_items WHERE type = 'VIDEO' AND (title LIKE '%' || :query || '%' OR display_name LIKE '%' || :query || '%') ORDER BY title ASC")
    LiveData<List<MediaEntity>> searchVideos(String query);

    @Query("SELECT * FROM media_items WHERE type = 'VIDEO' ORDER BY last_played_at DESC LIMIT :limit")
    LiveData<List<MediaEntity>> getRecentVideos(int limit);

    @Query("SELECT * FROM media_items WHERE is_favorite = 1 ORDER BY title ASC")
    LiveData<List<MediaEntity>> getFavoriteVideos();

    @Query("SELECT * FROM media_items WHERE id = :id")
    MediaEntity getById(long id);

    @Query("UPDATE media_items SET last_played_at = :timestamp, last_position = :position, play_count = play_count + 1 WHERE id = :id")
    void updateLastPlayed(long id, long timestamp, long position);

    @Query("SELECT last_position FROM media_items WHERE id = :id")
    long getLastPosition(long id);

    @Query("UPDATE media_items SET is_favorite = :favorite WHERE id = :id")
    void setFavorite(long id, boolean favorite);

    @Query("SELECT is_favorite FROM media_items WHERE id = :id")
    boolean isFavorite(long id);

    @Query("SELECT DISTINCT folder_name FROM media_items WHERE type = 'VIDEO' AND folder_name IS NOT NULL")
    LiveData<List<String>> getAllFolderNames();

    @Query("DELETE FROM media_items WHERE type = 'VIDEO'")
    void deleteAllVideos();
}
