package com.pgcluster.api.service;

import com.pgcluster.api.client.PrometheusClient;
import com.pgcluster.api.client.PrometheusClient.RangeQueryResponse;
import com.pgcluster.api.client.PrometheusClient.RangeQueryResult;
import com.pgcluster.api.model.dto.ClusterMetricsResponse;
import com.pgcluster.api.model.dto.ClusterMetricsResponse.DataPoint;
import com.pgcluster.api.model.dto.ClusterMetricsResponse.MetricSeries;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.pgcluster.api.util.NetworkUtils;

import java.time.Instant;
import java.util.*;

/**
 * Service for fetching cluster metrics from Prometheus.
 * Provides time-series data for CPU, memory, disk, connections, QPS, and replication lag.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final PrometheusClient prometheusClient;
    private final ClusterRepository clusterRepository;

    // Time range to duration in seconds
    private static final Map<String, Integer> RANGE_TO_SECONDS = Map.of(
            "1h", 3600,
            "6h", 21600,
            "24h", 86400,
            "7d", 604800
    );

    // Time range to step size (for appropriate resolution)
    private static final Map<String, String> RANGE_TO_STEP = Map.of(
            "1h", "15s",
            "6h", "1m",
            "24h", "5m",
            "7d", "30m"
    );

    // Step string to seconds
    private static final Map<String, Integer> STEP_TO_SECONDS = Map.of(
            "15s", 15,
            "1m", 60,
            "5m", 300,
            "30m", 1800
    );

    // Server type to disk size in GB (for Database Size limit display)
    private static final Map<String, Integer> SERVER_TYPE_DISK_GB = Map.ofEntries(
            Map.entry("cx22", 40),
            Map.entry("cx23", 40),
            Map.entry("cx32", 80),
            Map.entry("cx33", 80),
            Map.entry("cx42", 160),
            Map.entry("cx43", 160),
            Map.entry("cx52", 320),
            Map.entry("cx53", 320),
            Map.entry("cpx11", 40),
            Map.entry("cpx21", 80),
            Map.entry("cpx31", 160),
            Map.entry("cpx41", 320),
            Map.entry("cpx51", 640),
            Map.entry("cax11", 40),
            Map.entry("cax21", 80),
            Map.entry("cax31", 160),
            Map.entry("cax41", 320)
    );

    /**
     * Get metrics for a cluster
     *
     * @param clusterId The cluster UUID
     * @param user      The authenticated user
     * @param range     Time range: "1h", "6h", "24h", "7d"
     * @return ClusterMetricsResponse with all metrics
     */
    public ClusterMetricsResponse getClusterMetrics(UUID clusterId, User user, String range) {
        // Validate cluster ownership
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cluster not found"));

        // Check cluster is running
        if (!Cluster.STATUS_RUNNING.equals(cluster.getStatus())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Metrics only available for running clusters");
        }

        // Validate and normalize range
        if (!RANGE_TO_SECONDS.containsKey(range)) {
            range = "1h"; // Default
        }

        String slug = cluster.getSlug();
        String step = RANGE_TO_STEP.get(range);
        int rangeSeconds = RANGE_TO_SECONDS.get(range);
        int stepSeconds = STEP_TO_SECONDS.get(step);

        long end = Instant.now().getEpochSecond();
        long start = end - rangeSeconds;

        log.debug("Fetching metrics for cluster {} ({}), range={}, step={}", slug, clusterId, range, step);

        // Get disk limit based on node size
        String nodeSize = cluster.getNodeSize();
        Long diskLimitBytes = SERVER_TYPE_DISK_GB.getOrDefault(nodeSize, 40) * 1024L * 1024L * 1024L;

        return ClusterMetricsResponse.builder()
                .clusterId(clusterId.toString())
                .clusterSlug(slug)
                .queryTime(Instant.now())
                .timeRange(range)
                .stepSeconds(stepSeconds)
                .cpu(queryMetric(buildCpuQuery(slug), start, end, step))
                .memory(queryMetric(buildMemoryQuery(slug), start, end, step))
                .disk(queryMetric(buildDiskQuery(slug), start, end, step))
                .connections(queryMetric(buildConnectionsQuery(slug), start, end, step))
                .qps(queryMetric(buildQpsQuery(slug), start, end, step))
                .replicationLag(queryMetric(buildReplicationLagQuery(slug), start, end, step))
                .cacheHitRatio(queryMetric(buildCacheHitRatioQuery(slug), start, end, step))
                .databaseSize(queryMetric(buildDatabaseSizeQuery(slug), start, end, step))
                .deadlocks(queryMetric(buildDeadlocksQuery(slug), start, end, step))
                .diskSpaceUsed(queryMetric(buildDiskSpaceUsedQuery(slug), start, end, step))
                .diskLimitBytes(diskLimitBytes)
                .build();
    }

    /**
     * Query a single metric and transform to MetricSeries list
     */
    private List<MetricSeries> queryMetric(String query, long start, long end, String step) {
        List<MetricSeries> result = new ArrayList<>();

        try {
            RangeQueryResponse response = prometheusClient.queryRange(query, start, end, step);

            if (response == null || response.getData() == null || response.getData().getResult() == null) {
                log.debug("No data returned for query: {}", query);
                return result;
            }

            for (RangeQueryResult queryResult : response.getData().getResult()) {
                Map<String, String> metric = queryResult.getMetric();
                if (metric == null) continue;

                String nodeName = metric.getOrDefault("node_name",
                        metric.getOrDefault("instance", "unknown"));
                String nodeRole = metric.getOrDefault("node_role", "unknown");
                String nodeIp = NetworkUtils.extractIp(metric.getOrDefault("instance", ""));

                List<DataPoint> dataPoints = new ArrayList<>();
                if (queryResult.getValues() != null) {
                    for (List<Object> valueArray : queryResult.getValues()) {
                        if (valueArray.size() >= 2) {
                            try {
                                // Prometheus returns [timestamp, "value"]
                                long timestamp = ((Number) valueArray.get(0)).longValue();
                                double value = Double.parseDouble(String.valueOf(valueArray.get(1)));

                                // Handle NaN and Inf
                                if (Double.isNaN(value) || Double.isInfinite(value)) {
                                    value = 0.0;
                                }

                                dataPoints.add(DataPoint.builder()
                                        .time(timestamp)
                                        .value(value)
                                        .build());
                            } catch (NumberFormatException e) {
                                // Skip invalid values
                            }
                        }
                    }
                }

                if (!dataPoints.isEmpty()) {
                    result.add(MetricSeries.builder()
                            .nodeName(nodeName)
                            .nodeRole(nodeRole)
                            .nodeIp(nodeIp)
                            .data(dataPoints)
                            .build());
                }
            }

        } catch (Exception e) {
            log.error("Failed to query metric: {}", e.getMessage());
        }

        // Sort by role (leader first)
        result.sort((a, b) -> {
            if ("leader".equalsIgnoreCase(a.getNodeRole())) return -1;
            if ("leader".equalsIgnoreCase(b.getNodeRole())) return 1;
            return a.getNodeName().compareTo(b.getNodeName());
        });

        return result;
    }

    // ============ PromQL Query Builders ============

    private String buildCpuQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "100 - (avg by (instance, node_name, node_role) (rate(node_cpu_seconds_total{mode=\"idle\",cluster_slug=\"%s\"}[5m])) * 100)",
                escaped
        );
    }

    private String buildMemoryQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "(1 - (node_memory_MemAvailable_bytes{cluster_slug=\"%s\"} / node_memory_MemTotal_bytes{cluster_slug=\"%s\"})) * 100",
                escaped, escaped
        );
    }

    private String buildDiskQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "(1 - (node_filesystem_avail_bytes{mountpoint=\"/\",cluster_slug=\"%s\"} / node_filesystem_size_bytes{mountpoint=\"/\",cluster_slug=\"%s\"})) * 100",
                escaped, escaped
        );
    }

    private String buildConnectionsQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        // Try pg_stat_database_numbackends first (total connections per database)
        return String.format(
                "pg_stat_database_numbackends{cluster_slug=\"%s\",datname=\"postgres\"}",
                escaped
        );
    }

    private String buildQpsQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "sum by (instance, node_name, node_role) (rate(pg_stat_database_xact_commit{cluster_slug=\"%s\",datname=\"postgres\"}[5m]) + rate(pg_stat_database_xact_rollback{cluster_slug=\"%s\",datname=\"postgres\"}[5m]))",
                escaped, escaped
        );
    }

    private String buildReplicationLagQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        // pg_replication_lag_seconds is exposed by postgres_exporter (replicas only)
        return String.format(
                "pg_replication_lag_seconds{cluster_slug=\"%s\",node_role=\"replica\"}",
                escaped
        );
    }

    // ============ New Metric Query Builders ============

    private String buildCacheHitRatioQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        // Cache hit ratio: percentage of blocks found in shared_buffers vs read from disk
        return String.format(
                "100 * pg_stat_database_blks_hit{cluster_slug=\"%s\",datname=\"postgres\"} / " +
                "(pg_stat_database_blks_hit{cluster_slug=\"%s\",datname=\"postgres\"} + " +
                "pg_stat_database_blks_read{cluster_slug=\"%s\",datname=\"postgres\"})",
                escaped, escaped, escaped
        );
    }

    private String buildDatabaseSizeQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "pg_database_size_bytes{cluster_slug=\"%s\",datname=\"postgres\"}",
                escaped
        );
    }

    private String buildDeadlocksQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "rate(pg_stat_database_deadlocks{cluster_slug=\"%s\",datname=\"postgres\"}[5m])",
                escaped
        );
    }

    private String buildDiskSpaceUsedQuery(String slug) {
        String escaped = prometheusClient.escapeLabel(slug);
        return String.format(
                "node_filesystem_size_bytes{mountpoint=\"/\",cluster_slug=\"%s\"} - " +
                "node_filesystem_avail_bytes{mountpoint=\"/\",cluster_slug=\"%s\"}",
                escaped, escaped
        );
    }
}
