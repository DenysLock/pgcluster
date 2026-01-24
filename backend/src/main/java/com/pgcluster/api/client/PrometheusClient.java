package com.pgcluster.api.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgcluster.api.util.NetworkUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for querying Prometheus metrics API.
 * <p>
 * Note: PromQL queries contain curly braces {} which Spring's URI builders interpret
 * as template variables. We use manual URL encoding to handle this correctly.
 */
@Slf4j
@Component
public class PrometheusClient {

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    private final RestClient restClient = RestClient.create();

    /**
     * Execute an instant query against Prometheus
     */
    public QueryResponse query(String promQL) {
        try {
            URI uri = buildPrometheusUri("/api/v1/query", "query", promQL);

            QueryResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(QueryResponse.class);

            if (response != null && "success".equals(response.getStatus())) {
                return response;
            }

            log.warn("Prometheus query failed: {}", promQL);
            return null;

        } catch (Exception e) {
            log.error("Failed to query Prometheus: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get health status for all nodes in a cluster
     */
    public List<NodeHealth> getClusterNodeHealth(String clusterSlug) {
        // Validate and sanitize cluster slug to prevent PromQL injection
        if (clusterSlug == null || !clusterSlug.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$")) {
            log.warn("Invalid cluster slug format for Prometheus query: {}", clusterSlug);
            return new ArrayList<>();
        }

        // Escape any special characters that could break the PromQL query
        String escapedSlug = escapePromQLLabelValue(clusterSlug);
        String query = String.format("up{job=\"customer-patroni\",cluster_slug=\"%s\"}", escapedSlug);
        QueryResponse response = query(query);

        List<NodeHealth> nodes = new ArrayList<>();

        if (response == null || response.getData() == null || response.getData().getResult() == null) {
            return nodes;
        }

        for (QueryResult result : response.getData().getResult()) {
            String status = "down";
            if (result.getValue() != null && result.getValue().size() > 1) {
                Object val = result.getValue().get(1);
                if ("1".equals(String.valueOf(val))) {
                    status = "running";
                }
            }

            Map<String, String> metric = result.getMetric();
            nodes.add(NodeHealth.builder()
                    .nodeName(metric.getOrDefault("node_name", "unknown"))
                    .nodeRole(metric.getOrDefault("node_role", "unknown"))
                    .ip(NetworkUtils.extractIp(metric.getOrDefault("instance", "")))
                    .status(status)
                    .build());
        }

        return nodes;
    }

    /**
     * Check if Prometheus is reachable
     */
    public boolean isHealthy() {
        try {
            restClient.get()
                    .uri(prometheusUrl + "/-/healthy")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Escape special characters in PromQL label values to prevent injection.
     * In PromQL, backslash and double-quote need escaping in label matchers.
     */
    private String escapePromQLLabelValue(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")  // Escape backslashes first
                .replace("\"", "\\\"")  // Escape double quotes
                .replace("\n", "\\n");  // Escape newlines
    }

    // DTOs

    @Data
    public static class QueryResponse {
        private String status;
        private QueryData data;
        private String errorType;
        private String error;
    }

    @Data
    public static class QueryData {
        @JsonProperty("resultType")
        private String resultType;
        private List<QueryResult> result;
    }

    @Data
    public static class QueryResult {
        private Map<String, String> metric;
        private List<Object> value; // [timestamp, value]
    }

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NodeHealth {
        private String nodeName;
        private String nodeRole;
        private String ip;
        private String status; // "running" or "down"
    }

    // ============ Range Query Support ============

    /**
     * Execute a range query against Prometheus for time-series data
     *
     * @param promQL The PromQL query
     * @param start  Start timestamp (Unix epoch seconds)
     * @param end    End timestamp (Unix epoch seconds)
     * @param step   Step duration (e.g., "15s", "1m", "5m")
     * @return RangeQueryResponse or null if query fails
     */
    public RangeQueryResponse queryRange(String promQL, long start, long end, String step) {
        try {
            log.debug("Prometheus range query: {}", promQL);

            URI uri = buildPrometheusUri("/api/v1/query_range",
                    "query", promQL,
                    "start", String.valueOf(start),
                    "end", String.valueOf(end),
                    "step", step);

            RangeQueryResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(RangeQueryResponse.class);

            if (response != null && "success".equals(response.getStatus())) {
                return response;
            }

            log.warn("Prometheus range query failed: {} - {}", promQL,
                    response != null ? response.getError() : "null response");
            return null;

        } catch (Exception e) {
            log.error("Failed to execute Prometheus range query: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build a properly encoded URI for Prometheus API calls.
     * <p>
     * PromQL queries contain {} which Spring's UriBuilder interprets as template variables.
     * This method manually encodes the query to avoid that issue.
     *
     * @param path   API path (e.g., "/api/v1/query")
     * @param params Key-value pairs of query parameters
     * @return Properly encoded URI
     */
    private URI buildPrometheusUri(String path, String... params) throws Exception {
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < params.length; i += 2) {
            if (i > 0) query.append("&");
            query.append(params[i])
                    .append("=")
                    .append(URLEncoder.encode(params[i + 1], StandardCharsets.UTF_8));
        }
        return new URI(prometheusUrl + path + "?" + query);
    }

    /**
     * Get the escaped label value for PromQL queries (exposed for external use)
     */
    public String escapeLabel(String value) {
        return escapePromQLLabelValue(value);
    }

    @Data
    public static class RangeQueryResponse {
        private String status;
        private RangeQueryData data;
        private String errorType;
        private String error;
    }

    @Data
    public static class RangeQueryData {
        @JsonProperty("resultType")
        private String resultType; // "matrix" for range queries
        private List<RangeQueryResult> result;
    }

    @Data
    public static class RangeQueryResult {
        private Map<String, String> metric;
        private List<List<Object>> values; // [[timestamp, value], [timestamp, value], ...]
    }
}
