package com.pgcluster.api.service;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.VpsNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for interacting with Patroni cluster management.
 * Provides methods to query Patroni REST API on cluster nodes.
 */
@Slf4j
@Service
public class PatroniService {

    private final SshService sshService;
    private final VpsNodeRepository vpsNodeRepository;

    @Value("${timeouts.patroni-api:10000}")
    private int patroniTimeoutMs;

    private static final String PATRONI_API_COMMAND = "curl -s http://localhost:8008/patroni";

    public PatroniService(SshService sshService, VpsNodeRepository vpsNodeRepository) {
        this.sshService = sshService;
        this.vpsNodeRepository = vpsNodeRepository;
    }

    /**
     * Check if a node is the current Patroni leader (primary).
     *
     * @param node The node to check
     * @return true if the node is the leader
     */
    public boolean isLeaderNode(VpsNode node) {
        try {
            SshService.CommandResult result = sshService.executeCommand(
                    node.getPublicIp(),
                    PATRONI_API_COMMAND,
                    patroniTimeoutMs
            );

            if (result.isSuccess()) {
                return isLeaderRole(result.getStdout());
            }
        } catch (Exception e) {
            log.warn("Error checking node {} for leader: {}", node.getName(), e.getMessage());
        }
        return false;
    }

    /**
     * Find the leader node for a cluster.
     *
     * @param cluster The cluster to find the leader for
     * @return The leader node, or null if not found
     */
    public VpsNode findLeaderNode(Cluster cluster) {
        List<VpsNode> nodes = vpsNodeRepository.findByCluster(cluster);
        return findLeaderNode(nodes);
    }

    /**
     * Find the leader node from a list of nodes.
     * Uses parallel execution to check all nodes concurrently for faster leader discovery.
     *
     * @param nodes The list of nodes to search
     * @return The leader node, or null if not found
     */
    public VpsNode findLeaderNode(List<VpsNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        // Check all nodes in parallel
        List<CompletableFuture<VpsNode>> futures = nodes.stream()
                .map(node -> CompletableFuture.supplyAsync(() -> {
                    try {
                        SshService.CommandResult result = sshService.executeCommand(
                                node.getPublicIp(),
                                PATRONI_API_COMMAND,
                                patroniTimeoutMs
                        );
                        if (result.isSuccess() && isLeaderRole(result.getStdout())) {
                            log.debug("Found leader node: {} ({})", node.getName(), node.getPublicIp());
                            return node;
                        }
                    } catch (Exception e) {
                        log.warn("Error checking node {} for leader: {}", node.getName(), e.getMessage());
                    }
                    return null;
                }))
                .toList();

        // Wait for all to complete with overall timeout
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            allFutures.get(patroniTimeoutMs + 2000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for leader discovery, checking partial results");
        } catch (Exception e) {
            log.warn("Error during parallel leader discovery: {}", e.getMessage());
        }

        // Return the first successfully identified leader
        return futures.stream()
                .map(f -> {
                    try {
                        return f.getNow(null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Find the leader node's IP address from a list of nodes.
     * Falls back to first node if no leader is found.
     *
     * @param nodes The list of nodes to search
     * @return The leader's IP address, or first node's IP as fallback
     * @throws IllegalStateException if no usable IP address can be found
     */
    public String findLeaderIp(List<VpsNode> nodes) {
        VpsNode leader = findLeaderNode(nodes);
        if (leader != null) {
            return getNodeIp(leader);
        }
        // Fallback to first node if no leader found
        log.warn("No leader found, falling back to first node");
        if (nodes.isEmpty()) {
            throw new IllegalStateException("No nodes available to find leader IP");
        }
        return getNodeIp(nodes.get(0));
    }

    /**
     * Get a usable IP address from a node.
     * Prefers publicIp, falls back to privateIp.
     */
    private String getNodeIp(VpsNode node) {
        String ip = node.getPublicIp();
        if (ip == null || ip.isBlank()) {
            ip = node.getPrivateIp();
        }
        if (ip == null || ip.isBlank()) {
            throw new IllegalStateException("Node " + node.getName() + " has no usable IP address");
        }
        return ip;
    }

    /**
     * Check if Patroni API response indicates a leader role.
     * Handles various role names used by Patroni: master, primary, leader.
     *
     * @param patroniOutput The JSON output from Patroni API
     * @return true if the output indicates a leader role
     */
    public boolean isLeaderRole(String patroniOutput) {
        return "leader".equals(parseRole(patroniOutput));
    }

    /**
     * Check if Patroni API response indicates a replica role.
     *
     * @param patroniOutput The JSON output from Patroni API
     * @return true if the output indicates a replica role
     */
    public boolean isReplicaRole(String patroniOutput) {
        return "replica".equals(parseRole(patroniOutput));
    }

    /**
     * Parse the role from Patroni API output.
     * Normalizes various Patroni role names to "leader", "replica", or "unknown".
     *
     * @param patroniOutput The JSON output from Patroni API
     * @return "leader", "replica", or "unknown"
     */
    public String parseRole(String patroniOutput) {
        if (patroniOutput == null || !patroniOutput.contains("\"role\"")) {
            return "unknown";
        }
        // Check for leader roles (master, primary, leader)
        if (patroniOutput.contains("\"role\": \"master\"") || patroniOutput.contains("\"role\":\"master\"") ||
            patroniOutput.contains("\"role\": \"primary\"") || patroniOutput.contains("\"role\":\"primary\"") ||
            patroniOutput.contains("\"role\": \"leader\"") || patroniOutput.contains("\"role\":\"leader\"")) {
            return "leader";
        }
        // Check for replica role
        if (patroniOutput.contains("\"role\": \"replica\"") || patroniOutput.contains("\"role\":\"replica\"")) {
            return "replica";
        }
        return "unknown";
    }

    /**
     * Get the replication state for a role.
     *
     * @param role The parsed role ("leader", "replica", or "unknown")
     * @return "running" for leader, "streaming" for replica, "unknown" otherwise
     */
    public String getStateForRole(String role) {
        return switch (role) {
            case "leader" -> "running";
            case "replica" -> "streaming";
            default -> "unknown";
        };
    }
}
