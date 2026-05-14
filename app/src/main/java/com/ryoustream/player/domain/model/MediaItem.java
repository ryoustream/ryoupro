package com.ryoustream.player.domain.model;

import android.net.Uri;
import androidx.annotation.Nullable;

/**
 * Domain model representing a playable media item.
 */
public class MediaItem {

    public enum Type {
        VIDEO, AUDIO, STREAM
    }

    private final long id;
    private final String title;
    private final String displayName;
    private final Uri uri;
    private final String path;
    private final long duration; // milliseconds
    private final long size; // bytes
    private final long dateAdded;
    private final long dateModified;
    private final String mimeType;
    private final Type type;

    @Nullable private final String thumbnailPath;
    @Nullable private final String folderName;
    @Nullable private final String resolution;
    @Nullable private final String artistName;
    @Nullable private final String albumName;
    private final int width;
    private final int height;
    private final double frameRate;

    private MediaItem(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.displayName = builder.displayName;
        this.uri = builder.uri;
        this.path = builder.path;
        this.duration = builder.duration;
        this.size = builder.size;
        this.dateAdded = builder.dateAdded;
        this.dateModified = builder.dateModified;
        this.mimeType = builder.mimeType;
        this.type = builder.type;
        this.thumbnailPath = builder.thumbnailPath;
        this.folderName = builder.folderName;
        this.resolution = builder.resolution;
        this.artistName = builder.artistName;
        this.albumName = builder.albumName;
        this.width = builder.width;
        this.height = builder.height;
        this.frameRate = builder.frameRate;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDisplayName() { return displayName; }
    public Uri getUri() { return uri; }
    public String getPath() { return path; }
    public long getDuration() { return duration; }
    public long getSize() { return size; }
    public long getDateAdded() { return dateAdded; }
    public long getDateModified() { return dateModified; }
    public String getMimeType() { return mimeType; }
    public Type getType() { return type; }
    @Nullable public String getThumbnailPath() { return thumbnailPath; }
    @Nullable public String getFolderName() { return folderName; }
    @Nullable public String getResolution() { return resolution; }
    @Nullable public String getArtistName() { return artistName; }
    @Nullable public String getAlbumName() { return albumName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public double getFrameRate() { return frameRate; }

    public boolean isVideo() { return type == Type.VIDEO; }
    public boolean isAudio() { return type == Type.AUDIO; }
    public boolean isStream() { return type == Type.STREAM; }

    /**
     * Returns formatted duration string (HH:MM:SS or MM:SS)
     */
    public String getFormattedDuration() {
        long totalSeconds = duration / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Returns human-readable file size
     */
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public static class Builder {
        private long id;
        private String title = "";
        private String displayName = "";
        private Uri uri;
        private String path = "";
        private long duration = 0;
        private long size = 0;
        private long dateAdded = 0;
        private long dateModified = 0;
        private String mimeType = "";
        private Type type = Type.VIDEO;
        private String thumbnailPath;
        private String folderName;
        private String resolution;
        private String artistName;
        private String albumName;
        private int width = 0;
        private int height = 0;
        private double frameRate = 0;

        public Builder id(long id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder uri(Uri uri) { this.uri = uri; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder duration(long duration) { this.duration = duration; return this; }
        public Builder size(long size) { this.size = size; return this; }
        public Builder dateAdded(long dateAdded) { this.dateAdded = dateAdded; return this; }
        public Builder dateModified(long dateModified) { this.dateModified = dateModified; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder type(Type type) { this.type = type; return this; }
        public Builder thumbnailPath(String path) { this.thumbnailPath = path; return this; }
        public Builder folderName(String name) { this.folderName = name; return this; }
        public Builder resolution(String resolution) { this.resolution = resolution; return this; }
        public Builder artistName(String name) { this.artistName = name; return this; }
        public Builder albumName(String name) { this.albumName = name; return this; }
        public Builder width(int width) { this.width = width; return this; }
        public Builder height(int height) { this.height = height; return this; }
        public Builder frameRate(double fps) { this.frameRate = fps; return this; }
        public MediaItem build() { return new MediaItem(this); }
    }
}
