package com.pgcluster.api.service;

import com.pgcluster.api.client.HetznerClient;
import com.pgcluster.api.event.ClusterDeleteRequestedEvent;
import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.ClusterCreateRequest;
import com.pgcluster.api.model.dto.ClusterCredentialsResponse;
import com.pgcluster.api.model.dto.ClusterListResponse;
import com.pgcluster.api.model.dto.ClusterResponse;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ClusterService")
@ExtendWith(MockitoExtension.class)
class ClusterServiceTest {

    @Mock private ClusterRepository clusterRepository;
    @Mock private VpsNodeRepository vpsNodeRepository;
    @Mock private HetznerClient hetznerClient;
    @Mock private ProvisioningService provisioningService;
    @Mock private SshService sshService;
    @Mock private PatroniService patroniService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ClusterService clusterService;

    @Nested
    @DisplayName("createCluster")
    class CreateCluster {

        @Test
        @DisplayName("should create cluster and start provisioning")
        void shouldCreateCluster() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1", "nbg1", "hel1"));
            when(clusterRepository.existsBySlug(any())).thenReturn(false);
            when(clusterRepository.saveAndFlush(any(Cluster.class))).thenAnswer(inv -> {
                Cluster c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(provisioningService.createAllServersSync(any())).thenReturn(List.of(new VpsNode()));

            ClusterResponse response = clusterService.createCluster(request, user);

            assertThat(response).isNotNull();
            verify(provisioningService).continueProvisioningFromServers(any(), any());
            verify(auditLogService).logAsync(eq(AuditLog.CLUSTER_CREATED), eq(user), eq("cluster"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw CONFLICT when custom slug already exists")
        void shouldThrowOnDuplicateSlug() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            request.setSlug("my-cluster");

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1"));
            when(clusterRepository.existsBySlug("my-cluster")).thenReturn(true);

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("should validate node regions availability")
        void shouldValidateRegions() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            request.setNodeRegions(List.of("invalid-region"));

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1", "nbg1"));

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("invalid-region");
                    });
        }

        @Test
        @DisplayName("should clean up cluster record on server creation failure")
        void shouldCleanupOnFailure() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1"));
            when(clusterRepository.existsBySlug(any())).thenReturn(false);
            when(clusterRepository.saveAndFlush(any(Cluster.class))).thenAnswer(inv -> {
                Cluster c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(provisioningService.createAllServersSync(any()))
                    .thenThrow(new RuntimeException("Server creation failed"));

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

            verify(clusterRepository).deleteById(any(UUID.class));
        }

        @Test
        @DisplayName("should parse Hetzner dedicated core limit error")
        void shouldParseHetznerDedicatedCoreError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1"));
            when(clusterRepository.existsBySlug(any())).thenReturn(false);
            when(clusterRepository.saveAndFlush(any(Cluster.class))).thenAnswer(inv -> {
                Cluster c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(provisioningService.createAllServersSync(any()))
                    .thenThrow(new RuntimeException("dedicated_core_limit exceeded"));

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("dedicated vCPU limit"));
        }
    }

    @Nested
    @DisplayName("deleteCluster")
    class DeleteCluster {

        @Test
        @DisplayName("should set status to DELETING and publish event")
        void shouldDeleteCluster() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(clusterRepository.save(any(Cluster.class))).thenAnswer(inv -> inv.getArgument(0));

            clusterService.deleteCluster(cluster.getId(), user);

            ArgumentCaptor<Cluster> captor = ArgumentCaptor.forClass(Cluster.class);
            verify(clusterRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(Cluster.STATUS_DELETING);

            verify(eventPublisher).publishEvent(any(ClusterDeleteRequestedEvent.class));
            verify(auditLogService).log(eq(AuditLog.CLUSTER_DELETED), eq(user), eq("cluster"), eq(cluster.getId()), any());
        }

        @Test
        @DisplayName("should throw CONFLICT when already deleting")
        void shouldThrowWhenAlreadyDeleting() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_DELETING);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> clusterService.deleteCluster(cluster.getId(), user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster not found")
        void shouldThrowWhenNotFound() {
            User user = createTestUser();
            UUID unknownId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(unknownId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clusterService.deleteCluster(unknownId, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("getClusterCredentials")
    class GetClusterCredentials {

        @Test
        @DisplayName("should return credentials with connection strings")
        void shouldReturnCredentials() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            cluster.setHostname("test.db.pgcluster.com");
            cluster.setPostgresPassword("secret123");
            cluster.setPort(5432);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            ClusterCredentialsResponse response = clusterService.getClusterCredentials(cluster.getId(), user);

            assertThat(response.getHostname()).isEqualTo("test.db.pgcluster.com");
            assertThat(response.getPort()).isEqualTo(5432);
            assertThat(response.getPooledPort()).isEqualTo(6432);
            assertThat(response.getPassword()).isEqualTo("secret123");
            assertThat(response.getConnectionString()).contains("postgresql://postgres:secret123@test.db.pgcluster.com:5432/postgres");

            verify(auditLogService).logAsync(eq(AuditLog.CREDENTIALS_ACCESSED), eq(user), eq("cluster"), eq(cluster.getId()), any(), any(), any());
        }

        @Test
        @DisplayName("should throw SERVICE_UNAVAILABLE when hostname is null")
        void shouldThrowWhenHostnameNull() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            cluster.setHostname(null);
            cluster.setPostgresPassword("secret");

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> clusterService.getClusterCredentials(cluster.getId(), user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }

        @Test
        @DisplayName("should throw SERVICE_UNAVAILABLE when password is null")
        void shouldThrowWhenPasswordNull() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            cluster.setHostname("test.db.pgcluster.com");
            cluster.setPostgresPassword(null);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> clusterService.getClusterCredentials(cluster.getId(), user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @Nested
    @DisplayName("listClusters")
    class ListClusters {

        @Test
        @DisplayName("should return all clusters for user")
        void shouldReturnClusters() {
            User user = createTestUser();
            Cluster c1 = createCluster(Cluster.STATUS_RUNNING);
            Cluster c2 = createCluster(Cluster.STATUS_PENDING);

            when(clusterRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(c1, c2));

            ClusterListResponse response = clusterService.listClusters(user);

            assertThat(response.getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list for user with no clusters")
        void shouldReturnEmptyList() {
            User user = createTestUser();

            when(clusterRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

            ClusterListResponse response = clusterService.listClusters(user);

            assertThat(response.getCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getCluster")
    class GetCluster {

        @Test
        @DisplayName("should return cluster response for valid id and user")
        void shouldReturnCluster() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);

            when(clusterRepository.findByIdAndUserWithNodes(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            ClusterResponse response = clusterService.getCluster(cluster.getId(), user);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster does not exist")
        void shouldThrowWhenNotFound() {
            User user = createTestUser();
            UUID unknownId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUserWithNodes(unknownId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clusterService.getCluster(unknownId, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("getAvailableLocations")
    class GetAvailableLocations {

        @Test
        @DisplayName("should transform Hetzner locations to LocationDto list")
        void shouldTransformLocations() {
            HetznerClient.LocationInfo loc = new HetznerClient.LocationInfo();
            loc.setName("fsn1");
            loc.setDescription("Falkenstein DC Park 1");
            loc.setCity("Falkenstein");
            loc.setCountry("DE");

            when(hetznerClient.getLocations()).thenReturn(List.of(loc));
            when(hetznerClient.getAvailableLocationsForServerType("cx23")).thenReturn(Set.of("fsn1"));

            var result = clusterService.getAvailableLocations("cx23");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("fsn1");
            assertThat(result.get(0).getCity()).isEqualTo("Falkenstein");
            assertThat(result.get(0).isAvailable()).isTrue();
        }

        @Test
        @DisplayName("should mark all locations as available when server type check fails")
        void shouldMarkAllAvailableOnApiFailure() {
            HetznerClient.LocationInfo loc = new HetznerClient.LocationInfo();
            loc.setName("fsn1");
            loc.setDescription("Falkenstein");
            loc.setCity("Falkenstein");
            loc.setCountry("DE");

            when(hetznerClient.getLocations()).thenReturn(List.of(loc));
            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenThrow(new RuntimeException("API error"));

            var result = clusterService.getAvailableLocations("cx23");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("getServerTypes")
    class GetServerTypes {

        @Test
        @DisplayName("should return shared and dedicated server types")
        void shouldReturnServerTypes() {
            HetznerClient.ServerTypeInfo sharedInfo = new HetznerClient.ServerTypeInfo();
            sharedInfo.setName("cx23");
            sharedInfo.setCores(2);
            sharedInfo.setMemory(4);
            sharedInfo.setDisk(40);

            when(hetznerClient.getServerType("cx23")).thenReturn(sharedInfo);
            when(hetznerClient.getServerType(argThat(n -> !n.equals("cx23")))).thenThrow(new RuntimeException("Not found"));
            when(hetznerClient.getAvailableLocationsForServerType("cx23")).thenReturn(Set.of("fsn1", "nbg1"));

            var response = clusterService.getServerTypes();

            assertThat(response).isNotNull();
            assertThat(response.getShared()).hasSize(1);
            assertThat(response.getShared().get(0).getName()).isEqualTo("cx23");
            assertThat(response.getShared().get(0).getCores()).isEqualTo(2);
        }

        @Test
        @DisplayName("should skip server types that fail to fetch")
        void shouldSkipFailedTypes() {
            when(hetznerClient.getServerType(anyString())).thenThrow(new RuntimeException("API error"));

            var response = clusterService.getServerTypes();

            assertThat(response.getShared()).isEmpty();
            assertThat(response.getDedicated()).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseHetznerError (via createCluster failure)")
    class ParseHetznerError {

        private void setupCreateClusterToFail(String errorMessage) {
            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1"));
            when(clusterRepository.existsBySlug(any())).thenReturn(false);
            when(clusterRepository.saveAndFlush(any(Cluster.class))).thenAnswer(inv -> {
                Cluster c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(provisioningService.createAllServersSync(any()))
                    .thenThrow(new RuntimeException(errorMessage));
        }

        @Test
        @DisplayName("should parse resource_limit_exceeded error")
        void shouldParseResourceLimitError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            setupCreateClusterToFail("resource_limit_exceeded for server");

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("account limits"));
        }

        @Test
        @DisplayName("should parse resource_unavailable error")
        void shouldParseResourceUnavailableError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            setupCreateClusterToFail("resource_unavailable in fsn1");

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("not available in selected region"));
        }

        @Test
        @DisplayName("should parse uniqueness_error")
        void shouldParseUniquenessError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            setupCreateClusterToFail("uniqueness_error: server name taken");

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("already exists"));
        }

        @Test
        @DisplayName("should parse unauthorized error")
        void shouldParseUnauthorizedError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            setupCreateClusterToFail("unauthorized access");

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("authentication failed"));
        }

        @Test
        @DisplayName("should parse 429 rate limit error")
        void shouldParseRateLimitError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            setupCreateClusterToFail("HTTP 429 response from API");

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("Too many requests"));
        }

        @Test
        @DisplayName("should return generic error for unknown messages")
        void shouldReturnGenericError() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            setupCreateClusterToFail("something totally unexpected");

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("Failed to create servers"));
        }

        @Test
        @DisplayName("should handle null error message")
        void shouldHandleNullErrorMessage() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1"));
            when(clusterRepository.existsBySlug(any())).thenReturn(false);
            when(clusterRepository.saveAndFlush(any(Cluster.class))).thenAnswer(inv -> {
                Cluster c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(provisioningService.createAllServersSync(any()))
                    .thenThrow(new RuntimeException((String) null));

            assertThatThrownBy(() -> clusterService.createCluster(request, user))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("Please try again"));
        }
    }

    @Nested
    @DisplayName("generateUniqueSlug (via createCluster)")
    class GenerateUniqueSlug {

        @Test
        @DisplayName("should truncate long names in slug")
        void shouldTruncateLongNames() {
            User user = createTestUser();
            ClusterCreateRequest request = createClusterCreateRequest();
            request.setName("a-very-long-cluster-name-that-exceeds-the-fifty-character-limit-for-slugs");

            when(hetznerClient.getAvailableLocationsForServerType("cx23"))
                    .thenReturn(Set.of("fsn1"));
            when(clusterRepository.existsBySlug(any())).thenReturn(false);
            when(clusterRepository.saveAndFlush(any(Cluster.class))).thenAnswer(inv -> {
                Cluster c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(provisioningService.createAllServersSync(any())).thenReturn(List.of(new VpsNode()));

            ClusterResponse response = clusterService.createCluster(request, user);

            assertThat(response).isNotNull();
            ArgumentCaptor<Cluster> captor = ArgumentCaptor.forClass(Cluster.class);
            verify(clusterRepository).saveAndFlush(captor.capture());
            // Slug should be base (max 50) + dash + 6 char suffix = max 57 chars
            assertThat(captor.getValue().getSlug().length()).isLessThanOrEqualTo(57);
        }
    }

    @Nested
    @DisplayName("getAvailableLocations (no-arg)")
    class GetAvailableLocationsNoArg {

        @Test
        @DisplayName("should default to cx23 server type")
        void shouldDefaultToCx23() {
            HetznerClient.LocationInfo loc = new HetznerClient.LocationInfo();
            loc.setName("fsn1");
            loc.setDescription("Falkenstein");
            loc.setCity("Falkenstein");
            loc.setCountry("DE");

            when(hetznerClient.getLocations()).thenReturn(List.of(loc));
            when(hetznerClient.getAvailableLocationsForServerType("cx23")).thenReturn(Set.of("fsn1"));

            var result = clusterService.getAvailableLocations();

            assertThat(result).hasSize(1);
            verify(hetznerClient).getAvailableLocationsForServerType("cx23");
        }
    }

    // ==================== Helpers ====================

    private User createTestUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hash")
                .role("user")
                .active(true)
                .build();
    }

    private Cluster createCluster(String status) {
        return Cluster.builder()
                .id(UUID.randomUUID())
                .name("test-cluster")
                .slug("test-cluster-abc123")
                .status(status)
                .nodeCount(1)
                .nodeSize("cx23")
                .postgresVersion("16")
                .build();
    }

    private ClusterCreateRequest createClusterCreateRequest() {
        ClusterCreateRequest request = new ClusterCreateRequest();
        request.setName("test-cluster");
        request.setSlug(null); // auto-generate
        request.setNodeSize("cx23");
        request.setNodeRegions(List.of("fsn1"));
        request.setPostgresVersion("16");
        return request;
    }
}
