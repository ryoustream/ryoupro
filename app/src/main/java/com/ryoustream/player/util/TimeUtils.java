package com.ryoustream.player.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for time formatting.
 */
public final class TimeUtils {

    private TimeUtils() {}

    /**
     * Formats milliseconds into HH:MM:SS or MM:SS string.
     */
    public static String formatDuration(long millis) {
        if (millis < 0) return "00:00";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Formats seconds into HH:MM:SS or MM:SS string.
     */
    public static String formatSeconds(long seconds) {
        return formatDuration(seconds * 1000);
    }
}
