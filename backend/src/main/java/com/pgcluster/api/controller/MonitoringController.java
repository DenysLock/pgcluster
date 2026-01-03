package com.pgcluster.api.controller;

import com.pgcluster.api.client.PrometheusClient;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Internal monitoring and Prometheus integration")
public class MonitoringController {

    private final ClusterRepository clusterRepository;
    private final PrometheusClient prometheusClient;

    /**
     * Returns all cluster nodes as Prometheus scrape targets.
     * Used by Prometheus http_sd_configs for dynamic service discovery.
     * GET /internal/prometheus-targets
     */
    @GetMapping("/prometheus-targets")
    @Operation(summary = "Get Prometheus scrape targets for all running clusters")
    public ResponseEntity<List<PrometheusTarget>> getPrometheusTargets() {
        List<Cluster> clusters = clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING);
        List<PrometheusTarget> targets = new ArrayList<>();

        for (Cluster cluster : clusters) {
            if (cluster.getNodes() == null) continue;

            for (VpsNode node : cluster.getNodes()) {
                if (node.getPublicIp() == null) continue;

                // Each node exposes multiple exporters
                List<String> nodeTargets = List.of(
                        node.getPublicIp() + ":9100", // node_exporter
                        node.getPublicIp() + ":9187", // postgres_exporter
                        node.getPublicIp() + ":8008"  // patroni metrics
                );

                Map<String, String> labels = new HashMap<>();
                labels.put("cluster_id", cluster.getId().toString());
                labels.put("cluster_slug", cluster.getSlug());
                labels.put("node_role", node.getRole() != null ? node.getRole() : "unknown");
                labels.put("node_name", node.getName());

                targets.add(PrometheusTarget.builder()
                        .targets(nodeTargets)
                        .labels(labels)
                        .build());
            }
        }

        return ResponseEntity.ok(targets);
    }

    /**
     * Returns aggregated cluster metrics summary.
     * GET /internal/cluster-metrics
     */
    @GetMapping("/cluster-metrics")
    @Operation(summary = "Get aggregated cluster metrics")
    public ResponseEntity<Map<String, Object>> getClusterMetricsSummary() {
        Map<String, Integer> statusCounts = new HashMap<>();

        String[] statuses = {
                Cluster.STATUS_PENDING,
                Cluster.STATUS_CREATING,
                Cluster.STATUS_RUNNING,
                Cluster.STATUS_ERROR,
                Cluster.STATUS_DELETING
        };

        for (String status : statuses) {
            List<Cluster> clusters = clusterRepository.findByStatus(status);
            statusCounts.put(status, clusters.size());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("clusters_by_status", statusCounts);
        result.put("prometheus_healthy", prometheusClient.isHealthy());

        return ResponseEntity.ok(result);
    }

    /**
     * Get node health from Prometheus for a specific cluster.
     * GET /internal/clusters/{slug}/prometheus-health
     */
    @GetMapping("/clusters/{slug}/prometheus-health")
    @Operation(summary = "Get cluster node health from Prometheus")
    public ResponseEntity<List<PrometheusClient.NodeHealth>> getClusterPrometheusHealth(
            @PathVariable String slug) {
        List<PrometheusClient.NodeHealth> health = prometheusClient.getClusterNodeHealth(slug);
        return ResponseEntity.ok(health);
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class PrometheusTarget {
        private List<String> targets;
        private Map<String, String> labels;
    }
}
