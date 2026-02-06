package com.pgcluster.api.service;

import com.pgcluster.api.client.CloudflareClient;
import com.pgcluster.api.client.HetznerClient;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.RestoreJob;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.RestoreJobRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import com.pgcluster.api.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for provisioning and managing VPS infrastructure.
 * Handles server creation on Hetzner Cloud, DNS management via Cloudflare,
 * container deployment, and cluster configuration including Patroni, etcd,
 * PgBouncer, and pgBackRest setup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private final HetznerClient hetznerClient;
    private final CloudflareClient cloudflareClient;
    private final SshService sshService;
    private final ClusterRepository clusterRepository;
    private final VpsNodeRepository vpsNodeRepository;
    private final RestoreJobRepository restoreJobRepository;
    private final PgBackRestService pgBackRestService;
    private final HostKeyVerifier hostKeyVerifier;
    private final PatroniService patroniService;
    private final ClusterProgressService clusterProgressService;

    @Value("${cluster.base-domain}")
    private String baseDomain;

    @Value("${s3.endpoint:}")
    private String s3Endpoint;

    @Value("${s3.access-key:}")
    private String s3AccessKey;

    @Value("${s3.secret-key:}")
    private String s3SecretKey;

    @Value("${s3.bucket:pgcluster-backups}")
    private String s3Bucket;

    @Value("${backup.enabled:false}")
    private boolean backupEnabled;

    @Value("${restore.timeout-minutes:30}")
    private int restoreTimeoutMinutes;

    /**
     * Create ALL servers synchronously for a cluster.
     * This blocks until all servers are created successfully.
     * If ANY server fails, all previously created servers are rolled back and an exception is thrown.
     *
     * @param cluster The cluster to create servers for
     * @return List of created VpsNodes
     * @throws RuntimeException if any server creation fails (after rollback)
     */
    @Transactional
    public List<VpsNode> createAllServersSync(Cluster cluster) {
        List<VpsNode> createdNodes = new ArrayList<>();
        String snapshotId = hetznerClient.getSnapshotId();
        List<String> nodeRegions = cluster.getNodeRegions();

        log.info("Creating all {} servers synchronously for cluster: {}", cluster.getNodeCount(), cluster.getSlug());

        for (int i = 0; i < cluster.getNodeCount(); i++) {
            String nodeName = String.format("%s-node-%d", cluster.getSlug(), i + 1);
            String nodeLocation = nodeRegions != null && i < nodeRegions.size()
                    ? nodeRegions.get(i)
                    : cluster.getRegion();

            log.info("Creating server {}/{}: {} in {}", i + 1, cluster.getNodeCount(), nodeName, nodeLocation);

            // Create VPS node entity
            VpsNode node = VpsNode.builder()
                    .cluster(cluster)
                    .name(nodeName)
                    .serverType(cluster.getNodeSize())
                    .location(nodeLocation)
                    .status(VpsNode.STATUS_CREATING)
                    .role(i == 0 ? "leader" : "replica")
                    .build();
            node = vpsNodeRepository.save(node);

            try {
                // Create Hetzner server
                HetznerClient.CreateServerRequest request = HetznerClient.CreateServerRequest.builder()
                        .name(nodeName)
                        .serverType(cluster.getNodeSize())
                        .image(snapshotId)
                        .location(nodeLocation)
                        .sshKeys(Arrays.asList(hetznerClient.getSshKeyIds()))
                        .labels(Map.of(
                                "cluster", cluster.getSlug(),
                                "managed-by", "pgcluster"
                        ));

                HetznerClient.ServerResponse server = hetznerClient.createServer(request);

                // Update node with Hetzner info
                node.setHetznerId(server.getId());
                node.setPublicIp(server.getPublicNet().getIpv4().getIp());
                node.setStatus(VpsNode.STATUS_STARTING);
                node = vpsNodeRepository.save(node);

                // Clear any cached SSH host key for this IP
                hostKeyVerifier.removeHost(node.getPublicIp());

                createdNodes.add(node);
                log.info("Server {}/{} created: {} with IP {}", i + 1, cluster.getNodeCount(), nodeName, node.getPublicIp());

            } catch (Exception e) {
                log.error("Failed to create server {}: {}", nodeName, e.getMessage());

                // Mark current node as error
                node.setStatus(VpsNode.STATUS_ERROR);
                node.setErrorMessage(e.getMessage());
                vpsNodeRepository.save(node);

                // Rollback: delete all previously created servers
                if (!createdNodes.isEmpty()) {
                    log.warn("Rolling back {} previously created servers due to failure", createdNodes.size());
                    rollbackServers(createdNodes);
                }

                throw new RuntimeException("Failed to create server " + nodeName + ": " + e.getMessage(), e);
            }
        }

        log.info("All {} servers created successfully for cluster: {}", createdNodes.size(), cluster.getSlug());
        return createdNodes;
    }

    /**
     * Rollback (delete) a list of servers from Hetzner and mark them as error in DB.
     */
    private void rollbackServers(List<VpsNode> nodes) {
        for (VpsNode node : nodes) {
            try {
                if (node.getHetznerId() != null) {
                    hetznerClient.deleteServer(node.getHetznerId());
                    log.info("Rolled back server: {} (Hetzner ID: {})", node.getName(), node.getHetznerId());
                }
                node.setStatus(VpsNode.STATUS_ERROR);
                node.setErrorMessage("Rolled back due to cluster creation failure");
                vpsNodeRepository.save(node);
            } catch (Exception rollbackEx) {
                log.error("Failed to rollback server {}: {}", node.getName(), rollbackEx.getMessage());
            }
        }
    }

    /**
     * Continue provisioning asynchronously after servers are already created.
     * Starts from SSH wait phase (Phase 2).
     */
    @Async
    public void continueProvisioningFromServers(Cluster cluster, List<VpsNode> nodes) {
        try {
            log.info("Continuing provisioning for cluster: {} (servers already created)", cluster.getSlug());

            clusterProgressService.updateStatus(cluster.getId(), Cluster.STATUS_CREATING, null);

            // Generate passwords
            String replicatorPassword = PasswordGenerator.generate(24);

            // Continue from Phase 2 (SSH wait) - servers already exist
            continueProvisioning(cluster, nodes, replicatorPassword);
        } catch (Exception e) {
            log.error("Failed to provision cluster {}: {}", cluster.getSlug(), e.getMessage(), e);
            if (!isClusterBeingDeleted(cluster.getId())) {
                clusterProgressService.updateStatus(cluster.getId(), Cluster.STATUS_ERROR, e.getMessage());
            } else {
                log.info("Cluster {} is being deleted, skipping error status update", cluster.getSlug());
            }
        }
    }

    /**
     * Provision a cluster asynchronously (legacy - creates all servers)
     */
    @Async
    public void provisionClusterAsync(Cluster cluster) {
        try {
            provisionCluster(cluster);
        } catch (Exception e) {
            log.error("Failed to provision cluster {}: {}", cluster.getSlug(), e.getMessage(), e);
            if (!isClusterBeingDeleted(cluster.getId())) {
                clusterProgressService.updateStatus(cluster.getId(), Cluster.STATUS_ERROR, e.getMessage());
            } else {
                log.info("Cluster {} is being deleted, skipping error status update", cluster.getSlug());
            }
        }
    }

    /**
     * Main provisioning logic - SSH-based with Docker containers
     */
    @Transactional
    public void provisionCluster(Cluster cluster) {
        log.info("Starting provisioning for cluster: {}", cluster.getSlug());

        clusterProgressService.updateStatus(cluster.getId(), Cluster.STATUS_CREATING, null);

        // Generate passwords
        String replicatorPassword = PasswordGenerator.generate(24);

        // Phase 1: Create VPS nodes with snapshot
        log.info("Phase 1: Creating VPS nodes...");
        clusterProgressService.updateProgress(cluster.getId(), Cluster.STEP_CREATING_SERVERS, 1);
        List<VpsNode> nodes = createVpsNodes(cluster);

        continueProvisioning(cluster, nodes, replicatorPassword);
    }

    /**
     * Continue provisioning from Phase 2 onwards
     */
    private void continueProvisioning(Cluster cluster, List<VpsNode> nodes, String replicatorPassword) {

        // Check if cluster was deleted during node creation
        if (isClusterBeingDeleted(cluster.getId())) {
            log.warn("Cluster {} marked for deletion during provisioning, aborting", cluster.getSlug());
            cleanupNodesOnAbort(nodes);
            return;
        }

        // Clear any stale SSH host keys for these IPs (handles IP recycling)
        for (VpsNode node : nodes) {
            sshService.removeHostKeyTrust(node.getPublicIp());
        }

        // Phase 2: Wait for SSH to be available
        log.info("Phase 2: Waiting for SSH on all nodes...");
        clusterProgressService.updateProgress(cluster.getId(), Cluster.STEP_WAITING_SSH, 2);
        waitForSsh(nodes, cluster.getId());

        // Check if cluster was deleted during SSH wait
        if (isClusterBeingDeleted(cluster.getId())) {
            log.warn("Cluster {} marked for deletion during provisioning, aborting", cluster.getSlug());
            cleanupNodesOnAbort(nodes);
            return;
        }

        // Phase 3: Build cluster configuration strings
        log.info("Phase 3: Building cluster configuration...");
        clusterProgressService.updateProgress(cluster.getId(), Cluster.STEP_BUILDING_CONFIG, 3);
        String etcdCluster = nodes.stream()
                .map(n -> n.getName() + "=http://" + n.getPublicIp() + ":2380")
                .collect(Collectors.joining(","));

        String etcdHosts = nodes.stream()
                .map(n -> n.getPublicIp() + ":2379")
                .collect(Collectors.joining(","));

        // Phase 4a: Upload configs to all nodes
        log.info("Phase 4a: Uploading configs to all nodes...");
        clusterProgressService.updateProgress(cluster.getId(), Cluster.STEP_STARTING_CONTAINERS, 4);
        for (VpsNode node : nodes) {
            uploadNodeConfig(cluster, node, etcdCluster, etcdHosts, replicatorPassword);
        }

        // Phase 4b: Start etcd on all nodes (must be running before health checks pass)
        log.info("Phase 4b: Starting etcd on all nodes...");
        for (VpsNode node : nodes) {
            startEtcdContainer(node);
        }

        // Phase 4c: Wait for etcd cluster to form
        log.info("Phase 4c: Waiting for etcd cluster...");
        waitForEtcdCluster(nodes);

        // Check if cluster was deleted during etcd setup
        if (isClusterBeingDeleted(cluster.getId())) {
            log.warn("Cluster {} marked for deletion during provisioning, aborting", cluster.getSlug());
            cleanupNodesOnAbort(nodes);
            return;
        }

        // Phase 4d: Start remaining containers (patroni, exporters)
        log.info("Phase 4d: Starting remaining containers on all nodes...");
        for (VpsNode node : nodes) {
            startRemainingContainers(node);
        }

        // Phase 5: Wait for Patroni cluster to be healthy
        log.info("Phase 5: Waiting for Patroni cluster to be healthy...");
        clusterProgressService.updateProgress(cluster.getId(), Cluster.STEP_ELECTING_LEADER, 5);
        waitForPatroniCluster(nodes, cluster.getSlug());

        // Check if cluster was deleted during Patroni setup
        if (isClusterBeingDeleted(cluster.getId())) {
            log.warn("Cluster {} marked for deletion during provisioning, aborting", cluster.getSlug());
            cleanupNodesOnAbort(nodes);
            return;
        }

        // Phase 5b: Initialize pgBackRest if backup is enabled
        if (backupEnabled && s3Endpoint != null && !s3Endpoint.isBlank()) {
            log.info("Phase 5b: Initializing pgBackRest...");
            VpsNode leaderNode = nodes.stream()
                    .filter(patroniService::isLeaderNode)
                    .findFirst()
                    .orElse(nodes.get(0));
            try {
                // Apply archive settings via Patroni API
                applyArchiveSettings(leaderNode, cluster.getSlug());
                log.info("Archive settings applied for cluster {}", cluster.getSlug());

                // Create pgBackRest stanza
                pgBackRestService.createStanza(cluster, leaderNode);
                log.info("pgBackRest stanza initialized for cluster {}", cluster.getSlug());
            } catch (Exception e) {
                log.warn("Failed to initialize pgBackRest (non-critical): {}", e.getMessage());
                // Non-critical error - cluster can still run without backups
            }
        }

        // Phase 6: Create DNS record pointing to leader
        log.info("Phase 6: Creating DNS record...");
        clusterProgressService.updateProgress(cluster.getId(), Cluster.STEP_CREATING_DNS, 6);
        String leaderIp = patroniService.findLeaderIp(nodes);
        createDnsRecord(cluster.getSlug(), leaderIp);

        // Update cluster status
        cluster.setHostname(cluster.getSlug() + "." + baseDomain);
        cluster.setStatus(Cluster.STATUS_RUNNING);
        clusterRepository.save(cluster);

        log.info("Cluster {} provisioned successfully in {} nodes", cluster.getSlug(), nodes.size());
    }

    /**
     * Create VPS nodes for the cluster using snapshot
     */
    private List<VpsNode> createVpsNodes(Cluster cluster) {
        List<VpsNode> nodes = new ArrayList<>();
        String snapshotId = hetznerClient.getSnapshotId();
        List<String> nodeRegions = cluster.getNodeRegions();

        log.info("Using image/snapshot: {}", snapshotId);
        log.info("Node regions: {}", nodeRegions);

        for (int i = 0; i < cluster.getNodeCount(); i++) {
            String nodeName = String.format("%s-node-%d", cluster.getSlug(), i + 1);
            String nodeLocation = nodeRegions != null && i < nodeRegions.size()
                    ? nodeRegions.get(i)
                    : cluster.getRegion(); // Fallback for backward compatibility

            // Create VPS node entity
            VpsNode node = VpsNode.builder()
                    .cluster(cluster)
                    .name(nodeName)
                    .serverType(cluster.getNodeSize())
                    .location(nodeLocation)
                    .status(VpsNode.STATUS_CREATING)
                    .role(i == 0 ? "leader" : "replica")
                    .build();
            node = vpsNodeRepository.save(node);

            try {
                // Create Hetzner server with snapshot
                HetznerClient.CreateServerRequest request = HetznerClient.CreateServerRequest.builder()
                        .name(nodeName)
                        .serverType(cluster.getNodeSize())
                        .image(snapshotId)
                        .location(nodeLocation)
                        .sshKeys(Arrays.asList(hetznerClient.getSshKeyIds()))
                        .labels(Map.of(
                                "cluster", cluster.getSlug(),
                                "managed-by", "pgcluster"
                        ));

                HetznerClient.ServerResponse server = hetznerClient.createServer(request);

                // Update node with Hetzner info
                node.setHetznerId(server.getId());
                node.setPublicIp(server.getPublicNet().getIpv4().getIp());
                node.setStatus(VpsNode.STATUS_STARTING);
                vpsNodeRepository.save(node);

                nodes.add(node);

                // Clear any cached SSH host key for this IP (IPs can be recycled)
                hostKeyVerifier.removeHost(node.getPublicIp());

                log.info("Created node {} with IP {}", nodeName, node.getPublicIp());

            } catch (Exception e) {
                log.error("Failed to create node {}: {}", nodeName, e.getMessage());
                node.setStatus(VpsNode.STATUS_ERROR);
                node.setErrorMessage(e.getMessage());
                vpsNodeRepository.save(node);

                // Rollback: delete previously created nodes
                if (!nodes.isEmpty()) {
                    log.warn("Rolling back {} previously created nodes due to failure", nodes.size());
                    for (VpsNode createdNode : nodes) {
                        try {
                            if (createdNode.getHetznerId() != null) {
                                hetznerClient.deleteServer(createdNode.getHetznerId());
                                log.info("Rolled back node: {}", createdNode.getName());
                            }
                            createdNode.setStatus(VpsNode.STATUS_ERROR);
                            createdNode.setErrorMessage("Rolled back due to cluster creation failure");
                            vpsNodeRepository.save(createdNode);
                        } catch (Exception rollbackEx) {
                            log.error("Failed to rollback node {}: {}", createdNode.getName(), rollbackEx.getMessage());
                        }
                    }
                }
                throw e;
            }
        }

        return nodes;
    }

    /**
     * Wait for SSH to be available on all nodes.
     * Checks for cluster deletion between attempts to abort early.
     */
    private void waitForSsh(List<VpsNode> nodes, UUID clusterId) {
        for (VpsNode node : nodes) {
            log.info("Waiting for SSH on {}...", node.getName());
            int maxAttempts = 60;
            boolean ready = false;
            for (int i = 0; i < maxAttempts; i++) {
                if (isClusterBeingDeleted(clusterId)) {
                    throw new RuntimeException("Cluster marked for deletion, aborting SSH wait");
                }
                if (sshService.isHostReachable(node.getPublicIp())) {
                    ready = true;
                    break;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("SSH wait interrupted for " + node.getName());
                }
            }
            if (!ready) {
                throw new RuntimeException("SSH not available on " + node.getName() + " after timeout");
            }
            log.info("SSH ready on {}", node.getName());
        }
    }

    /**
     * Upload configuration files to a node (without starting containers)
     */
    private void uploadNodeConfig(Cluster cluster, VpsNode node, String etcdCluster,
                                  String etcdHosts, String replicatorPassword) {
        log.info("Uploading config to node {}...", node.getName());

        // Generate .env file with sensitive credentials (chmod 600)
        String envFile = generateEnvFile(
                cluster.getPostgresPassword(),
                replicatorPassword
        );

        // Generate docker-compose.yml (references .env file, no inline passwords)
        String dockerCompose = generateDockerCompose(
                cluster.getSlug(),
                node.getName(),
                node.getPublicIp(),
                etcdCluster,
                cluster.getPostgresVersion()
        );

        // Generate patroni.yml
        String patroniYml = generatePatroniConfig(
                cluster.getId().toString(),
                cluster.getSlug(),
                node.getName(),
                node.getPublicIp(),
                etcdHosts,
                cluster.getPostgresPassword(),
                replicatorPassword,
                cluster.getNodeSize(),
                cluster.getPostgresVersion()
        );

        // Ensure config directory exists
        sshService.executeCommand(
                node.getPublicIp(),
                "mkdir -p /opt/pgcluster"
        );

        // Upload .env file
        sshService.uploadContent(
                node.getPublicIp(),
                envFile,
                "/opt/pgcluster/.env"
        );

        // Set restrictive permissions on .env file (owner read only)
        sshService.executeCommand(
                node.getPublicIp(),
                "chmod 600 /opt/pgcluster/.env"
        );

        // Upload docker-compose.yml
        sshService.uploadContent(
                node.getPublicIp(),
                dockerCompose,
                "/opt/pgcluster/docker-compose.yml"
        );

        // Upload patroni.yml
        sshService.uploadContent(
                node.getPublicIp(),
                patroniYml,
                "/opt/pgcluster/patroni.yml"
        );

        // Set permissions on patroni.yml - needs to be readable by postgres user (UID 999) in container
        sshService.executeCommand(
                node.getPublicIp(),
                "chmod 644 /opt/pgcluster/patroni.yml"
        );

        // Ensure data directories exist with correct permissions
        // PostgreSQL requires 700 permissions on data directory
        sshService.executeCommand(
                node.getPublicIp(),
                "mkdir -p /data/postgresql /data/etcd && chmod 700 /data/postgresql && chown -R 999:999 /data/postgresql"
        );

        // Generate and upload PgBouncer config
        String pgbouncerConfig = generatePgBouncerConfig(cluster.getNodeSize());
        sshService.uploadContent(
                node.getPublicIp(),
                pgbouncerConfig,
                "/opt/pgcluster/pgbouncer.ini"
        );

        // Generate and upload PgBouncer userlist
        String pgbouncerUserlist = generatePgBouncerUserlist(cluster.getPostgresPassword());
        sshService.uploadContent(
                node.getPublicIp(),
                pgbouncerUserlist,
                "/opt/pgcluster/userlist.txt"
        );

        // Set readable permissions on userlist.txt (contains MD5 hash, read by pgbouncer user)
        sshService.executeCommand(
                node.getPublicIp(),
                "chmod 644 /opt/pgcluster/userlist.txt"
        );

        // Upload pgBackRest config if backup is enabled
        if (backupEnabled && s3Endpoint != null && !s3Endpoint.isBlank()) {
            pgBackRestService.uploadConfig(cluster, node);
        }

        log.info("Node {} config uploaded with secure permissions", node.getName());
    }

    /**
     * Generate .env file with sensitive credentials
     */
    private String generateEnvFile(String postgresPassword, String replicatorPassword) {
        return """
            # PostgreSQL Cluster Credentials
            # This file contains sensitive data - do not share or commit
            POSTGRES_PASSWORD=%s
            REPLICATOR_PASSWORD=%s
            """.formatted(postgresPassword, replicatorPassword);
    }

    /**
     * Start only etcd container on a node
     */
    private void startEtcdContainer(VpsNode node) {
        log.info("Starting etcd on {}...", node.getName());

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                "cd /opt/pgcluster && docker compose up -d etcd"
        );

        if (!result.isSuccess()) {
            log.error("Failed to start etcd on {}: {}", node.getName(), result.getStderr());
            throw new RuntimeException("Failed to start etcd on " + node.getName());
        }

        log.info("Etcd started on {}", node.getName());
    }

    /**
     * Wait for etcd cluster to form (needs quorum)
     */
    private void waitForEtcdCluster(List<VpsNode> nodes) {
        int maxAttempts = 30; // 30 attempts * 2 seconds = 60 seconds max
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(2000);
                attempt++;

                // Check etcd health on first node
                SshService.CommandResult result = sshService.executeCommand(
                        nodes.get(0).getPublicIp(),
                        "docker exec etcd etcdctl endpoint health --cluster",
                        10000
                );

                if (result.isSuccess() && result.getStdout().contains("is healthy")) {
                    log.info("Etcd cluster is healthy after {} attempts", attempt);
                    return;
                }

                log.debug("Waiting for etcd cluster... attempt {}/{}", attempt, maxAttempts);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for etcd cluster");
            }
        }

        throw new RuntimeException("Etcd cluster did not become healthy within timeout");
    }

    /**
     * Start remaining containers (patroni, exporters) on a node
     */
    private void startRemainingContainers(VpsNode node) {
        log.info("Starting remaining containers on {}...", node.getName());

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                "cd /opt/pgcluster && docker compose up -d"
        );

        if (!result.isSuccess()) {
            log.error("Failed to start containers on {}: {}", node.getName(), result.getStderr());
            throw new RuntimeException("Failed to start containers on " + node.getName());
        }

        log.info("All containers started on {}", node.getName());
    }

    /**
     * Start core containers for restore (patroni and node-exporter only, without dependent services).
     * This avoids health check timeout issues during restore.
     */
    private void startCoreContainersForRestore(VpsNode node) {
        log.info("Starting core containers (patroni, node-exporter) on {} for restore...", node.getName());

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                "cd /opt/pgcluster && docker compose up -d patroni node-exporter",
                300000  // 5 minutes timeout for container pull/start
        );

        if (!result.isSuccess()) {
            log.error("Failed to start core containers on {}: {}", node.getName(), result.getStderr());
            throw new RuntimeException("Failed to start core containers on " + node.getName());
        }

        log.info("Core containers started on {}", node.getName());
    }

    /**
     * Start dependent containers (pgbouncer, postgres-exporter) after patroni is healthy.
     * Waits for Docker health check to pass before attempting to start.
     */
    private void startDependentContainers(VpsNode node) {
        log.info("Starting dependent containers on {}...", node.getName());

        // First wait for Patroni's Docker health check to pass
        // This is different from waitForPatroniRestore which checks /patroni endpoint
        waitForPatroniDockerHealthy(node, 60);

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                "cd /opt/pgcluster && docker compose up -d pgbouncer postgres-exporter",
                120000  // 2 minutes timeout
        );

        if (!result.isSuccess()) {
            // Non-critical: containers have restart policy and will eventually start
            log.warn("Dependent containers didn't start immediately on {} (will retry automatically): {}",
                    node.getName(), result.getStderr());
        } else {
            log.info("Dependent containers started on {}", node.getName());
        }
    }

    /**
     * Wait for Patroni container to be healthy according to Docker health check.
     * Docker Compose depends_on: condition: service_healthy requires this.
     */
    private void waitForPatroniDockerHealthy(VpsNode node, int timeoutSeconds) {
        int maxAttempts = timeoutSeconds / 2;
        int attempt = 0;

        log.info("Waiting for Patroni Docker health check on {}...", node.getName());

        while (attempt < maxAttempts) {
            try {
                SshService.CommandResult result = sshService.executeCommand(
                        node.getPublicIp(),
                        "docker inspect --format='{{.State.Health.Status}}' patroni",
                        10000
                );

                if (result.isSuccess()) {
                    String status = result.getStdout().trim();
                    if ("healthy".equals(status)) {
                        log.info("Patroni Docker health check passed after {} attempts", attempt + 1);
                        return;
                    }
                    log.debug("Patroni health status: {} (attempt {}/{})", status, attempt + 1, maxAttempts);
                }

                Thread.sleep(2000);
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Patroni health check");
            }
        }

        log.warn("Patroni Docker health check didn't pass within {}s, proceeding anyway", timeoutSeconds);
    }

    /**
     * Generate docker-compose.yml from template.
     * Passwords are loaded from .env file for security.
     */
    private String generateDockerCompose(String clusterSlug, String nodeName,
                                         String nodeIp, String etcdCluster,
                                         String postgresVersion) {
        return """
            services:
              etcd:
                image: quay.io/coreos/etcd:v3.5.11
                container_name: etcd
                restart: unless-stopped
                network_mode: host
                volumes:
                  - /data/etcd:/etcd-data
                environment:
                  - ETCD_NAME=%s
                  - ETCD_DATA_DIR=/etcd-data
                  - ETCD_LISTEN_PEER_URLS=http://%s:2380
                  - ETCD_LISTEN_CLIENT_URLS=http://%s:2379,http://127.0.0.1:2379
                  - ETCD_INITIAL_ADVERTISE_PEER_URLS=http://%s:2380
                  - ETCD_ADVERTISE_CLIENT_URLS=http://%s:2379
                  - ETCD_INITIAL_CLUSTER=%s
                  - ETCD_INITIAL_CLUSTER_STATE=new
                  - ETCD_INITIAL_CLUSTER_TOKEN=%s-etcd
                healthcheck:
                  test: ["CMD", "etcdctl", "endpoint", "health"]
                  interval: 10s
                  timeout: 5s
                  retries: 3

              patroni:
                image: denysd1/patroni:%s
                container_name: patroni
                restart: unless-stopped
                network_mode: host
                depends_on:
                  etcd:
                    condition: service_healthy
                volumes:
                  - /data/postgresql:/var/lib/postgresql/data
                  - /opt/pgcluster/patroni.yml:/etc/patroni/patroni.yml:ro
                  - /etc/pgbackrest/pgbackrest.conf:/etc/pgbackrest/pgbackrest.conf:ro
                  - /var/log/pgbackrest:/var/log/pgbackrest
                  - /var/spool/pgbackrest:/var/spool/pgbackrest
                environment:
                  - PATRONI_NAME=%s
                  - PATRONI_RESTAPI_CONNECT_ADDRESS=%s:8008
                  - PATRONI_POSTGRESQL_CONNECT_ADDRESS=%s:5432
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:8008/health"]
                  interval: 10s
                  timeout: 5s
                  retries: 3

              node-exporter:
                image: prom/node-exporter:v1.7.0
                container_name: node-exporter
                restart: unless-stopped
                network_mode: host
                pid: host
                volumes:
                  - /:/host:ro,rslave
                command:
                  - '--path.rootfs=/host'
                  - '--web.listen-address=:9100'

              postgres-exporter:
                image: prometheuscommunity/postgres-exporter:v0.15.0
                container_name: postgres-exporter
                restart: unless-stopped
                network_mode: host
                env_file:
                  - .env
                depends_on:
                  patroni:
                    condition: service_healthy
                environment:
                  - DATA_SOURCE_NAME=postgresql://postgres:${POSTGRES_PASSWORD}@127.0.0.1:5432/postgres?sslmode=disable
                command:
                  - '--web.listen-address=:9187'

              pgbouncer:
                image: edoburu/pgbouncer:v1.23.1-p3
                container_name: pgbouncer
                restart: unless-stopped
                network_mode: host
                depends_on:
                  patroni:
                    condition: service_healthy
                volumes:
                  - /opt/pgcluster/pgbouncer.ini:/etc/pgbouncer/pgbouncer.ini:ro
                  - /opt/pgcluster/userlist.txt:/etc/pgbouncer/userlist.txt:ro
                healthcheck:
                  test: ["CMD", "pg_isready", "-h", "127.0.0.1", "-p", "6432"]
                  interval: 10s
                  timeout: 5s
                  retries: 3
            """.formatted(
                nodeName,           // ETCD_NAME
                nodeIp,             // ETCD_LISTEN_PEER_URLS
                nodeIp,             // ETCD_LISTEN_CLIENT_URLS
                nodeIp,             // ETCD_INITIAL_ADVERTISE_PEER_URLS
                nodeIp,             // ETCD_ADVERTISE_CLIENT_URLS
                etcdCluster,        // ETCD_INITIAL_CLUSTER
                clusterSlug,        // ETCD_INITIAL_CLUSTER_TOKEN
                postgresVersion,    // patroni image version
                nodeName,           // PATRONI_NAME
                nodeIp,             // PATRONI_RESTAPI_CONNECT_ADDRESS
                nodeIp              // PATRONI_POSTGRESQL_CONNECT_ADDRESS
        );
    }

    /**
     * Generate patroni.yml configuration
     */
    private String generatePatroniConfig(String clusterId, String clusterSlug, String nodeName,
                                         String nodeIp, String etcdHosts,
                                         String postgresPassword, String replicatorPassword,
                                         String nodeSize, String postgresVersion) {
        // Calculate memory settings based on node size
        MemorySettings mem = getMemorySettings(nodeSize);

        // Build WAL archiving configuration using pgBackRest if backup is enabled
        String archiveParams = "";
        String recoveryConf = "";
        if (backupEnabled && s3Endpoint != null && !s3Endpoint.isBlank()) {
            archiveParams = """
                    archive_mode: "on"
                    archive_timeout: 60
                    archive_command: 'pgbackrest --stanza=%s archive-push %%p'
                """.formatted(clusterSlug);

            recoveryConf = """

              recovery_conf:
                restore_command: 'pgbackrest --stanza=%s archive-get %%f %%p'
            """.formatted(clusterSlug);
        }

        String baseConfig = """
            scope: %s
            namespace: /pgcluster/
            name: %s

            restapi:
              listen: 0.0.0.0:8008
              connect_address: %s:8008

            etcd3:
              hosts: %s

            bootstrap:
              dcs:
                ttl: 30
                loop_wait: 10
                retry_timeout: 10
                maximum_lag_on_failover: 1048576
                postgresql:
                  use_pg_rewind: true
                  use_slots: true
                  parameters:
                    max_connections: 100
                    shared_buffers: %s
                    effective_cache_size: %s
                    work_mem: 4MB
                    maintenance_work_mem: 64MB
                    wal_level: replica
                    hot_standby: on
                    max_wal_senders: 10
                    max_replication_slots: 10
                    wal_keep_size: 128MB
                    logging_collector: off
                    log_destination: stderr
            %s
              initdb:
                - encoding: UTF8
                - data-checksums

              pg_hba:
                - host replication replicator 0.0.0.0/0 md5
                - host all all 0.0.0.0/0 md5

              users:
                postgres:
                  password: %s
                  options:
                    - superuser
                replicator:
                  password: %s
                  options:
                    - replication

            postgresql:
              listen: 0.0.0.0:5432
              connect_address: %s:5432
              data_dir: /var/lib/postgresql/data
              bin_dir: /usr/lib/postgresql/%s/bin
              pgpass: /tmp/pgpass
              authentication:
                replication:
                  username: replicator
                  password: %s
                superuser:
                  username: postgres
                  password: %s
            %s
            tags:
              nofailover: false
              noloadbalance: false
              clonefrom: false
              nosync: false
            """.formatted(
                clusterSlug,        // scope
                nodeName,           // name
                nodeIp,             // connect_address
                etcdHosts,          // etcd hosts
                mem.sharedBuffers,  // shared_buffers
                mem.effectiveCache, // effective_cache_size
                archiveParams,      // archive parameters
                postgresPassword,   // postgres password
                replicatorPassword, // replicator password
                nodeIp,             // postgresql connect_address
                postgresVersion,    // bin_dir version
                replicatorPassword, // replication password
                postgresPassword,   // superuser password
                recoveryConf        // recovery configuration
        );

        return baseConfig;
    }

    /**
     * Get memory settings based on node size
     */
    private MemorySettings getMemorySettings(String nodeSize) {
        return switch (nodeSize) {
            case "cx23" -> new MemorySettings("512MB", "1536MB");    // 4GB RAM
            case "cx33" -> new MemorySettings("2GB", "6GB");         // 8GB RAM
            case "cx43" -> new MemorySettings("4GB", "12GB");        // 16GB RAM
            case "cx53" -> new MemorySettings("8GB", "24GB");        // 32GB RAM
            default -> new MemorySettings("256MB", "768MB");        // Default for smaller
        };
    }

    private record MemorySettings(String sharedBuffers, String effectiveCache) {}

    private record PgBouncerSettings(int defaultPoolSize, int maxClientConn, int reservePoolSize) {}

    private PgBouncerSettings getPgBouncerSettings(String nodeSize) {
        return switch (nodeSize) {
            case "cx23" -> new PgBouncerSettings(20, 400, 5);    // 4GB RAM
            case "cx33" -> new PgBouncerSettings(40, 600, 10);   // 8GB RAM
            case "cx43" -> new PgBouncerSettings(80, 1000, 20);  // 16GB RAM
            case "cx53" -> new PgBouncerSettings(150, 2000, 40); // 32GB RAM
            default -> new PgBouncerSettings(15, 300, 3);
        };
    }

    /**
     * Generate PgBouncer configuration file
     */
    private String generatePgBouncerConfig(String nodeSize) {
        PgBouncerSettings pool = getPgBouncerSettings(nodeSize);
        return """
            [databases]
            * = host=127.0.0.1 port=5432

            [pgbouncer]
            listen_addr = 0.0.0.0
            listen_port = 6432
            auth_type = md5
            auth_file = /etc/pgbouncer/userlist.txt
            admin_users = postgres
            pool_mode = transaction
            default_pool_size = %d
            max_client_conn = %d
            reserve_pool_size = %d
            reserve_pool_timeout = 5
            server_reset_query = DISCARD ALL
            ignore_startup_parameters = extra_float_digits
            """.formatted(pool.defaultPoolSize, pool.maxClientConn, pool.reservePoolSize);
    }

    /**
     * Generate PgBouncer userlist.txt file with MD5 password hash
     */
    private String generatePgBouncerUserlist(String postgresPassword) {
        // MD5 format: "md5" + md5(password + username)
        String md5Hash = md5Hex(postgresPassword + "postgres");
        return "\"postgres\" \"md5%s\"\n".formatted(md5Hash);
    }

    /**
     * Compute MD5 hash of a string and return hex representation
     */
    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Wait for Patroni cluster to elect a leader
     */
    private void waitForPatroniCluster(List<VpsNode> nodes, String clusterSlug) {
        int maxAttempts = 60; // 5 minutes
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(5000);
                attempt++;

                // Check if any node is the leader
                for (VpsNode node : nodes) {
                    SshService.CommandResult result = sshService.executeCommand(
                            node.getPublicIp(),
                            "curl -s http://localhost:8008/patroni",
                            10000
                    );

                    if (result.isSuccess() && patroniService.isLeaderRole(result.getStdout())) {
                        log.info("Patroni cluster {} has elected leader on {}",
                                clusterSlug, node.getName());
                        return;
                    }
                }

                log.debug("Waiting for Patroni leader election... attempt {}/{}", attempt, maxAttempts);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Patroni cluster");
            } catch (Exception e) {
                log.warn("Error checking Patroni status: {}", e.getMessage());
            }
        }

        throw new RuntimeException("Patroni cluster did not elect a leader within timeout");
    }

    /**
     * Apply archive settings via Patroni API and restart PostgreSQL.
     * The bootstrap config in patroni.yml doesn't update running clusters,
     * so we need to apply settings via the Patroni REST API.
     */
    private void applyArchiveSettings(VpsNode leaderNode, String stanzaName) {
        // Validate stanzaName to prevent shell injection
        if (stanzaName == null || !stanzaName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid stanza name: " + stanzaName);
        }
        log.info("Applying archive settings via Patroni API for stanza {}", stanzaName);

        // Update Patroni config via PATCH /config
        String configJson = String.format(
                "{\"postgresql\": {\"parameters\": {\"archive_mode\": \"on\", " +
                "\"archive_command\": \"pgbackrest --stanza=%s archive-push %%p\", " +
                "\"archive_timeout\": 60}}}",
                stanzaName
        );

        String patchCommand = String.format(
                "curl -s -X PATCH http://localhost:8008/config -H 'Content-Type: application/json' -d '%s'",
                configJson
        );

        SshService.CommandResult patchResult = sshService.executeCommand(
                leaderNode.getPublicIp(),
                patchCommand,
                30000
        );

        if (!patchResult.isSuccess()) {
            throw new RuntimeException("Failed to update Patroni config: " + patchResult.getStderr());
        }

        log.info("Patroni config updated, restarting PostgreSQL...");

        // Restart PostgreSQL to apply archive_mode (requires restart)
        SshService.CommandResult restartResult = sshService.executeCommand(
                leaderNode.getPublicIp(),
                "curl -s -X POST http://localhost:8008/restart -d '{}'",
                60000
        );

        if (!restartResult.isSuccess()) {
            throw new RuntimeException("Failed to restart PostgreSQL: " + restartResult.getStderr());
        }

        // Wait for PostgreSQL to be ready (polling instead of fixed sleep)
        waitForPostgresReady(leaderNode, 30);

        log.info("Archive settings applied and PostgreSQL restarted");
    }

    /**
     * Wait for PostgreSQL to be ready after restart by polling Patroni API.
     *
     * @param node The node to check
     * @param maxWaitSeconds Maximum time to wait
     */
    private void waitForPostgresReady(VpsNode node, int maxWaitSeconds) {
        int pollIntervalMs = 2000;
        int maxAttempts = (maxWaitSeconds * 1000) / pollIntervalMs;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                SshService.CommandResult result = sshService.executeCommand(
                        node.getPublicIp(),
                        "curl -s http://localhost:8008/patroni",
                        10000
                );

                if (result.isSuccess() && patroniService.isLeaderRole(result.getStdout())) {
                    log.info("PostgreSQL is ready after {} seconds", (attempt * pollIntervalMs) / 1000);
                    return;
                }

                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for PostgreSQL", e);
            } catch (Exception e) {
                log.debug("Waiting for PostgreSQL... attempt {}/{}: {}", attempt + 1, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for PostgreSQL", ie);
                }
            }
        }

        log.warn("PostgreSQL did not become ready within {} seconds, continuing anyway", maxWaitSeconds);
    }

    /**
     * Create DNS record for cluster
     */
    private void createDnsRecord(String slug, String ip) {
        String hostname = slug + "." + baseDomain;

        try {
            // Check if record exists
            CloudflareClient.DnsRecord existing = cloudflareClient.findDnsRecord(hostname);

            if (existing != null) {
                // Update existing record
                cloudflareClient.updateDnsRecord(existing.getId(), hostname, ip, false);
            } else {
                // Create new record
                cloudflareClient.createDnsRecord(hostname, ip, false);
            }

            log.info("DNS record created/updated: {} -> {}", hostname, ip);

        } catch (Exception e) {
            log.error("Failed to create DNS record: {}", e.getMessage());
            // Don't fail provisioning for DNS issues
        }
    }

    /**
     * Delete a cluster asynchronously
     */
    @Async
    public void deleteClusterAsync(Cluster cluster) {
        try {
            deleteCluster(cluster);
        } catch (Exception e) {
            log.error("Failed to delete cluster {}: {}", cluster.getSlug(), e.getMessage(), e);
            clusterProgressService.updateStatus(cluster.getId(), Cluster.STATUS_ERROR, "Deletion failed: " + e.getMessage());
        }
    }

    /**
     * Delete cluster and all its resources
     */
    @Transactional
    public void deleteCluster(Cluster cluster) {
        log.info("Deleting cluster: {}", cluster.getSlug());

        // Cancel any pending/in-progress restore jobs involving this cluster
        cancelOrphanedRestoreJobs(cluster);

        // Collect Hetzner IDs from database
        Set<Long> deletedServerIds = new HashSet<>();

        // Delete VPS nodes from database
        List<VpsNode> nodes = vpsNodeRepository.findByCluster(cluster);
        for (VpsNode node : nodes) {
            if (node.getHetznerId() != null) {
                try {
                    hetznerClient.deleteServer(node.getHetznerId());
                    deletedServerIds.add(node.getHetznerId());
                    log.info("Deleted Hetzner server: {}", node.getHetznerId());
                } catch (Exception e) {
                    log.warn("Failed to delete Hetzner server {}: {}", node.getHetznerId(), e.getMessage());
                }
            }
        }

        // Also query Hetzner directly by label to catch any servers not yet in database
        // This handles the race condition where server was created but hetznerId not yet saved
        try {
            List<HetznerClient.ServerResponse> labeledServers =
                    hetznerClient.listServersByLabel("cluster=" + cluster.getSlug());
            for (HetznerClient.ServerResponse server : labeledServers) {
                if (!deletedServerIds.contains(server.getId())) {
                    try {
                        hetznerClient.deleteServer(server.getId());
                        log.info("Deleted orphaned Hetzner server found by label: {} ({})",
                                server.getName(), server.getId());
                    } catch (Exception e) {
                        log.warn("Failed to delete orphaned server {}: {}", server.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query Hetzner for orphaned servers: {}", e.getMessage());
        }

        // Delete DNS record
        try {
            String hostname = cluster.getSlug() + "." + baseDomain;
            CloudflareClient.DnsRecord record = cloudflareClient.findDnsRecord(hostname);
            if (record != null) {
                cloudflareClient.deleteDnsRecord(record.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to delete DNS record: {}", e.getMessage());
        }

        // Delete cluster from database
        cluster.setStatus(Cluster.STATUS_DELETED);
        clusterRepository.save(cluster);

        log.info("Cluster {} deleted", cluster.getSlug());
    }

    /**
     * Check if the cluster is marked for deletion (DELETING or DELETED status).
     * Used to abort provisioning early if user deleted the cluster mid-creation.
     */
    private boolean isClusterBeingDeleted(UUID clusterId) {
        return clusterRepository.findById(clusterId)
                .map(c -> Cluster.STATUS_DELETING.equals(c.getStatus()) ||
                          Cluster.STATUS_DELETED.equals(c.getStatus()))
                .orElse(true); // If cluster doesn't exist, treat as deleted
    }

    /**
     * Clean up Hetzner servers when provisioning is aborted.
     * Called when cluster is deleted during creation.
     */
    private void cleanupNodesOnAbort(List<VpsNode> nodes) {
        log.info("Cleaning up {} nodes due to provisioning abort", nodes.size());
        for (VpsNode node : nodes) {
            if (node.getHetznerId() != null) {
                try {
                    hetznerClient.deleteServer(node.getHetznerId());
                    log.info("Cleaned up aborted node: {} ({})", node.getName(), node.getHetznerId());
                } catch (Exception e) {
                    log.warn("Failed to cleanup node {}: {}", node.getName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Cancel any pending or in-progress restore jobs involving the given cluster.
     * Called during cluster deletion to prevent orphaned jobs from blocking future restores.
     */
    private void cancelOrphanedRestoreJobs(Cluster cluster) {
        List<RestoreJob> jobs = restoreJobRepository.findByCluster(cluster);
        int cancelledCount = 0;

        for (RestoreJob job : jobs) {
            if ("pending".equals(job.getStatus()) || "in_progress".equals(job.getStatus())) {
                job.setStatus(RestoreJob.STATUS_CANCELLED);
                job.setErrorMessage("Cluster was deleted");
                restoreJobRepository.save(job);
                cancelledCount++;
                log.info("Cancelled restore job {} (was {})", job.getId(), job.getStatus());
            }
        }

        if (cancelledCount > 0) {
            log.info("Cancelled {} orphaned restore job(s) for cluster {}", cancelledCount, cluster.getSlug());
        }
    }

    /**
     * Provision a new cluster by restoring from a backup.
     *
     * Key differences from normal provisioning:
     * 1. pgBackRest config points to SOURCE cluster's S3 path (for reading backup)
     * 2. Patroni bootstrap uses pgbackrest restore method instead of initdb
     * 3. After restore completes, pgBackRest is reconfigured for new cluster's path
     * 4. New stanza is created for the new cluster
     */
    @Transactional
    public void provisionClusterFromRestore(Cluster targetCluster, Cluster sourceCluster,
                                             Backup backup, Instant targetTime, RestoreJob job) {
        log.info("Starting restore provisioning: {} -> {}", sourceCluster.getSlug(), targetCluster.getSlug());

        clusterProgressService.updateStatus(targetCluster.getId(), Cluster.STATUS_CREATING, null);

        // Generate passwords for new cluster
        String replicatorPassword = PasswordGenerator.generate(24);

        // Update job progress
        updateRestoreJobProgress(job, "CREATING_SERVERS", 5);

        // Phase 1: Create VPS nodes
        log.info("Phase 1: Creating VPS nodes for restored cluster...");
        clusterProgressService.updateProgress(targetCluster.getId(), Cluster.STEP_CREATING_SERVERS, 1);
        List<VpsNode> nodes = createVpsNodes(targetCluster);

        // Clear any stale SSH host keys for these IPs (handles IP recycling)
        for (VpsNode node : nodes) {
            sshService.removeHostKeyTrust(node.getPublicIp());
        }

        // Phase 2: Wait for SSH
        log.info("Phase 2: Waiting for SSH on all nodes...");
        clusterProgressService.updateProgress(targetCluster.getId(), Cluster.STEP_WAITING_SSH, 2);
        updateRestoreJobProgress(job, "WAITING_SSH", 15);
        waitForSsh(nodes, targetCluster.getId());

        // Phase 3: Build cluster configuration
        log.info("Phase 3: Building cluster configuration...");
        clusterProgressService.updateProgress(targetCluster.getId(), Cluster.STEP_BUILDING_CONFIG, 3);
        String etcdCluster = nodes.stream()
                .map(n -> n.getName() + "=http://" + n.getPublicIp() + ":2380")
                .collect(Collectors.joining(","));
        String etcdHosts = nodes.stream()
                .map(n -> n.getPublicIp() + ":2379")
                .collect(Collectors.joining(","));

        // Phase 4a: Upload configs (restore mode - pgBackRest points to source cluster)
        log.info("Phase 4a: Uploading configs (restore mode) to all nodes...");
        clusterProgressService.updateProgress(targetCluster.getId(), Cluster.STEP_STARTING_CONTAINERS, 4);
        updateRestoreJobProgress(job, "CONFIGURING_NODES", 20);

        VpsNode firstNode = nodes.get(0);

        for (int i = 0; i < nodes.size(); i++) {
            VpsNode node = nodes.get(i);

            if (i == 0) {
                // First node: restore from backup using pgBackRest
                uploadNodeConfigForRestore(targetCluster, sourceCluster, node, etcdCluster,
                        etcdHosts, replicatorPassword, backup.getPgbackrestLabel(), targetTime);
            } else {
                // Replica nodes: will stream from restored leader
                uploadNodeConfigForReplica(targetCluster, node, etcdCluster, etcdHosts, replicatorPassword);
            }

            // Upload pgBackRest config pointing to SOURCE cluster (for reading backup)
            pgBackRestService.uploadRestoreConfig(sourceCluster, node);
        }

        // Phase 4b: Start etcd on all nodes
        log.info("Phase 4b: Starting etcd on all nodes...");
        for (VpsNode node : nodes) {
            startEtcdContainer(node);
        }

        // Phase 4c: Wait for etcd cluster
        log.info("Phase 4c: Waiting for etcd cluster...");
        waitForEtcdCluster(nodes);

        // Phase 4d: Start Patroni on first node only (restore will happen)
        log.info("Phase 4d: Starting Patroni on first node (restore mode)...");
        updateRestoreJobProgress(job, "RESTORING_DATA", 30);
        // Start only patroni and node-exporter first (without health-check dependent services)
        startCoreContainersForRestore(firstNode);

        // Phase 5: Wait for restore to complete (configurable timeout)
        log.info("Phase 5: Waiting for pgBackRest restore to complete (timeout: {} minutes)...", restoreTimeoutMinutes);
        waitForPatroniRestore(firstNode, targetCluster.getSlug(), restoreTimeoutMinutes * 60);

        // Now start the dependent services (pgbouncer, postgres-exporter)
        log.info("Phase 5: Starting dependent containers on first node...");
        startDependentContainers(firstNode);

        // Phase 5a: Start replica nodes
        log.info("Phase 5a: Starting replica nodes...");
        updateRestoreJobProgress(job, "STARTING_REPLICAS", 70);
        for (int i = 1; i < nodes.size(); i++) {
            startRemainingContainers(nodes.get(i));
        }

        // Wait for full cluster health
        log.info("Phase 5b: Waiting for cluster to be fully healthy...");
        clusterProgressService.updateProgress(targetCluster.getId(), Cluster.STEP_ELECTING_LEADER, 5);
        waitForPatroniCluster(nodes, targetCluster.getSlug());

        // Phase 5c: Reconfigure pgBackRest for NEW cluster's repository
        log.info("Phase 5c: Reconfiguring pgBackRest for new cluster...");
        updateRestoreJobProgress(job, "CONFIGURING_BACKUP", 85);
        for (VpsNode node : nodes) {
            pgBackRestService.uploadConfig(targetCluster, node);
        }

        // Phase 5d: Create new stanza for the restored cluster
        if (backupEnabled && s3Endpoint != null && !s3Endpoint.isBlank()) {
            log.info("Phase 5d: Initializing pgBackRest for new cluster...");
            VpsNode leaderNode = nodes.stream()
                    .filter(patroniService::isLeaderNode)
                    .findFirst()
                    .orElse(firstNode);

            try {
                applyArchiveSettings(leaderNode, targetCluster.getSlug());
                pgBackRestService.createStanza(targetCluster, leaderNode);
                log.info("pgBackRest stanza created for restored cluster: {}", targetCluster.getSlug());
            } catch (Exception e) {
                log.warn("Failed to initialize pgBackRest for restored cluster (non-critical): {}", e.getMessage());
            }
        }

        // Phase 6: Create DNS record
        log.info("Phase 6: Creating DNS record...");
        clusterProgressService.updateProgress(targetCluster.getId(), Cluster.STEP_CREATING_DNS, 6);
        updateRestoreJobProgress(job, "CREATING_DNS", 95);
        String leaderIp = patroniService.findLeaderIp(nodes);
        createDnsRecord(targetCluster.getSlug(), leaderIp);

        // Update cluster status
        targetCluster.setHostname(targetCluster.getSlug() + "." + baseDomain);
        targetCluster.setStatus(Cluster.STATUS_RUNNING);
        clusterRepository.save(targetCluster);

        log.info("Restored cluster {} provisioned successfully in {} nodes", targetCluster.getSlug(), nodes.size());
    }

    /**
     * Upload configuration for first node that will restore from backup
     */
    private void uploadNodeConfigForRestore(Cluster targetCluster, Cluster sourceCluster, VpsNode node,
                                             String etcdCluster, String etcdHosts, String replicatorPassword,
                                             String backupLabel, Instant targetTime) {
        log.info("Uploading restore config to node {}...", node.getName());

        // Generate .env file
        String envFile = generateEnvFile(targetCluster.getPostgresPassword(), replicatorPassword);

        // Generate docker-compose.yml
        String dockerCompose = generateDockerCompose(
                targetCluster.getSlug(),
                node.getName(),
                node.getPublicIp(),
                etcdCluster,
                targetCluster.getPostgresVersion()
        );

        // Generate patroni.yml with restore method
        String patroniYml = generatePatroniConfigForRestore(
                targetCluster.getId().toString(),
                targetCluster.getSlug(),
                node.getName(),
                node.getPublicIp(),
                etcdHosts,
                targetCluster.getPostgresPassword(),
                replicatorPassword,
                targetCluster.getNodeSize(),
                targetCluster.getPostgresVersion(),
                sourceCluster.getSlug(),
                backupLabel,
                targetTime
        );

        // Ensure config directory exists
        sshService.executeCommand(node.getPublicIp(), "mkdir -p /opt/pgcluster");

        // Upload configs
        sshService.uploadContent(node.getPublicIp(), envFile, "/opt/pgcluster/.env");
        sshService.executeCommand(node.getPublicIp(), "chmod 600 /opt/pgcluster/.env");

        sshService.uploadContent(node.getPublicIp(), dockerCompose, "/opt/pgcluster/docker-compose.yml");
        sshService.uploadContent(node.getPublicIp(), patroniYml, "/opt/pgcluster/patroni.yml");
        sshService.executeCommand(node.getPublicIp(), "chmod 644 /opt/pgcluster/patroni.yml");

        // Ensure data directories exist
        sshService.executeCommand(node.getPublicIp(),
                "mkdir -p /data/postgresql /data/etcd && chmod 700 /data/postgresql && chown -R 999:999 /data/postgresql");

        // Generate and upload PgBouncer configs
        String pgbouncerConfig = generatePgBouncerConfig(targetCluster.getNodeSize());
        sshService.uploadContent(node.getPublicIp(), pgbouncerConfig, "/opt/pgcluster/pgbouncer.ini");

        String pgbouncerUserlist = generatePgBouncerUserlist(targetCluster.getPostgresPassword());
        sshService.uploadContent(node.getPublicIp(), pgbouncerUserlist, "/opt/pgcluster/userlist.txt");
        sshService.executeCommand(node.getPublicIp(), "chmod 644 /opt/pgcluster/userlist.txt");

        log.info("Restore config uploaded to node {}", node.getName());
    }

    /**
     * Upload configuration for replica nodes (stream from leader)
     */
    private void uploadNodeConfigForReplica(Cluster cluster, VpsNode node, String etcdCluster,
                                             String etcdHosts, String replicatorPassword) {
        // Use normal config - replicas will stream from restored leader
        uploadNodeConfig(cluster, node, etcdCluster, etcdHosts, replicatorPassword);
    }

    /**
     * Generate patroni.yml configured for restore from pgBackRest backup
     */
    private String generatePatroniConfigForRestore(String clusterId, String clusterSlug, String nodeName,
                                                    String nodeIp, String etcdHosts, String postgresPassword,
                                                    String replicatorPassword, String nodeSize, String postgresVersion,
                                                    String sourceClusterSlug, String backupLabel, Instant targetTime) {

        // Calculate PostgreSQL memory settings based on node size
        MemorySettings mem = getMemorySettings(nodeSize);

        // Build pgBackRest restore command
        StringBuilder restoreCmd = new StringBuilder();
        restoreCmd.append("pgbackrest --stanza=").append(sourceClusterSlug);
        if (backupLabel != null && !backupLabel.isEmpty()) {
            restoreCmd.append(" --set=").append(backupLabel);
        }
        restoreCmd.append(" --delta restore");

        // Build recovery target settings for PITR
        // Note: Second line needs explicit indentation because %s only indents the first line
        String recoveryTarget = "";
        if (targetTime != null) {
            String targetTimeStr = targetTime.toString().replace("T", " ").replace("Z", "");
            recoveryTarget = "recovery_target_time: '%s'\n                  recovery_target_action: promote"
                    .formatted(targetTimeStr);
        }

        return """
            scope: %s
            namespace: /pgcluster/
            name: %s

            restapi:
              listen: 0.0.0.0:8008
              connect_address: %s:8008

            etcd3:
              hosts: %s

            bootstrap:
              dcs:
                ttl: 30
                loop_wait: 10
                retry_timeout: 10
                maximum_lag_on_failover: 1048576
                postgresql:
                  use_pg_rewind: true
                  use_slots: true
                  parameters:
                    max_connections: 100
                    shared_buffers: %s
                    effective_cache_size: %s
                    work_mem: 4MB
                    maintenance_work_mem: 64MB
                    wal_level: replica
                    hot_standby: 'on'
                    max_wal_senders: 10
                    max_replication_slots: 10
                    archive_mode: 'on'
                    archive_command: 'pgbackrest --stanza=%s archive-push %%p'

              method: pgbackrest
              pgbackrest:
                command: '%s'
                keep_existing_recovery_conf: false
                no_params: true
                recovery_conf:
                  restore_command: 'pgbackrest --stanza=%s archive-get %%f %%p'
                  %s

              pg_hba:
                - host replication replicator 0.0.0.0/0 md5
                - host all all 0.0.0.0/0 md5

              users:
                postgres:
                  password: %s
                  options:
                    - superuser
                replicator:
                  password: %s
                  options:
                    - replication

            postgresql:
              listen: 0.0.0.0:5432
              connect_address: %s:5432
              data_dir: /var/lib/postgresql/data
              bin_dir: /usr/lib/postgresql/%s/bin
              pgpass: /tmp/pgpass
              authentication:
                superuser:
                  username: postgres
                  password: %s
                replication:
                  username: replicator
                  password: %s
                rewind:
                  username: postgres
                  password: %s
              parameters:
                unix_socket_directories: '/var/run/postgresql'

            tags:
              nofailover: false
              noloadbalance: false
              clonefrom: false
              nosync: false
            """.formatted(
                clusterSlug,
                nodeName,
                nodeIp,
                etcdHosts,
                mem.sharedBuffers(),
                mem.effectiveCache(),
                clusterSlug,           // archive_command uses NEW cluster stanza
                restoreCmd.toString(), // pgbackrest restore command
                sourceClusterSlug,     // restore_command uses SOURCE cluster stanza
                recoveryTarget,
                postgresPassword,
                replicatorPassword,
                nodeIp,
                postgresVersion,       // bin_dir version
                postgresPassword,
                replicatorPassword,
                postgresPassword
        );
    }

    /**
     * Wait for Patroni to complete pgBackRest restore
     */
    private void waitForPatroniRestore(VpsNode node, String clusterSlug, int timeoutSeconds) {
        int maxAttempts = timeoutSeconds / 5;
        int attempt = 0;

        log.info("Waiting for Patroni restore to complete (timeout: {}s)...", timeoutSeconds);

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(5000);
                attempt++;

                SshService.CommandResult result = sshService.executeCommand(
                        node.getPublicIp(),
                        "curl -s http://localhost:8008/patroni",
                        10000
                );

                if (result.isSuccess()) {
                    String output = result.getStdout();
                    // Check if PostgreSQL is running and ready
                    if ((output.contains("\"role\": \"master\"") || output.contains("\"role\":\"master\"") ||
                         output.contains("\"role\": \"primary\"") || output.contains("\"role\":\"primary\"")) &&
                        output.contains("\"state\": \"running\"")) {
                        log.info("Patroni restore completed after {} attempts (~{}s)", attempt, attempt * 5);
                        return;
                    }

                    // Log progress
                    if (attempt % 12 == 0) { // Every minute
                        log.info("Restore still in progress... ({}s elapsed)", attempt * 5);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for restore");
            }
        }

        throw new RuntimeException("pgBackRest restore did not complete within " + timeoutSeconds + " seconds");
    }

    /**
     * Update restore job progress
     */
    private void updateRestoreJobProgress(RestoreJob job, String step, int progress) {
        if (job != null) {
            job.setCurrentStep(step);
            job.setProgress(progress);
            restoreJobRepository.save(job);
        }
    }
}
