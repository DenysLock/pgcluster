package com.pgcluster.api.service;

import com.pgcluster.api.client.CloudflareClient;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service that synchronizes DNS records with current Patroni leaders.
 * Runs every 30 seconds to detect failover and update DNS accordingly.
 *
 * Only runs on the database leader node to avoid duplicate work across
 * the 3 control plane instances.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DnsSyncService {

    private final ClusterRepository clusterRepository;
    private final PatroniService patroniService;
    private final CloudflareClient cloudflareClient;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Scheduled job that checks all running clusters and updates DNS
     * if the Patroni leader has changed.
     *
     * Only executes on the DB leader node to prevent duplicate API calls.
     */
    @Scheduled(fixedRate = 30000)  // 30 seconds
    public void syncClusterDns() {
        // Only run on the DB leader node to avoid 3x duplicate work
        if (!isLocalNodeLeader()) {
            return;
        }

        List<Cluster> runningClusters = clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING);

        for (Cluster cluster : runningClusters) {
            try {
                syncDnsForCluster(cluster);
            } catch (Exception e) {
                log.warn("Failed to sync DNS for cluster {}: {}", cluster.getSlug(), e.getMessage());
            }
        }
    }

    private void syncDnsForCluster(Cluster cluster) {
        if (cluster.getHostname() == null) {
            return;
        }

        List<VpsNode> nodes = cluster.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // Find current Patroni leader
        String currentLeaderIp = patroniService.findLeaderIp(nodes);
        if (currentLeaderIp == null) {
            log.debug("No leader found for cluster {}, skipping DNS sync", cluster.getSlug());
            return;
        }

        // Get current DNS record
        CloudflareClient.DnsRecord dnsRecord = cloudflareClient.findDnsRecord(cluster.getHostname());
        if (dnsRecord == null) {
            log.warn("No DNS record found for {}", cluster.getHostname());
            return;
        }

        String currentDnsIp = dnsRecord.getContent();
        String recordId = dnsRecord.getId();

        // Update if different
        if (!currentLeaderIp.equals(currentDnsIp)) {
            log.info("DNS mismatch for {}: DNS={}, Leader={}. Updating...",
                cluster.getSlug(), currentDnsIp, currentLeaderIp);

            cloudflareClient.updateDnsRecord(recordId, cluster.getHostname(), currentLeaderIp, false);

            log.info("Updated DNS for {} from {} to {}",
                cluster.getSlug(), currentDnsIp, currentLeaderIp);
        }
    }

    /**
     * Check if the local PostgreSQL instance is the primary (leader).
     * pg_is_in_recovery() returns false on the primary, true on replicas.
     */
    private boolean isLocalNodeLeader() {
        try {
            Boolean isInRecovery = jdbcTemplate.queryForObject(
                "SELECT pg_is_in_recovery()", Boolean.class);
            return isInRecovery != null && !isInRecovery;
        } catch (Exception e) {
            log.debug("Could not determine leader status: {}", e.getMessage());
            return false;
        }
    }
}
