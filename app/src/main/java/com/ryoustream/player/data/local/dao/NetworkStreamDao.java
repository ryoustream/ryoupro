package com.ryoustream.player.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.ryoustream.player.data.local.entity.NetworkStreamEntity;
import java.util.List;

/**
 * Room DAO for network stream operations.
 */
@Dao
public interface NetworkStreamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NetworkStreamEntity stream);

    @Delete
    void delete(NetworkStreamEntity stream);

    @Query("DELETE FROM network_streams WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM network_streams ORDER BY last_played_at DESC")
    LiveData<List<NetworkStreamEntity>> getAllStreams();

    @Query("UPDATE network_streams SET last_played_at = :timestamp WHERE id = :id")
    void updateLastPlayed(long id, long timestamp);

    @Query("SELECT COUNT(*) FROM network_streams")
    int getCount();
}
