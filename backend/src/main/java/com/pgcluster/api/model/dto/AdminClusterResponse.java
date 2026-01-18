package com.pgcluster.api.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgcluster.api.model.entity.Cluster;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class AdminClusterResponse {

    private UUID id;
    private String name;
    private String slug;
    private String plan;
    private String status;

    // Owner info
    @JsonProperty("owner_id")
    private UUID ownerId;

    @JsonProperty("owner_email")
    private String ownerEmail;

    // Configuration
    @JsonProperty("postgres_version")
    private String postgresVersion;

    @JsonProperty("node_count")
    private int nodeCount;

    @JsonProperty("node_size")
    private String nodeSize;

    private String region;

    // Connection info
    private ConnectionInfo connection;

    // Resources
    private Resources resources;

    // Nodes
    private List<NodeInfo> nodes;

    // Error info
    @JsonProperty("error_message")
    private String errorMessage;

    // Provisioning progress
    @JsonProperty("provisioning_step")
    private String provisioningStep;

    @JsonProperty("provisioning_progress")
    private Integer provisioningProgress;

    @JsonProperty("total_steps")
    private Integer totalSteps;

    // Timestamps
    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    public static class ConnectionInfo {
        private String hostname;
        private int port;
        private String username;
        @JsonProperty("credentials_available")
        private boolean credentialsAvailable;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class Resources {
        @JsonProperty("storage_gb")
        private int storageGb;
        @JsonProperty("memory_mb")
        private int memoryMb;
        @JsonProperty("cpu_cores")
        private int cpuCores;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class NodeInfo {
        private UUID id;
        private String name;
        @JsonProperty("public_ip")
        private String publicIp;
        private String status;
        private String role;
        @JsonProperty("server_type")
        private String serverType;
        private String location;
    }

    public static AdminClusterResponse fromEntity(Cluster cluster) {
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

        return AdminClusterResponse.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .slug(cluster.getSlug())
                .plan(cluster.getPlan())
                .status(cluster.getStatus())
                .ownerId(cluster.getUser().getId())
                .ownerEmail(cluster.getUser().getEmail())
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
