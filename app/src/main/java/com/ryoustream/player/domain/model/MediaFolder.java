package com.ryoustream.player.domain.model;

/**
 * Domain model representing a media folder.
 */
public class MediaFolder {
    private final String path;
    private final String name;
    private final int itemCount;
    private final long lastModified;
    private final String thumbnailPath;

    public MediaFolder(String path, String name, int itemCount, long lastModified, String thumbnailPath) {
        this.path = path;
        this.name = name;
        this.itemCount = itemCount;
        this.lastModified = lastModified;
        this.thumbnailPath = thumbnailPath;
    }

    public String getPath() { return path; }
    public String getName() { return name; }
    public int getItemCount() { return itemCount; }
    public long getLastModified() { return lastModified; }
    public String getThumbnailPath() { return thumbnailPath; }
}
