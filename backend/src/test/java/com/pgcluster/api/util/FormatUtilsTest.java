package com.pgcluster.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FormatUtils")
class FormatUtilsTest {

    @Test
    @DisplayName("should return '0 B' for null input")
    void shouldReturnZeroForNull() {
        assertThat(FormatUtils.formatBytes((Long) null)).isEqualTo("0 B");
    }

    @Test
    @DisplayName("should return '0 B' for zero")
    void shouldReturnZeroForZero() {
        assertThat(FormatUtils.formatBytes(0L)).isEqualTo("0 B");
    }

    @Test
    @DisplayName("should format bytes correctly")
    void shouldFormatBytes() {
        assertThat(FormatUtils.formatBytes(500L)).isEqualTo("500.00 B");
    }

    @Test
    @DisplayName("should format kilobytes correctly")
    void shouldFormatKilobytes() {
        assertThat(FormatUtils.formatBytes(1536L)).isEqualTo("1.50 KB");
    }

    @Test
    @DisplayName("should format megabytes correctly")
    void shouldFormatMegabytes() {
        assertThat(FormatUtils.formatBytes(1_572_864L)).isEqualTo("1.50 MB");
    }

    @Test
    @DisplayName("should format gigabytes correctly")
    void shouldFormatGigabytes() {
        assertThat(FormatUtils.formatBytes(1_610_612_736L)).isEqualTo("1.50 GB");
    }

    @Test
    @DisplayName("should format terabytes correctly")
    void shouldFormatTerabytes() {
        assertThat(FormatUtils.formatBytes(1_649_267_441_664L)).isEqualTo("1.50 TB");
    }

    @Test
    @DisplayName("should handle primitive long overload")
    void shouldHandlePrimitiveLong() {
        assertThat(FormatUtils.formatBytes(1024L)).isEqualTo("1.00 KB");
    }
}
