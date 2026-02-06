package com.pgcluster.api.service;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.repository.ClusterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ClusterProgressService")
@ExtendWith(MockitoExtension.class)
class ClusterProgressServiceTest {

    @Mock private ClusterRepository clusterRepository;

    @InjectMocks
    private ClusterProgressService clusterProgressService;

    @Nested
    @DisplayName("updateProgress")
    class UpdateProgress {

        @Test
        @DisplayName("should update step and progress when cluster exists")
        void shouldUpdateProgress() {
            UUID clusterId = UUID.randomUUID();
            Cluster cluster = Cluster.builder()
                    .id(clusterId)
                    .slug("test-cluster")
                    .build();

            when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));

            clusterProgressService.updateProgress(clusterId, "deploying_nodes", 3);

            assertThat(cluster.getProvisioningStep()).isEqualTo("deploying_nodes");
            assertThat(cluster.getProvisioningProgress()).isEqualTo(3);
            verify(clusterRepository).save(cluster);
        }

        @Test
        @DisplayName("should do nothing when cluster not found")
        void shouldDoNothingWhenNotFound() {
            UUID clusterId = UUID.randomUUID();
            when(clusterRepository.findById(clusterId)).thenReturn(Optional.empty());

            clusterProgressService.updateProgress(clusterId, "step", 1);

            verify(clusterRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("should update status and error message")
        void shouldUpdateStatus() {
            UUID clusterId = UUID.randomUUID();
            Cluster cluster = Cluster.builder()
                    .id(clusterId)
                    .slug("test-cluster")
                    .status(Cluster.STATUS_CREATING)
                    .build();

            when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));

            clusterProgressService.updateStatus(clusterId, Cluster.STATUS_ERROR, "SSH failed");

            assertThat(cluster.getStatus()).isEqualTo(Cluster.STATUS_ERROR);
            assertThat(cluster.getErrorMessage()).isEqualTo("SSH failed");
            verify(clusterRepository).save(cluster);
        }

        @Test
        @DisplayName("should skip update when cluster is DELETING")
        void shouldSkipWhenDeleting() {
            UUID clusterId = UUID.randomUUID();
            Cluster cluster = Cluster.builder()
                    .id(clusterId)
                    .slug("test-cluster")
                    .status(Cluster.STATUS_DELETING)
                    .build();

            when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));

            clusterProgressService.updateStatus(clusterId, Cluster.STATUS_ERROR, "Should not apply");

            assertThat(cluster.getStatus()).isEqualTo(Cluster.STATUS_DELETING);
            verify(clusterRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip update when cluster is DELETED")
        void shouldSkipWhenDeleted() {
            UUID clusterId = UUID.randomUUID();
            Cluster cluster = Cluster.builder()
                    .id(clusterId)
                    .slug("test-cluster")
                    .status(Cluster.STATUS_DELETED)
                    .build();

            when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));

            clusterProgressService.updateStatus(clusterId, Cluster.STATUS_RUNNING, null);

            assertThat(cluster.getStatus()).isEqualTo(Cluster.STATUS_DELETED);
            verify(clusterRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not set error message when null")
        void shouldNotSetErrorWhenNull() {
            UUID clusterId = UUID.randomUUID();
            Cluster cluster = Cluster.builder()
                    .id(clusterId)
                    .slug("test-cluster")
                    .status(Cluster.STATUS_CREATING)
                    .build();

            when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));

            clusterProgressService.updateStatus(clusterId, Cluster.STATUS_RUNNING, null);

            assertThat(cluster.getStatus()).isEqualTo(Cluster.STATUS_RUNNING);
            assertThat(cluster.getErrorMessage()).isNull();
            verify(clusterRepository).save(cluster);
        }
    }
}
