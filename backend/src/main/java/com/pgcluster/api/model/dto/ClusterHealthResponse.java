package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterHealthResponse {

    private String clusterId;
    private String clusterSlug;
    private String overallStatus; // healthy, degraded, unhealthy
    private PatroniStatus patroni;
    private List<NodeHealth> nodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatroniStatus {
        private String leader;
        private int replicas;
        private boolean haEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeHealth {
        private String name;
        private String ip;
        private String role; // leader, replica
        private String state; // running, streaming, unknown
        private boolean reachable;
        private Long lagBytes; // replication lag in bytes (for replicas)
        private String location; // e.g., "fsn1", "hel1"
        private String flag; // e.g., "🇩🇪", "🇫🇮"
    }
}
