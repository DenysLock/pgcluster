package com.pgcluster.api.util;

/**
 * Utility class for formatting values for display.
 */
public final class FormatUtils {

    private static final String[] BYTE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    private FormatUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Format bytes into human-readable format (e.g., "1.50 GB").
     *
     * @param bytes Number of bytes (nullable)
     * @return Formatted string like "1.50 GB", or "0 B" if null or zero
     */
    public static String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0 B";
        }
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < BYTE_UNITS.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, BYTE_UNITS[unitIndex]);
    }

    /**
     * Format bytes into human-readable format (e.g., "1.50 GB").
     *
     * @param bytes Number of bytes
     * @return Formatted string like "1.50 GB", or "0 B" if zero
     */
    public static String formatBytes(long bytes) {
        return formatBytes(Long.valueOf(bytes));
    }
}
