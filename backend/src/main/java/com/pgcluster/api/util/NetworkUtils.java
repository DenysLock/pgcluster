package com.pgcluster.api.util;

/**
 * Utility class for network-related operations.
 */
public final class NetworkUtils {

    private NetworkUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract IP address from a host:port string.
     * <p>
     * Examples:
     * - "192.168.1.1:9100" → "192.168.1.1"
     * - "192.168.1.1" → "192.168.1.1"
     * - "" → ""
     *
     * @param hostPort The host:port string (e.g., from Prometheus instance label)
     * @return The IP address without port, or the original string if no port present
     */
    public static String extractIp(String hostPort) {
        if (hostPort == null || hostPort.isEmpty()) {
            return "";
        }
        int colonIndex = hostPort.indexOf(':');
        if (colonIndex > 0) {
            return hostPort.substring(0, colonIndex);
        }
        return hostPort;
    }
}
