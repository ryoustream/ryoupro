package com.ryoustream.player.data.local.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.ryoustream.player.data.local.dao.MediaDao;
import com.ryoustream.player.data.local.dao.NetworkStreamDao;
import com.ryoustream.player.data.local.entity.MediaEntity;
import com.ryoustream.player.data.local.entity.NetworkStreamEntity;

/**
 * Room database for RyouPlayer.
 */
@Database(
        entities = {MediaEntity.class, NetworkStreamEntity.class},
        version = 1,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "ryou_player.db";
    private static volatile AppDatabase instance;

    public abstract MediaDao mediaDao();
    public abstract NetworkStreamDao networkStreamDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
