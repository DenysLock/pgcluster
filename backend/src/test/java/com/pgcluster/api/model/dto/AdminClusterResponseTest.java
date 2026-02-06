package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminClusterResponse")
class AdminClusterResponseTest {

    @Nested
    @DisplayName("fromEntity")
    class FromEntity {

        @Test
        @DisplayName("should map all fields including owner info")
        void shouldMapAllFieldsWithOwner() {
            User owner = User.builder()
                    .id(UUID.randomUUID())
                    .email("owner@test.com")
                    .passwordHash("hash")
                    .role("USER")
                    .active(true)
                    .build();

            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .user(owner)
                    .name("admin-test")
                    .slug("admin-test-slug")
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
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            AdminClusterResponse response = AdminClusterResponse.fromEntity(cluster);

            assertThat(response.getId()).isEqualTo(cluster.getId());
            assertThat(response.getOwnerId()).isEqualTo(owner.getId());
            assertThat(response.getOwnerEmail()).isEqualTo("owner@test.com");
            assertThat(response.getName()).isEqualTo("admin-test");
            assertThat(response.getPostgresVersion()).isEqualTo("16");
            assertThat(response.getConnection()).isNotNull();
            assertThat(response.getConnection().isCredentialsAvailable()).isTrue();
            assertThat(response.getResources().getStorageGb()).isEqualTo(40);
        }

        @Test
        @DisplayName("should handle null hostname")
        void shouldHandleNullHostname() {
            User owner = User.builder()
                    .id(UUID.randomUUID())
                    .email("owner@test.com")
                    .passwordHash("hash")
                    .role("USER")
                    .active(true)
                    .build();

            Cluster cluster = Cluster.builder()
                    .id(UUID.randomUUID())
                    .user(owner)
                    .name("test")
                    .slug("test-slug")
                    .hostname(null)
                    .build();

            AdminClusterResponse response = AdminClusterResponse.fromEntity(cluster);

            assertThat(response.getConnection()).isNull();
        }

        @Test
        @DisplayName("should map nodes when present")
        void shouldMapNodes() {
            User owner = User.builder()
                    .id(UUID.randomUUID())
                    .email("owner@test.com")
                    .passwordHash("hash")
                    .role("USER")
                    .active(true)
                    .build();

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
                    .user(owner)
                    .name("test")
                    .slug("test-slug")
                    .nodes(List.of(node))
                    .build();

            AdminClusterResponse response = AdminClusterResponse.fromEntity(cluster);

            assertThat(response.getNodes()).hasSize(1);
            assertThat(response.getNodes().get(0).getName()).isEqualTo("node-1");
        }
    }
}
