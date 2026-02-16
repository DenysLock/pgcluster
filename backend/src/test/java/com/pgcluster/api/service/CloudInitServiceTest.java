package com.pgcluster.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CloudInitService")
class CloudInitServiceTest {

    private CloudInitService cloudInitService;

    @BeforeEach
    void setUp() {
        cloudInitService = new CloudInitService();
        ReflectionTestUtils.setField(cloudInitService, "baseDomain", "db.pgcluster.com");
    }

    @Nested
    @DisplayName("generateCloudInit")
    class GenerateCloudInit {

        @Test
        @DisplayName("should generate valid bash script header")
        void shouldGenerateBashHeader() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).startsWith("#!/bin/bash");
            assertThat(script).contains("set -e");
        }

        @Test
        @DisplayName("should include postgres version in script")
        void shouldIncludePostgresVersion() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("postgresql-16");
            assertThat(script).contains("postgresql-client-16");
        }

        @Test
        @DisplayName("should include postgres version 15")
        void shouldIncludePostgresVersion15() {
            CloudInitService.CloudInitConfig config = createConfig("15");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("postgresql-15");
        }

        @Test
        @DisplayName("should include node IP in etcd service")
        void shouldIncludeNodeIp() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("10.0.0.1");
        }

        @Test
        @DisplayName("should include cluster slug in patroni config")
        void shouldIncludeClusterSlug() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("test-cluster-abc123");
        }

        @Test
        @DisplayName("should include node name in configs")
        void shouldIncludeNodeName() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("node-1");
        }

        @Test
        @DisplayName("should include postgres password in patroni config")
        void shouldIncludePasswords() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("secret-pg-password");
            assertThat(script).contains("secret-repl-password");
        }

        @Test
        @DisplayName("should include etcd installation steps")
        void shouldIncludeEtcd() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("etcd");
            assertThat(script).contains("ETCD_VER=v3.5.11");
        }

        @Test
        @DisplayName("should include patroni installation steps")
        void shouldIncludePatroni() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("patroni");
            assertThat(script).contains("pip3 install patroni");
        }

        @Test
        @DisplayName("should include systemd service enablement")
        void shouldIncludeServiceStart() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("systemctl enable etcd");
            assertThat(script).contains("systemctl start etcd");
            assertThat(script).contains("systemctl enable patroni");
            assertThat(script).contains("systemctl start patroni");
        }

        @Test
        @DisplayName("should include etcd initial cluster config")
        void shouldIncludeEtcdClusterConfig() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("node1=http://10.0.0.1:2380,node2=http://10.0.0.2:2380");
        }

        @Test
        @DisplayName("should include etcd hosts config")
        void shouldIncludeEtcdHosts() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("10.0.0.1:2379,10.0.0.2:2379");
        }

        @Test
        @DisplayName("should include replication user setup")
        void shouldIncludeReplicationUser() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("replicator");
            assertThat(script).contains("replication");
        }

        @Test
        @DisplayName("should include PostgreSQL data directory cleanup")
        void shouldIncludeDataDirCleanup() {
            CloudInitService.CloudInitConfig config = createConfig("16");
            String script = cloudInitService.generateCloudInit(config);
            assertThat(script).contains("rm -rf /var/lib/postgresql/16/main");
            assertThat(script).contains("mkdir -p /var/lib/postgresql/16/main");
        }

        @Test
        @DisplayName("should include completion message")
        void shouldIncludeCompletionMessage() {
            String script = cloudInitService.generateCloudInit(createConfig("16"));
            assertThat(script).contains("Cloud-init completed");
        }
    }

    private CloudInitService.CloudInitConfig createConfig(String postgresVersion) {
        return CloudInitService.CloudInitConfig.builder()
                .clusterSlug("test-cluster-abc123")
                .nodeName("node-1")
                .nodeIp("10.0.0.1")
                .postgresVersion(postgresVersion)
                .postgresPassword("secret-pg-password")
                .replicatorPassword("secret-repl-password")
                .etcdInitialCluster("node1=http://10.0.0.1:2380,node2=http://10.0.0.2:2380")
                .etcdHosts("10.0.0.1:2379,10.0.0.2:2379")
                .allNodeIps(List.of("10.0.0.1", "10.0.0.2"))
                .build();
    }
}
