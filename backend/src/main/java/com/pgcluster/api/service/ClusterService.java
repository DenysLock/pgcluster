package com.pgcluster.api.service;

import com.pgcluster.api.client.HetznerClient;
import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.ClusterCreateRequest;
import com.pgcluster.api.model.dto.ClusterCredentialsResponse;
import com.pgcluster.api.model.dto.ClusterHealthResponse;
import com.pgcluster.api.model.dto.ClusterListResponse;
import com.pgcluster.api.model.dto.ClusterResponse;
import com.pgcluster.api.model.dto.LocationDto;
import com.pgcluster.api.model.dto.ServerTypeDto;
import com.pgcluster.api.model.dto.ServerTypesResponse;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import com.pgcluster.api.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.pgcluster.api.event.ClusterDeleteRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing PostgreSQL clusters.
 * Handles cluster CRUD operations, health checks, and coordinates with
 * ProvisioningService for infrastructure management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final VpsNodeRepository vpsNodeRepository;
    private final HetznerClient hetznerClient;
    @Lazy
    private final ProvisioningService provisioningService;
    private final SshService sshService;
    private final PatroniService patroniService;
    private final ApplicationEventPublisher eventPublisher;

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

    /**
     * Get available Hetzner locations for node placement.
     * Fetches locations from Hetzner API and transforms them with country names and flags.
     * Also checks which locations have the default server type available.
     */
    public List<LocationDto> getAvailableLocations() {
        return getAvailableLocations("cx23"); // Default server type (must match ClusterCreateRequest default)
    }

    /**
     * Get available Hetzner locations for a specific server type.
     */
    public List<LocationDto> getAvailableLocations(String serverType) {
        List<HetznerClient.LocationInfo> hetznerLocations = hetznerClient.getLocations();

        // Get set of locations where the server type is available
        java.util.Set<String> availableLocations;
        try {
            availableLocations = hetznerClient.getAvailableLocationsForServerType(serverType);
        } catch (Exception e) {
            log.warn("Failed to check server type availability, marking all as available: {}", e.getMessage());
            availableLocations = null; // Will mark all as available
        }

        final java.util.Set<String> finalAvailableLocations = availableLocations;

        return hetznerLocations.stream()
                .map(loc -> LocationDto.builder()
                        .id(loc.getName())
                        .name(loc.getDescription())
                        .city(loc.getCity())
                        .country(loc.getCountry())
                        .countryName(LocationDto.getCountryName(loc.getCountry()))
                        .flag(LocationDto.getFlag(loc.getCountry()))
                        .available(finalAvailableLocations == null || finalAvailableLocations.contains(loc.getName()))
                        .build())
                .toList();
    }

    /**
     * Get available server types grouped by category (shared/dedicated).
     * Fetches specs from Hetzner API and checks availability per location.
     */
    public ServerTypesResponse getServerTypes() {
        List<String> sharedTypes = List.of("cx23", "cx33", "cx43", "cx53");
        List<String> dedicatedTypes = List.of("ccx13", "ccx23", "ccx33", "ccx43", "ccx53", "ccx63");

        return ServerTypesResponse.builder()
                .shared(fetchServerTypes(sharedTypes))
                .dedicated(fetchServerTypes(dedicatedTypes))
                .build();
    }

    /**
     * Fetch server type info from Hetzner API for a list of server type names.
     */
    private List<ServerTypeDto> fetchServerTypes(List<String> names) {
        List<ServerTypeDto> result = new ArrayList<>();

        for (String name : names) {
            try {
                HetznerClient.ServerTypeInfo info = hetznerClient.getServerType(name);
                java.util.Set<String> locations = hetznerClient.getAvailableLocationsForServerType(name);

                result.add(ServerTypeDto.builder()
                        .name(info.getName())
                        .cores(info.getCores())
                        .memory(info.getMemory())  // Already in GB from Hetzner API
                        .disk(info.getDisk())
                        .availableLocations(locations)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to fetch server type {}: {}", name, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Validate that all requested node regions are available for the server type.
     */
    private void validateNodeRegions(List<String> nodeRegions, String serverType) {
        java.util.Set<String> availableLocations = hetznerClient.getAvailableLocationsForServerType(serverType);

        List<String> unavailableRegions = nodeRegions.stream()
                .filter(region -> !availableLocations.contains(region))
                .toList();

        if (!unavailableRegions.isEmpty()) {
            throw new ApiException(
                    "The following regions are not available for server type " + serverType + ": " +
                            String.join(", ", unavailableRegions) +
                            ". Please select different regions.",
                    HttpStatus.BAD_REQUEST
            );
        }
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

    /**
     * Create a new cluster. Server creation is SYNCHRONOUS to catch Hetzner errors
     * (account limits, availability) before returning to the user.
     * Remaining provisioning (SSH, containers, DNS) is async.
     */
    public ClusterResponse createCluster(ClusterCreateRequest request, User user) {
        // Validate that all selected regions are available
        validateNodeRegions(request.getNodeRegions(), request.getNodeSize());

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
        String postgresPassword = PasswordGenerator.generate(24);

        // Create cluster entity (hostname is set later in Phase 6 after DNS creation)
        Cluster cluster = Cluster.builder()
                .user(user)
                .name(request.getName())
                .slug(slug)
                .plan(request.getPlan())
                .status(Cluster.STATUS_PENDING)
                .postgresVersion(request.getPostgresVersion())
                .nodeCount(request.getNodeRegions().size()) // 1 = single node, 3 = HA cluster
                .nodeSize(request.getNodeSize())
                .region(request.getNodeRegions().get(0)) // Store first region as primary for display
                .postgresPassword(postgresPassword)
                .nodeRegions(request.getNodeRegions()) // Store all regions for provisioning
                .provisioningStep(Cluster.STEP_CREATING_SERVERS)
                .provisioningProgress(1)
                .build();

        cluster = clusterRepository.saveAndFlush(cluster);
        log.info("Cluster record created: {} ({})", cluster.getName(), cluster.getSlug());

        try {
            // SYNCHRONOUS: Create Hetzner servers (1 or 3 depending on cluster mode)
            // This blocks until all servers are created or fails with rollback
            log.info("Creating all servers synchronously for cluster: {}", cluster.getSlug());
            List<VpsNode> nodes = provisioningService.createAllServersSync(cluster);
            log.info("All {} servers created successfully for cluster: {}", nodes.size(), cluster.getSlug());

            // ASYNC: Continue provisioning (SSH, containers, DNS)
            provisioningService.continueProvisioningFromServers(cluster, nodes);

            return ClusterResponse.fromEntity(cluster);

        } catch (Exception e) {
            // Server creation failed - clean up cluster record
            log.error("Server creation failed for cluster {}: {}", cluster.getSlug(), e.getMessage());

            // Delete cluster record by ID (entity may be detached after transaction rollback)
            // VpsNode records are rolled back automatically by @Transactional
            try {
                clusterRepository.deleteById(cluster.getId());
                log.info("Cleaned up failed cluster record: {}", cluster.getSlug());
            } catch (Exception deleteEx) {
                log.error("Failed to clean up cluster record {}: {}", cluster.getSlug(), deleteEx.getMessage());
            }

            // Parse Hetzner error message for user-friendly display
            String userMessage = parseHetznerError(e.getMessage());
            throw new ApiException(userMessage, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Parse Hetzner API error messages into user-friendly messages.
     */
    private String parseHetznerError(String errorMessage) {
        if (errorMessage == null) {
            return "Failed to create servers. Please try again.";
        }

        // Dedicated vCPU limit: ccx servers use dedicated cores which have a separate quota.
        // e.g. ccx63 needs 48 dedicated vCPUs, but new accounts often only have 8.
        // Users should either use smaller ccx servers or switch to shared cx servers.
        if (errorMessage.contains("dedicated_core_limit") || errorMessage.contains("dedicated core limit")) {
            return "Selected server type exceeds your account's dedicated vCPU limit. " +
                   "Try a smaller dedicated server (ccx13/ccx23) or use shared servers (cx series).";
        }
        if (errorMessage.contains("resource_limit_exceeded") || errorMessage.contains("limit")) {
            return "Server creation failed due to account limits.";
        }
        if (errorMessage.contains("resource_unavailable") || errorMessage.contains("unavailable")) {
            return "Server type not available in selected region. Please try a different region or server type.";
        }
        if (errorMessage.contains("uniqueness_error") || errorMessage.contains("already exists")) {
            return "A server with this name already exists. Please try again.";
        }
        if (errorMessage.contains("unauthorized") || errorMessage.contains("401")) {
            return "Hetzner API authentication failed. Please contact support.";
        }
        if (errorMessage.contains("rate_limit") || errorMessage.contains("429")) {
            return "Too many requests to Hetzner API. Please wait and try again.";
        }

        // Return original message if no specific pattern matched
        return "Failed to create servers: " + errorMessage;
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

        // Publish event to trigger async deletion after transaction commits
        eventPublisher.publishEvent(new ClusterDeleteRequestedEvent(this, cluster));
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
                            .ip(node.getPublicIp())
                            .location(node.getLocation())
                            .flag(LocationDto.getFlagForLocation(node.getLocation()));

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

                    // Parse Patroni JSON response using shared PatroniService methods
                    String role = patroniService.parseRole(output);
                    String state = patroniService.getStateForRole(role);
                    healthBuilder.role(role);
                    healthBuilder.state(state);

                    if ("leader".equals(role)) {
                        leaderNode = node.getPublicIp();
                    } else if ("replica".equals(role)) {
                        replicaCount++;
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
            String suffix = PasswordGenerator.generateAlphanumericSuffix(6);
            String slug = baseSlug + "-" + suffix;

            if (!clusterRepository.existsBySlug(slug)) {
                return slug;
            }
            log.debug("Slug collision for '{}', retrying (attempt {})", slug, attempt + 1);
        }

        // Extremely unlikely to reach here
        throw new ApiException("Unable to generate unique slug after 10 attempts", HttpStatus.CONFLICT);
    }
}
