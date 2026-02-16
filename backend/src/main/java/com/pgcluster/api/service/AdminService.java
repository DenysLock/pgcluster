package com.pgcluster.api.service;

import com.pgcluster.api.event.ClusterDeleteRequestedEvent;
import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.*;
import com.pgcluster.api.model.entity.*;
import com.pgcluster.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;
    private final BackupRepository backupRepository;
    private final ExportRepository exportRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final MetricsService metricsService;
    private final BackupService backupService;
    private final ExportService exportService;
    private final VpsNodeRepository vpsNodeRepository;
    private final SshService sshService;
    private final PatroniService patroniService;

    /**
     * Get platform-wide statistics (excludes deleted clusters)
     */
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalClusters = clusterRepository.countByStatusNot(Cluster.STATUS_DELETED);
        long runningClusters = clusterRepository.countByStatus(Cluster.STATUS_RUNNING);
        long totalUsers = userRepository.count();

        return AdminStatsResponse.builder()
                .totalClusters(totalClusters)
                .runningClusters(runningClusters)
                .totalUsers(totalUsers)
                .build();
    }

    /**
     * List all clusters across all users
     * @param includeDeleted if true, includes deleted clusters for audit purposes
     */
    @Transactional(readOnly = true)
    public AdminClusterListResponse listAllClusters(boolean includeDeleted) {
        List<Cluster> clusters = includeDeleted
                ? clusterRepository.findAllWithUserIncludingDeleted()
                : clusterRepository.findAllWithUser();

        List<AdminClusterResponse> responses = clusters.stream()
                .map(AdminClusterResponse::fromEntity)
                .toList();

        return AdminClusterListResponse.builder()
                .clusters(responses)
                .count(responses.size())
                .build();
    }

    /**
     * Get specific cluster details (any user's cluster)
     */
    @Transactional(readOnly = true)
    public AdminClusterResponse getCluster(UUID id) {
        Cluster cluster = clusterRepository.findByIdWithUserAndNodes(id)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        return AdminClusterResponse.fromEntity(cluster);
    }

    /**
     * List all users
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse listAllUsers() {
        List<AdminUserResponse> users = userRepository.findAll().stream()
                .map(AdminUserResponse::fromEntity)
                .toList();

        return AdminUserListResponse.builder()
                .users(users)
                .count(users.size())
                .build();
    }

    // ==================== User Management ====================

    /**
     * Create a new user (admin provides credentials)
     */
    @Transactional
    public AdminUserResponse createUser(CreateUserRequest request, User admin) {
        String email = request.getEmail().toLowerCase();

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("Email already registered", HttpStatus.CONFLICT);
        }

        // Validate role
        String role = request.getRole();
        if (role != null && !role.equals("user") && !role.equals("admin")) {
            throw new ApiException("Invalid role. Must be 'user' or 'admin'", HttpStatus.BAD_REQUEST);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(role != null ? role : "user")
                .active(true)
                .build();

        user = userRepository.save(user);
        log.info("Admin {} created user: {}", admin.getEmail(), user.getEmail());

        // Audit log
        auditLogService.log(AuditLog.USER_CREATED, admin, "user", user.getId(),
                Map.of("created_email", user.getEmail(), "role", user.getRole()));

        return AdminUserResponse.fromEntity(user);
    }

    /**
     * Get detailed user info with their clusters
     */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        List<Cluster> userClusters = clusterRepository.findByUserAndStatusNotOrderByCreatedAtDesc(user, Cluster.STATUS_DELETED);
        List<AdminClusterResponse> clusterResponses = userClusters.stream()
                .map(AdminClusterResponse::fromEntity)
                .toList();

        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .clusters(clusterResponses)
                .clusterCount(clusterResponses.size())
                .build();
    }

    /**
     * Disable a user account (soft disable)
     */
    @Transactional
    public AdminUserResponse disableUser(UUID userId, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        if (user.getId().equals(admin.getId())) {
            throw new ApiException("Cannot disable your own account", HttpStatus.BAD_REQUEST);
        }

        user.setActive(false);
        user = userRepository.save(user);
        log.info("Admin {} disabled user: {}", admin.getEmail(), user.getEmail());

        auditLogService.log(AuditLog.USER_DISABLED, admin, "user", user.getId(),
                Map.of("disabled_email", user.getEmail()));

        return AdminUserResponse.fromEntity(user);
    }

    /**
     * Enable a user account
     */
    @Transactional
    public AdminUserResponse enableUser(UUID userId, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        user.setActive(true);
        user = userRepository.save(user);
        log.info("Admin {} enabled user: {}", admin.getEmail(), user.getEmail());

        auditLogService.log(AuditLog.USER_ENABLED, admin, "user", user.getId(),
                Map.of("enabled_email", user.getEmail()));

        return AdminUserResponse.fromEntity(user);
    }

    /**
     * Reset user password (admin sets new password)
     */
    @Transactional
    public void resetUserPassword(UUID userId, ResetPasswordRequest request, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        // Prevent admin from resetting their own password through this endpoint
        if (user.getId().equals(admin.getId())) {
            throw new ApiException("Cannot reset your own password through admin panel", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Admin {} reset password for user: {}", admin.getEmail(), user.getEmail());

        auditLogService.log(AuditLog.USER_PASSWORD_RESET, admin, "user", user.getId(),
                Map.of("reset_for_email", user.getEmail()));
    }

    // ==================== Cluster Management ====================

    /**
     * Delete any cluster as admin
     */
    @Transactional
    public void deleteClusterAsAdmin(UUID clusterId, User admin) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        if (Cluster.STATUS_DELETING.equals(cluster.getStatus())) {
            throw new ApiException("Cluster is already being deleted", HttpStatus.CONFLICT);
        }

        if (Cluster.STATUS_DELETED.equals(cluster.getStatus())) {
            throw new ApiException("Cluster is already deleted", HttpStatus.CONFLICT);
        }

        cluster.setStatus(Cluster.STATUS_DELETING);
        clusterRepository.save(cluster);

        String ownerEmail = cluster.getUser() != null ? cluster.getUser().getEmail() : "unknown";
        log.info("Admin {} deleted cluster: {} (owned by {})",
                admin.getEmail(), cluster.getSlug(), ownerEmail);

        auditLogService.log(AuditLog.CLUSTER_DELETED, admin, "cluster", cluster.getId(),
                Map.of("cluster_slug", cluster.getSlug(), "owner_email", ownerEmail, "admin_action", true));

        eventPublisher.publishEvent(new ClusterDeleteRequestedEvent(this, cluster));
    }

    // ==================== Admin Cluster Access ====================

    /**
     * Get cluster credentials as admin (for any cluster)
     */
    @Transactional(readOnly = true)
    public ClusterCredentialsResponse getClusterCredentialsAsAdmin(UUID clusterId, User admin) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        if (cluster.getHostname() == null || cluster.getPostgresPassword() == null) {
            throw new ApiException("Cluster credentials not yet available", HttpStatus.SERVICE_UNAVAILABLE);
        }

        log.info("Admin {} accessed credentials for cluster {} (owned by {})",
                admin.getEmail(), cluster.getSlug(),
                cluster.getUser() != null ? cluster.getUser().getEmail() : "unknown");

        auditLogService.log(AuditLog.CREDENTIALS_ACCESSED, admin, "cluster", cluster.getId(),
                Map.of("cluster_slug", cluster.getSlug(), "admin_action", true));

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
                .build();
    }

    /**
     * Get cluster backups as admin (for any cluster)
     */
    @Transactional(readOnly = true)
    public List<Backup> getClusterBackupsAsAdmin(UUID clusterId, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        List<Backup> backups = backupRepository.findByClusterOrderByCreatedAtDesc(cluster);

        if (!includeDeleted) {
            backups = backups.stream()
                    .filter(b -> !Backup.STATUS_DELETED.equals(b.getStatus()))
                    .toList();
        }

        return backups;
    }

    /**
     * Delete a backup as admin
     */
    @Transactional
    public void deleteBackupAsAdmin(UUID clusterId, UUID backupId, boolean confirm, User admin) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        Backup backup = backupRepository.findByIdAndCluster(backupId, cluster)
                .orElseThrow(() -> new ApiException("Backup not found", HttpStatus.NOT_FOUND));

        // Use the BackupService to handle the deletion (which includes dependent checks)
        backupService.deleteBackupAsAdmin(cluster, backup, confirm);

        log.info("Admin {} deleted backup {} from cluster {}",
                admin.getEmail(), backupId, cluster.getSlug());

        auditLogService.log(AuditLog.BACKUP_DELETED, admin, "backup", backupId,
                Map.of("cluster_slug", cluster.getSlug(), "admin_action", true));
    }

    /**
     * Get cluster exports as admin (for any cluster)
     */
    @Transactional(readOnly = true)
    public List<Export> getClusterExportsAsAdmin(UUID clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        return exportRepository.findByClusterOrderByCreatedAtDesc(cluster);
    }

    /**
     * Delete an export as admin
     */
    @Transactional
    public void deleteExportAsAdmin(UUID clusterId, UUID exportId, User admin) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        Export export = exportRepository.findByIdAndCluster(exportId, cluster)
                .orElseThrow(() -> new ApiException("Export not found", HttpStatus.NOT_FOUND));

        // Use the ExportService to handle deletion
        exportService.deleteExportAsAdmin(cluster, export);

        log.info("Admin {} deleted export {} from cluster {}",
                admin.getEmail(), exportId, cluster.getSlug());

        auditLogService.log(AuditLog.EXPORT_DELETED, admin, "export", exportId,
                Map.of("cluster_slug", cluster.getSlug(), "admin_action", true));
    }

    /**
     * Get cluster metrics as admin (for any cluster)
     */
    @Transactional(readOnly = true)
    public ClusterMetricsResponse getClusterMetricsAsAdmin(UUID clusterId, String range) {
        Cluster cluster = clusterRepository.findByIdWithUserAndNodes(clusterId)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        return metricsService.getClusterMetricsAsAdmin(cluster, range);
    }

    /**
     * Get cluster health as admin (for any cluster)
     */
    @Transactional
    public ClusterHealthResponse getClusterHealthAsAdmin(UUID clusterId) {
        Cluster cluster = clusterRepository.findByIdWithUserAndNodes(clusterId)
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
        List<ClusterHealthResponse.NodeHealth> nodeHealths = new java.util.ArrayList<>();
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
                String output = patroniService.getPatroniStatus(node);
                if (output != null) {
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
        if (leaderNode != null && nodeHealths.stream().allMatch(ClusterHealthResponse.NodeHealth::isReachable)) {
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
                        .build())
                .nodes(nodeHealths)
                .build();
    }
}
