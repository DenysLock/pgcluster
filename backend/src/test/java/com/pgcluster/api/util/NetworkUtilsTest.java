package com.pgcluster.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NetworkUtils")
class NetworkUtilsTest {

    @Test
    @DisplayName("should extract IP from host:port string")
    void shouldExtractIpFromHostPort() {
        assertThat(NetworkUtils.extractIp("192.168.1.1:9100")).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("should return IP when no port present")
    void shouldReturnIpWhenNoPort() {
        assertThat(NetworkUtils.extractIp("192.168.1.1")).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("should return empty string for null input")
    void shouldReturnEmptyForNull() {
        assertThat(NetworkUtils.extractIp(null)).isEqualTo("");
    }

    @Test
    @DisplayName("should return empty string for empty input")
    void shouldReturnEmptyForEmpty() {
        assertThat(NetworkUtils.extractIp("")).isEqualTo("");
    }

    @Test
    @DisplayName("should handle string starting with colon")
    void shouldHandleLeadingColon() {
        assertThat(NetworkUtils.extractIp(":9100")).isEqualTo(":9100");
    }
}
