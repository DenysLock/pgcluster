package com.pgcluster.api.service;

import com.pgcluster.api.client.CloudflareClient;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DnsSyncService")
@ExtendWith(MockitoExtension.class)
class DnsSyncServiceTest {

    @Mock private ClusterRepository clusterRepository;
    @Mock private PatroniService patroniService;
    @Mock private CloudflareClient cloudflareClient;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DnsSyncService dnsSyncService;

    @Nested
    @DisplayName("syncClusterDns")
    class SyncClusterDns {

        @Test
        @DisplayName("should skip when local node is NOT leader (pg_is_in_recovery = true)")
        void shouldSkipWhenNotLeader() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);

            dnsSyncService.syncClusterDns();

            verify(clusterRepository, never()).findByStatusWithNodes(any());
        }

        @Test
        @DisplayName("should skip when jdbcTemplate throws exception")
        void shouldSkipWhenJdbcThrows() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                    .thenThrow(new RuntimeException("DB error"));

            dnsSyncService.syncClusterDns();

            verify(clusterRepository, never()).findByStatusWithNodes(any());
        }

        @Test
        @DisplayName("should process clusters when local node is leader")
        void shouldProcessClustersWhenLeader() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

            Cluster cluster = createCluster("test.db.pgcluster.com");
            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of(cluster));

            String leaderIp = "10.0.0.1";
            when(patroniService.findLeaderIp(anyList())).thenReturn(leaderIp);

            CloudflareClient.DnsRecord record = new CloudflareClient.DnsRecord();
            record.setId("record-id");
            record.setContent(leaderIp); // Same IP — no update needed
            when(cloudflareClient.findDnsRecord("test.db.pgcluster.com")).thenReturn(record);

            dnsSyncService.syncClusterDns();

            verify(clusterRepository).findByStatusWithNodes(Cluster.STATUS_RUNNING);
            verify(cloudflareClient, never()).updateDnsRecord(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("should update DNS when leader IP has changed")
        void shouldUpdateDnsOnLeaderChange() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

            Cluster cluster = createCluster("test.db.pgcluster.com");
            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of(cluster));

            when(patroniService.findLeaderIp(anyList())).thenReturn("10.0.0.2");

            CloudflareClient.DnsRecord record = new CloudflareClient.DnsRecord();
            record.setId("record-id");
            record.setContent("10.0.0.1"); // Old IP
            when(cloudflareClient.findDnsRecord("test.db.pgcluster.com")).thenReturn(record);

            dnsSyncService.syncClusterDns();

            verify(cloudflareClient).updateDnsRecord("record-id", "test.db.pgcluster.com", "10.0.0.2", false);
        }

        @Test
        @DisplayName("should skip cluster when hostname is null")
        void shouldSkipClusterWithNullHostname() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

            Cluster cluster = createCluster(null); // No hostname
            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of(cluster));

            dnsSyncService.syncClusterDns();

            verify(patroniService, never()).findLeaderIp(any());
        }

        @Test
        @DisplayName("should skip cluster when nodes list is empty")
        void shouldSkipClusterWithEmptyNodes() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

            Cluster cluster = createCluster("test.db.pgcluster.com");
            cluster.setNodes(new ArrayList<>()); // Empty nodes
            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of(cluster));

            dnsSyncService.syncClusterDns();

            verify(patroniService, never()).findLeaderIp(any());
        }

        @Test
        @DisplayName("should skip DNS update when DNS record not found")
        void shouldSkipWhenNoDnsRecord() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

            Cluster cluster = createCluster("test.db.pgcluster.com");
            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of(cluster));
            when(patroniService.findLeaderIp(anyList())).thenReturn("10.0.0.1");
            when(cloudflareClient.findDnsRecord("test.db.pgcluster.com")).thenReturn(null);

            dnsSyncService.syncClusterDns();

            verify(cloudflareClient, never()).updateDnsRecord(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("should continue processing other clusters on failure")
        void shouldContinueOnClusterFailure() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

            Cluster cluster1 = createCluster("cluster1.db.pgcluster.com");
            Cluster cluster2 = createCluster("cluster2.db.pgcluster.com");

            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of(cluster1, cluster2));

            // First cluster throws exception
            when(patroniService.findLeaderIp(cluster1.getNodes()))
                    .thenThrow(new RuntimeException("Leader discovery failed"));

            // Second cluster works fine
            when(patroniService.findLeaderIp(cluster2.getNodes())).thenReturn("10.0.0.2");

            CloudflareClient.DnsRecord record = new CloudflareClient.DnsRecord();
            record.setId("id2");
            record.setContent("10.0.0.2");
            when(cloudflareClient.findDnsRecord("cluster2.db.pgcluster.com")).thenReturn(record);

            dnsSyncService.syncClusterDns();

            // Second cluster should still be processed
            verify(cloudflareClient).findDnsRecord("cluster2.db.pgcluster.com");
        }

        @Test
        @DisplayName("should handle empty running clusters list")
        void shouldHandleEmptyClusterList() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
            when(clusterRepository.findByStatusWithNodes(Cluster.STATUS_RUNNING))
                    .thenReturn(List.of());

            dnsSyncService.syncClusterDns();

            verify(patroniService, never()).findLeaderIp(any());
            verify(cloudflareClient, never()).findDnsRecord(any());
        }
    }

    // ==================== Helpers ====================

    private Cluster createCluster(String hostname) {
        VpsNode node = VpsNode.builder()
                .id(UUID.randomUUID())
                .publicIp("10.0.0.1")
                .name("node-1")
                .build();

        List<VpsNode> nodes = new ArrayList<>();
        nodes.add(node);

        return Cluster.builder()
                .id(UUID.randomUUID())
                .name("test-cluster")
                .slug("test-cluster-abc")
                .hostname(hostname)
                .status(Cluster.STATUS_RUNNING)
                .nodes(nodes)
                .build();
    }
}
