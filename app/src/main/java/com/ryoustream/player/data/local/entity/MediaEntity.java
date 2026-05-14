package com.ryoustream.player.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for persisting media metadata locally.
 */
@Entity(tableName = "media_items")
public class MediaEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @NonNull
    @ColumnInfo(name = "display_name")
    public String displayName = "";

    @NonNull
    @ColumnInfo(name = "uri")
    public String uri = "";

    @NonNull
    @ColumnInfo(name = "path")
    public String path = "";

    @ColumnInfo(name = "duration")
    public long duration;

    @ColumnInfo(name = "size")
    public long size;

    @ColumnInfo(name = "date_added")
    public long dateAdded;

    @ColumnInfo(name = "date_modified")
    public long dateModified;

    @NonNull
    @ColumnInfo(name = "mime_type")
    public String mimeType = "";

    @ColumnInfo(name = "type") // VIDEO, AUDIO, STREAM
    public String type = "VIDEO";

    @ColumnInfo(name = "folder_name")
    public String folderName;

    @ColumnInfo(name = "width")
    public int width;

    @ColumnInfo(name = "height")
    public int height;

    @ColumnInfo(name = "frame_rate")
    public double frameRate;

    @ColumnInfo(name = "last_played_at")
    public long lastPlayedAt;

    @ColumnInfo(name = "last_position")
    public long lastPosition;

    @ColumnInfo(name = "is_favorite")
    public boolean isFavorite;

    @ColumnInfo(name = "play_count")
    public int playCount;

    @ColumnInfo(name = "thumbnail_path")
    public String thumbnailPath;
}
