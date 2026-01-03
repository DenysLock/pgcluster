package com.pgcluster.api.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusClient {

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Execute an instant query against Prometheus
     */
    public QueryResponse query(String promQL) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .toUriString();

            ResponseEntity<QueryResponse> response = restTemplate.getForEntity(url, QueryResponse.class);

            if (response.getBody() != null && "success".equals(response.getBody().getStatus())) {
                return response.getBody();
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
        String query = String.format("up{job=\"customer-patroni\",cluster_slug=\"%s\"}", clusterSlug);
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
                    .ip(extractIp(metric.getOrDefault("instance", "")))
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
            String url = prometheusUrl + "/-/healthy";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract IP from instance label (e.g., "1.2.3.4:8008" -> "1.2.3.4")
     */
    private String extractIp(String instance) {
        int colonIndex = instance.indexOf(':');
        if (colonIndex > 0) {
            return instance.substring(0, colonIndex);
        }
        return instance;
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
}
