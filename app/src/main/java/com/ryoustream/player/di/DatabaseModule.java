package com.ryoustream.player.di;

import android.content.Context;
import com.ryoustream.player.data.local.dao.MediaDao;
import com.ryoustream.player.data.local.dao.NetworkStreamDao;
import com.ryoustream.player.data.local.database.AppDatabase;
import com.ryoustream.player.data.repository.MediaRepositoryImpl;
import com.ryoustream.player.domain.repository.MediaRepository;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

/**
 * Hilt module providing database and repository dependencies.
 */
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return AppDatabase.getInstance(context);
    }

    @Provides
    @Singleton
    public MediaDao provideMediaDao(AppDatabase database) {
        return database.mediaDao();
    }

    @Provides
    @Singleton
    public NetworkStreamDao provideNetworkStreamDao(AppDatabase database) {
        return database.networkStreamDao();
    }

    @Provides
    @Singleton
    public MediaRepository provideMediaRepository(
            @ApplicationContext Context context,
            MediaDao mediaDao,
            NetworkStreamDao streamDao) {
        return new MediaRepositoryImpl(context, mediaDao, streamDao);
    }
}
