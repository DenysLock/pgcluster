package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for cluster metrics from Prometheus.
 * Contains time-series data for various metrics, formatted for Lightweight Charts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterMetricsResponse {

    private String clusterId;
    private String clusterSlug;
    private Instant queryTime;
    private String timeRange;      // "1h", "6h", "24h", "7d"
    private Integer stepSeconds;   // Resolution in seconds

    private List<MetricSeries> cpu;
    private List<MetricSeries> memory;
    private List<MetricSeries> disk;
    private List<MetricSeries> connections;
    private List<MetricSeries> qps;
    private List<MetricSeries> replicationLag;

    // New metrics
    private List<MetricSeries> cacheHitRatio;
    private List<MetricSeries> databaseSize;
    private List<MetricSeries> deadlocks;
    private List<MetricSeries> diskSpaceUsed;  // Bytes used on disk (vs diskLimitBytes)
    private Long diskLimitBytes;  // For "X / Y GB" display based on server type

    /**
     * A single time-series for one node
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricSeries {
        private String nodeName;
        private String nodeRole;  // "leader" or "replica"
        private String nodeIp;
        private List<DataPoint> data;
    }

    /**
     * A single data point in the time series.
     * Format matches TradingView Lightweight Charts expectations.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private Long time;    // Unix timestamp in seconds
        private Double value; // Metric value
    }
}
