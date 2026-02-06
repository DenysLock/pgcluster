package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClusterResponse")
class ClusterResponseTest {

    @Nested
    @DisplayName("fromEntity")
    class FromEntity {

        @Test
        @DisplayName("should map all fields from cluster entity")
        void shouldMapAllFields() {
            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .name("test-cluster")
                    .slug("test-cluster-abc")
                    .plan("starter")
                    .status("running")
                    .postgresVersion("16")
                    .nodeCount(3)
                    .nodeSize("cx23")
                    .region("fsn1")
                    .hostname("test.db.pgcluster.com")
                    .port(5432)
                    .postgresPassword("secret")
                    .storageGb(40)
                    .memoryMb(4096)
                    .cpuCores(2)
                    .errorMessage(null)
                    .provisioningStep("running")
                    .provisioningProgress(6)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            ClusterResponse response = ClusterResponse.fromEntity(cluster);

            assertThat(response.getId()).isEqualTo(cluster.getId());
            assertThat(response.getName()).isEqualTo("test-cluster");
            assertThat(response.getSlug()).isEqualTo("test-cluster-abc");
            assertThat(response.getPlan()).isEqualTo("starter");
            assertThat(response.getStatus()).isEqualTo("running");
            assertThat(response.getPostgresVersion()).isEqualTo("16");
            assertThat(response.getNodeCount()).isEqualTo(3);
            assertThat(response.getNodeSize()).isEqualTo("cx23");
            assertThat(response.getRegion()).isEqualTo("fsn1");
            assertThat(response.getResources().getStorageGb()).isEqualTo(40);
            assertThat(response.getResources().getMemoryMb()).isEqualTo(4096);
            assertThat(response.getResources().getCpuCores()).isEqualTo(2);
            assertThat(response.getTotalSteps()).isEqualTo(Cluster.TOTAL_PROVISIONING_STEPS);
        }

        @Test
        @DisplayName("should build connection info when hostname is present")
        void shouldBuildConnectionInfo() {
            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .name("test")
                    .slug("test-slug")
                    .hostname("test.db.pgcluster.com")
                    .port(5432)
                    .postgresPassword("secret")
                    .build();

            ClusterResponse response = ClusterResponse.fromEntity(cluster);

            assertThat(response.getConnection()).isNotNull();
            assertThat(response.getConnection().getHostname()).isEqualTo("test.db.pgcluster.com");
            assertThat(response.getConnection().getPort()).isEqualTo(5432);
            assertThat(response.getConnection().getUsername()).isEqualTo("postgres");
            assertThat(response.getConnection().isCredentialsAvailable()).isTrue();
        }

        @Test
        @DisplayName("should set credentialsAvailable false when password is null")
        void shouldSetCredentialsFalseWhenNoPassword() {
            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .name("test")
                    .slug("test-slug")
                    .hostname("test.db.pgcluster.com")
                    .postgresPassword(null)
                    .build();

            ClusterResponse response = ClusterResponse.fromEntity(cluster);

            assertThat(response.getConnection()).isNotNull();
            assertThat(response.getConnection().isCredentialsAvailable()).isFalse();
        }

        @Test
        @DisplayName("should set connection to null when hostname is null")
        void shouldSetConnectionNullWhenNoHostname() {
            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .name("test")
                    .slug("test-slug")
                    .hostname(null)
                    .build();

            ClusterResponse response = ClusterResponse.fromEntity(cluster);

            assertThat(response.getConnection()).isNull();
        }

        @Test
        @DisplayName("should map nodes when present")
        void shouldMapNodes() {
            VpsNode node = VpsNode.builder()
                    .id(UUID.randomUUID())
                    .name("node-1")
                    .publicIp("10.0.0.1")
                    .status("running")
                    .role("leader")
                    .serverType("cx23")
                    .location("fsn1")
                    .build();

            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .name("test")
                    .slug("test-slug")
                    .nodes(List.of(node))
                    .build();

            ClusterResponse response = ClusterResponse.fromEntity(cluster);

            assertThat(response.getNodes()).hasSize(1);
            assertThat(response.getNodes().get(0).getName()).isEqualTo("node-1");
            assertThat(response.getNodes().get(0).getPublicIp()).isEqualTo("10.0.0.1");
            assertThat(response.getNodes().get(0).getRole()).isEqualTo("leader");
        }

        @Test
        @DisplayName("should set nodes to null when empty")
        void shouldSetNodesNullWhenEmpty() {
            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .name("test")
                    .slug("test-slug")
                    .build();

            ClusterResponse response = ClusterResponse.fromEntity(cluster);

            assertThat(response.getNodes()).isNull();
        }
    }
}
