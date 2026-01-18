package com.pgcluster.api.service;

import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.ClusterCreateRequest;
import com.pgcluster.api.model.dto.ClusterCredentialsResponse;
import com.pgcluster.api.model.dto.ClusterHealthResponse;
import com.pgcluster.api.model.dto.ClusterListResponse;
import com.pgcluster.api.model.dto.ClusterResponse;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final VpsNodeRepository vpsNodeRepository;
    @Lazy
    private final ProvisioningService provisioningService;
    private final SshService sshService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public ClusterListResponse listClusters(User user) {
        List<Cluster> clusters = clusterRepository.findByUserOrderByCreatedAtDesc(user);

        List<ClusterResponse> responses = clusters.stream()
                .map(ClusterResponse::fromEntity)
                .toList();

        return ClusterListResponse.builder()
                .clusters(responses)
                .count(responses.size())
                .build();
    }

    public ClusterResponse getCluster(UUID id, User user) {
        Cluster cluster = clusterRepository.findByIdAndUserWithNodes(id, user)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        return ClusterResponse.fromEntity(cluster);
    }

    /**
     * Get cluster credentials, access is logged.
     */
    public ClusterCredentialsResponse getClusterCredentials(UUID id, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        if (cluster.getHostname() == null || cluster.getPostgresPassword() == null) {
            throw new ApiException("Cluster credentials not yet available", HttpStatus.SERVICE_UNAVAILABLE);
        }

        log.info("Credentials accessed for cluster {} by user {}", cluster.getSlug(), user.getEmail());

        int pooledPort = 6432;

        return ClusterCredentialsResponse.builder()
                .hostname(cluster.getHostname())
                .port(cluster.getPort())
                .pooledPort(pooledPort)
                .database("postgres")
                .username("postgres")
                .password(cluster.getPostgresPassword())
                .connectionString(String.format("postgresql://postgres:%s@%s:%d/postgres",
                        cluster.getPostgresPassword(),
                        cluster.getHostname(),
                        cluster.getPort()))
                .pooledConnectionString(String.format("postgresql://postgres:%s@%s:%d/postgres",
                        cluster.getPostgresPassword(),
                        cluster.getHostname(),
                        pooledPort))
                .sslMode("prefer")
                .retrievedAt(java.time.Instant.now())
                .warning("Store these credentials securely. Do not share or commit to version control.")
                .build();
    }

    @Transactional
    public ClusterResponse createCluster(ClusterCreateRequest request, User user) {
        // Generate slug if not provided
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateUniqueSlug(request.getName());
        } else {
            // User provided custom slug - check uniqueness
            if (clusterRepository.existsBySlug(slug)) {
                throw new ApiException("Cluster slug already exists", HttpStatus.CONFLICT);
            }
        }

        // Generate postgres password
        String postgresPassword = generatePassword(24);

        // Create cluster entity (hostname is set later in Phase 6 after DNS creation)
        Cluster cluster = Cluster.builder()
                .user(user)
                .name(request.getName())
                .slug(slug)
                .plan(request.getPlan())
                .status(Cluster.STATUS_PENDING)
                .postgresVersion(request.getPostgresVersion())
                .nodeCount(request.getNodeCount())
                .nodeSize(request.getNodeSize())
                .region(request.getRegion())
                .postgresPassword(postgresPassword)
                .build();

        cluster = clusterRepository.save(cluster);
        log.info("Cluster created: {} ({})", cluster.getName(), cluster.getSlug());

        // Trigger async provisioning AFTER transaction commits
        final Cluster savedCluster = cluster;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                provisioningService.provisionClusterAsync(savedCluster);
            }
        });

        return ClusterResponse.fromEntity(cluster);
    }

    @Transactional
    public void deleteCluster(UUID id, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        // Check if cluster can be deleted
        if (Cluster.STATUS_DELETING.equals(cluster.getStatus())) {
            throw new ApiException("Cluster is already being deleted", HttpStatus.CONFLICT);
        }

        // Update status to deleting
        cluster.setStatus(Cluster.STATUS_DELETING);
        clusterRepository.save(cluster);

        log.info("Cluster marked for deletion: {} ({})", cluster.getName(), cluster.getSlug());

        // Trigger async deletion AFTER transaction commits
        final Cluster clusterToDelete = cluster;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                provisioningService.deleteClusterAsync(clusterToDelete);
            }
        });
    }

    /**
     * Get cluster health status by querying Patroni on each node
     */
    public ClusterHealthResponse getClusterHealth(UUID id, User user) {
        Cluster cluster = clusterRepository.findByIdAndUserWithNodes(id, user)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        // Check if cluster is running
        if (!Cluster.STATUS_RUNNING.equals(cluster.getStatus())) {
            return ClusterHealthResponse.builder()
                    .clusterId(cluster.getId().toString())
                    .clusterSlug(cluster.getSlug())
                    .overallStatus("unavailable")
                    .build();
        }

        List<VpsNode> nodes = vpsNodeRepository.findByCluster(cluster);
        List<ClusterHealthResponse.NodeHealth> nodeHealths = new ArrayList<>();
        String leaderNode = null;
        int replicaCount = 0;

        for (VpsNode node : nodes) {
            ClusterHealthResponse.NodeHealth.NodeHealthBuilder healthBuilder =
                    ClusterHealthResponse.NodeHealth.builder()
                            .name(node.getName())
                            .ip(node.getPublicIp());

            try {
                // Query Patroni REST API on each node
                SshService.CommandResult result = sshService.executeCommand(
                        node.getPublicIp(),
                        "curl -s http://localhost:8008/patroni",
                        10000
                );

                if (result.isSuccess()) {
                    String output = result.getStdout();
                    healthBuilder.reachable(true);

                    // Parse Patroni JSON response - handle whitespace variations
                    // Look for "role" : "primary" or "role": "replica" etc
                    if (output.contains("\"primary\"") && output.contains("\"role\"")) {
                        healthBuilder.role("leader");
                        healthBuilder.state("running");
                        leaderNode = node.getPublicIp();
                    } else if (output.contains("\"master\"") && output.contains("\"role\"")) {
                        healthBuilder.role("leader");
                        healthBuilder.state("running");
                        leaderNode = node.getPublicIp();
                    } else if (output.contains("\"replica\"") && output.contains("\"role\"")) {
                        healthBuilder.role("replica");
                        healthBuilder.state("streaming");
                        replicaCount++;
                    } else {
                        healthBuilder.role("unknown");
                        healthBuilder.state("unknown");
                    }
                } else {
                    healthBuilder.role("unknown");
                    healthBuilder.state("unknown");
                    healthBuilder.reachable(false);
                }
            } catch (Exception e) {
                log.warn("Failed to get health from node {}: {}", node.getName(), e.getMessage());
                healthBuilder.role("unknown");
                healthBuilder.state("unknown");
                healthBuilder.reachable(false);
            }

            nodeHealths.add(healthBuilder.build());
        }

        // Determine overall status
        String overallStatus;
        if (leaderNode != null && nodeHealths.stream().allMatch(n -> n.isReachable())) {
            overallStatus = "healthy";
        } else if (leaderNode != null) {
            overallStatus = "degraded";
        } else {
            overallStatus = "unhealthy";
        }

        return ClusterHealthResponse.builder()
                .clusterId(cluster.getId().toString())
                .clusterSlug(cluster.getSlug())
                .overallStatus(overallStatus)
                .patroni(ClusterHealthResponse.PatroniStatus.builder()
                        .leader(leaderNode)
                        .replicas(replicaCount)
                        .haEnabled(nodes.size() > 1)
                        .build())
                .nodes(nodeHealths)
                .build();
    }

    /**
     * Generate a unique slug with retry logic to handle collisions.
     * Uses 6-character alphanumeric suffix
     */
    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        // Limit base slug length to keep total slug reasonable
        if (baseSlug.length() > 50) {
            baseSlug = baseSlug.substring(0, 50);
        }

        // Try up to 10 times to generate unique slug
        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = generateAlphanumericSuffix(6);
            String slug = baseSlug + "-" + suffix;

            if (!clusterRepository.existsBySlug(slug)) {
                return slug;
            }
            log.debug("Slug collision for '{}', retrying (attempt {})", slug, attempt + 1);
        }

        // Extremely unlikely to reach here
        throw new ApiException("Unable to generate unique slug after 10 attempts", HttpStatus.CONFLICT);
    }

    /**
     * Generate random alphanumeric suffix.
     * 6 chars from 36 characters (a-z, 0-9) = 36^6 combinations
     */
    private String generateAlphanumericSuffix(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generatePassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
