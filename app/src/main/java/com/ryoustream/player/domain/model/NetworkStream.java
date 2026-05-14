package com.ryoustream.player.domain.model;

/**
 * Domain model for a network stream URL entry.
 */
public class NetworkStream {
    private final long id;
    private final String title;
    private final String url;
    private final String protocol;
    private final long addedAt;
    private long lastPlayedAt;

    public NetworkStream(long id, String title, String url, String protocol,
                         long addedAt, long lastPlayedAt) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.protocol = protocol;
        this.addedAt = addedAt;
        this.lastPlayedAt = lastPlayedAt;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getProtocol() { return protocol; }
    public long getAddedAt() { return addedAt; }
    public long getLastPlayedAt() { return lastPlayedAt; }
    public void setLastPlayedAt(long time) { this.lastPlayedAt = time; }

    /**
     * Detect stream protocol from URL
     */
    public static String detectProtocol(String url) {
        if (url == null) return "Unknown";
        String lower = url.toLowerCase();
        if (lower.startsWith("rtsp://")) return "RTSP";
        if (lower.startsWith("rtmp://")) return "RTMP";
        if (lower.endsWith(".m3u8") || lower.contains(".m3u8?")) return "HLS";
        if (lower.endsWith(".mpd") || lower.contains(".mpd?")) return "DASH";
        if (lower.startsWith("smb://")) return "SMB";
        if (lower.startsWith("ftp://")) return "FTP";
        if (lower.startsWith("sftp://")) return "SFTP";
        if (lower.startsWith("https://")) return "HTTPS";
        if (lower.startsWith("http://")) return "HTTP";
        return "Unknown";
    }
}
