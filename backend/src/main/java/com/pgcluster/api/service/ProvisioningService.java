package com.pgcluster.api.service;

import com.pgcluster.api.client.CloudflareClient;
import com.pgcluster.api.client.HetznerClient;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private final HetznerClient hetznerClient;
    private final CloudflareClient cloudflareClient;
    private final SshService sshService;
    private final ClusterRepository clusterRepository;
    private final VpsNodeRepository vpsNodeRepository;

    @Value("${cluster.base-domain}")
    private String baseDomain;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Provision a cluster asynchronously
     */
    @Async
    public void provisionClusterAsync(Cluster cluster) {
        try {
            provisionCluster(cluster);
        } catch (Exception e) {
            log.error("Failed to provision cluster {}: {}", cluster.getSlug(), e.getMessage(), e);
            updateClusterStatus(cluster.getId(), Cluster.STATUS_ERROR, e.getMessage());
        }
    }

    /**
     * Main provisioning logic - SSH-based with Docker containers
     */
    @Transactional
    public void provisionCluster(Cluster cluster) {
        log.info("Starting provisioning for cluster: {}", cluster.getSlug());

        updateClusterStatus(cluster.getId(), Cluster.STATUS_CREATING, null);

        // Generate passwords
        String replicatorPassword = generatePassword(24);

        // Phase 1: Create VPS nodes with snapshot
        log.info("Phase 1: Creating VPS nodes...");
        updateClusterProgress(cluster.getId(), Cluster.STEP_CREATING_SERVERS, 1);
        List<VpsNode> nodes = createVpsNodes(cluster);

        // Phase 2: Wait for SSH to be available
        log.info("Phase 2: Waiting for SSH on all nodes...");
        updateClusterProgress(cluster.getId(), Cluster.STEP_WAITING_SSH, 2);
        waitForSsh(nodes);

        // Phase 3: Build cluster configuration strings
        log.info("Phase 3: Building cluster configuration...");
        updateClusterProgress(cluster.getId(), Cluster.STEP_BUILDING_CONFIG, 3);
        String etcdCluster = nodes.stream()
                .map(n -> n.getName() + "=http://" + n.getPublicIp() + ":2380")
                .collect(Collectors.joining(","));

        String etcdHosts = nodes.stream()
                .map(n -> n.getPublicIp() + ":2379")
                .collect(Collectors.joining(","));

        // Phase 4a: Upload configs to all nodes
        log.info("Phase 4a: Uploading configs to all nodes...");
        updateClusterProgress(cluster.getId(), Cluster.STEP_STARTING_CONTAINERS, 4);
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

        // Phase 4d: Start remaining containers (patroni, exporters)
        log.info("Phase 4d: Starting remaining containers on all nodes...");
        for (VpsNode node : nodes) {
            startRemainingContainers(node);
        }

        // Phase 5: Wait for Patroni cluster to be healthy
        log.info("Phase 5: Waiting for Patroni cluster to be healthy...");
        updateClusterProgress(cluster.getId(), Cluster.STEP_ELECTING_LEADER, 5);
        waitForPatroniCluster(nodes, cluster.getSlug());

        // Phase 6: Create DNS record pointing to leader
        log.info("Phase 6: Creating DNS record...");
        updateClusterProgress(cluster.getId(), Cluster.STEP_CREATING_DNS, 6);
        String leaderIp = findLeaderIp(nodes);
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

        log.info("Using image/snapshot: {}", snapshotId);

        for (int i = 0; i < cluster.getNodeCount(); i++) {
            String nodeName = String.format("%s-node-%d", cluster.getSlug(), i + 1);

            // Create VPS node entity
            VpsNode node = VpsNode.builder()
                    .cluster(cluster)
                    .name(nodeName)
                    .serverType(cluster.getNodeSize())
                    .location(cluster.getRegion())
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
                        .location(cluster.getRegion())
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

                log.info("Created node {} with IP {}", nodeName, node.getPublicIp());

            } catch (Exception e) {
                log.error("Failed to create node {}: {}", nodeName, e.getMessage());
                node.setStatus(VpsNode.STATUS_ERROR);
                node.setErrorMessage(e.getMessage());
                vpsNodeRepository.save(node);
                throw e;
            }
        }

        return nodes;
    }

    /**
     * Wait for SSH to be available on all nodes
     */
    private void waitForSsh(List<VpsNode> nodes) {
        for (VpsNode node : nodes) {
            log.info("Waiting for SSH on {}...", node.getName());
            boolean ready = sshService.waitForSsh(node.getPublicIp(), 60, 5000); // 5 minutes max
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
                etcdCluster
        );

        // Generate patroni.yml
        String patroniYml = generatePatroniConfig(
                cluster.getSlug(),
                node.getName(),
                node.getPublicIp(),
                etcdHosts,
                cluster.getPostgresPassword(),
                replicatorPassword,
                cluster.getNodeSize()
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

        // Set restrictive permissions on userlist.txt (contains password hash)
        sshService.executeCommand(
                node.getPublicIp(),
                "chmod 600 /opt/pgcluster/userlist.txt"
        );

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
     * Generate docker-compose.yml from template.
     * Passwords are loaded from .env file for security.
     */
    private String generateDockerCompose(String clusterSlug, String nodeName,
                                         String nodeIp, String etcdCluster) {
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
                image: pgcluster/patroni:16
                container_name: patroni
                restart: unless-stopped
                network_mode: host
                depends_on:
                  etcd:
                    condition: service_healthy
                volumes:
                  - /data/postgresql:/var/lib/postgresql/data
                  - /opt/pgcluster/patroni.yml:/etc/patroni/patroni.yml:ro
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
                nodeName,           // PATRONI_NAME
                nodeIp,             // PATRONI_RESTAPI_CONNECT_ADDRESS
                nodeIp              // PATRONI_POSTGRESQL_CONNECT_ADDRESS
        );
    }

    /**
     * Generate patroni.yml configuration
     */
    private String generatePatroniConfig(String clusterSlug, String nodeName,
                                         String nodeIp, String etcdHosts,
                                         String postgresPassword, String replicatorPassword,
                                         String nodeSize) {
        // Calculate memory settings based on node size
        MemorySettings mem = getMemorySettings(nodeSize);

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
                    hot_standby: on
                    max_wal_senders: 10
                    max_replication_slots: 10
                    wal_keep_size: 128MB
                    logging_collector: off
                    log_destination: stderr

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
              bin_dir: /usr/lib/postgresql/16/bin
              pgpass: /tmp/pgpass
              authentication:
                replication:
                  username: replicator
                  password: %s
                superuser:
                  username: postgres
                  password: %s

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
                postgresPassword,   // postgres password
                replicatorPassword, // replicator password
                nodeIp,             // postgresql connect_address
                replicatorPassword, // replication password
                postgresPassword    // superuser password
        );
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

                    if (result.isSuccess()) {
                        String output = result.getStdout();
                        // Check for leader role (handle JSON with or without spaces)
                        if (output.contains("\"role\": \"master\"") || output.contains("\"role\":\"master\"") ||
                            output.contains("\"role\": \"primary\"") || output.contains("\"role\":\"primary\"") ||
                            output.contains("\"role\": \"leader\"") || output.contains("\"role\":\"leader\"")) {
                            log.info("Patroni cluster {} has elected leader on {}",
                                    clusterSlug, node.getName());
                            return;
                        }
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
     * Find the leader node's IP
     */
    private String findLeaderIp(List<VpsNode> nodes) {
        for (VpsNode node : nodes) {
            try {
                SshService.CommandResult result = sshService.executeCommand(
                        node.getPublicIp(),
                        "curl -s http://localhost:8008/patroni",
                        10000
                );

                if (result.isSuccess()) {
                    String output = result.getStdout();
                    // Check for leader role (handle JSON with or without spaces)
                    if (output.contains("\"role\": \"master\"") || output.contains("\"role\":\"master\"") ||
                        output.contains("\"role\": \"primary\"") || output.contains("\"role\":\"primary\"") ||
                        output.contains("\"role\": \"leader\"") || output.contains("\"role\":\"leader\"")) {
                        return node.getPublicIp();
                    }
                }
            } catch (Exception e) {
                log.warn("Error checking node {} for leader: {}", node.getName(), e.getMessage());
            }
        }

        // Fallback to first node if no leader found
        log.warn("No leader found, falling back to first node");
        return nodes.get(0).getPublicIp();
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
            updateClusterStatus(cluster.getId(), Cluster.STATUS_ERROR, "Deletion failed: " + e.getMessage());
        }
    }

    /**
     * Delete cluster and all its resources
     */
    @Transactional
    public void deleteCluster(Cluster cluster) {
        log.info("Deleting cluster: {}", cluster.getSlug());

        // Delete VPS nodes
        List<VpsNode> nodes = vpsNodeRepository.findByCluster(cluster);
        for (VpsNode node : nodes) {
            if (node.getHetznerId() != null) {
                try {
                    hetznerClient.deleteServer(node.getHetznerId());
                    log.info("Deleted Hetzner server: {}", node.getHetznerId());
                } catch (Exception e) {
                    log.warn("Failed to delete Hetzner server {}: {}", node.getHetznerId(), e.getMessage());
                }
            }
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

    private void updateClusterStatus(UUID clusterId, String status, String errorMessage) {
        clusterRepository.findById(clusterId).ifPresent(cluster -> {
            cluster.setStatus(status);
            if (errorMessage != null) {
                cluster.setErrorMessage(errorMessage);
            }
            clusterRepository.save(cluster);
        });
    }

    private void updateClusterProgress(UUID clusterId, String step, int progress) {
        clusterRepository.findById(clusterId).ifPresent(cluster -> {
            cluster.setProvisioningStep(step);
            cluster.setProvisioningProgress(progress);
            clusterRepository.save(cluster);
            log.info("Cluster {} progress: step={} ({}/{})",
                    cluster.getSlug(), step, progress, Cluster.TOTAL_PROVISIONING_STEPS);
        });
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
