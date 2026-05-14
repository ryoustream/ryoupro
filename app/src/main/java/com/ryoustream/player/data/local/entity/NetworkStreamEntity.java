package com.ryoustream.player.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for persisting network stream history.
 */
@Entity(tableName = "network_streams")
public class NetworkStreamEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @NonNull
    @ColumnInfo(name = "url")
    public String url = "";

    @NonNull
    @ColumnInfo(name = "protocol")
    public String protocol = "HTTP";

    @ColumnInfo(name = "added_at")
    public long addedAt;

    @ColumnInfo(name = "last_played_at")
    public long lastPlayedAt;
}
