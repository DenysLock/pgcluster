package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ClusterResponse {

    private UUID id;
    private String name;
    private String slug;
    private String plan;
    private String status;

    // Configuration
    private String postgresVersion;
    private int nodeCount;
    private String nodeSize;
    private String region;

    // Connection info
    private ConnectionInfo connection;

    // Resources
    private Resources resources;

    // Nodes
    private List<NodeInfo> nodes;

    // Error info
    private String errorMessage;

    // Provisioning progress
    private String provisioningStep;
    private Integer provisioningProgress;
    private Integer totalSteps;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    public static class ConnectionInfo {
        private String hostname;
        private int port;
        private String username;
        private boolean credentialsAvailable;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class Resources {
        private int storageGb;
        private int memoryMb;
        private int cpuCores;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class NodeInfo {
        private UUID id;
        private String name;
        private String publicIp;
        private String status;
        private String role;
        private String serverType;
        private String location;
    }

    public static ClusterResponse fromEntity(Cluster cluster) {
        ConnectionInfo connInfo = null;
        if (cluster.getHostname() != null) {
            connInfo = ConnectionInfo.builder()
                    .hostname(cluster.getHostname())
                    .port(cluster.getPort())
                    .username("postgres")
                    .credentialsAvailable(cluster.getPostgresPassword() != null)
                    .build();
        }

        List<NodeInfo> nodeInfos = null;
        if (cluster.getNodes() != null && !cluster.getNodes().isEmpty()) {
            nodeInfos = cluster.getNodes().stream()
                    .map(node -> NodeInfo.builder()
                            .id(node.getId())
                            .name(node.getName())
                            .publicIp(node.getPublicIp())
                            .status(node.getStatus())
                            .role(node.getRole())
                            .serverType(node.getServerType())
                            .location(node.getLocation())
                            .build())
                    .toList();
        }

        return ClusterResponse.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .slug(cluster.getSlug())
                .plan(cluster.getPlan())
                .status(cluster.getStatus())
                .postgresVersion(cluster.getPostgresVersion())
                .nodeCount(cluster.getNodeCount())
                .nodeSize(cluster.getNodeSize())
                .region(cluster.getRegion())
                .connection(connInfo)
                .resources(Resources.builder()
                        .storageGb(cluster.getStorageGb())
                        .memoryMb(cluster.getMemoryMb())
                        .cpuCores(cluster.getCpuCores())
                        .build())
                .nodes(nodeInfos)
                .errorMessage(cluster.getErrorMessage())
                .provisioningStep(cluster.getProvisioningStep())
                .provisioningProgress(cluster.getProvisioningProgress())
                .totalSteps(Cluster.TOTAL_PROVISIONING_STEPS)
                .createdAt(cluster.getCreatedAt())
                .updatedAt(cluster.getUpdatedAt())
                .build();
    }
}
